package wow.auth.session

import akka.actor.{ActorRef, FSM, Props}
import scodec.Attempt.{Failure, Successful}
import scodec.bits.BitVector
import scodec.{Codec, DecodeResult, Err}
import wow.auth.crypto.Srp6Protocol
import wow.auth.handlers._
import wow.auth.protocol.packets.ClientRealmlist
import wow.auth.protocol.{OpCodes, ServerPacket}
import wow.auth.session.AuthSession.{EventIncoming, SetCharacterCount}
import wow.auth.utils.{MalformedPacketHeaderException, PacketSerializer}
import wow.common.network.{TCPSession, TCPSessionFactory}
import wow.realm.RealmServer
import wow.realm.RealmServer.GetCharacterCount

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Handles an auth session
  */
class AuthSession(override val connection: ActorRef) extends TCPSession
                                                             with FSM[AuthSessionState, AuthSessionData]
                                                             with LogonChallengeHandler
                                                             with LogonProofHandler
                                                             with ReconnectChallengeHandler
                                                             with ReconnectProofHandler {
  val srp6: Srp6Protocol = new Srp6Protocol()

  // First packet that we expect from client is logon challenge
  startWith(StateNoData, NoData)

  when(StateNoData) {
    case Event(e@EventIncoming(bits), NoData) =>
      val state = Codec[OpCodes.Value].decode(bits) match {
        case Successful(DecodeResult(OpCodes.LogonChallenge, _)) => StateChallenge
        case Successful(DecodeResult(OpCodes.ReconnectChallenge, _)) => StateReconnectChallenge
        case Failure(err) => throw MalformedPacketHeaderException(err)
        case _ => throw MalformedPacketHeaderException(Err("Expected either logon or reconnect challenge"))
      }
      log.debug(s"Got first packet, going to $state")

      self ! e
      goto(state)
  }

  when(StateChallenge)(handleChallenge)

  when(StateProof)(handleProof)

  when(StateReconnectChallenge)(handleReconnectChallenge)

  when(StateReconnectProof)(handleReconnectProof)

  when(StateRealmlist) {
    case Event(EventIncoming(bits), data@RealmsListData(_, _, outgoingBits)) =>
      PacketSerializer.deserialize[ClientRealmlist](bits)

      outgoing(outgoingBits)

      stay using data

    case Event(SetCharacterCount(realmId, count), RealmsListData(account, charactersPerRealm, _)) =>
      stay using RealmsListData(account, charactersPerRealm.updated(realmId, count))
  }

  when(StateFailed, stateTimeout = 5 second) {
    case Event(StateTimeout, _) =>
      log.debug("Failed state expired, disconnecting")
      disconnect()
      stop
  }

  onTransition {
    // On first transition to StateRealmlist, ask every realm actor for the user's character count
    // Received values will be cached afterwards
    case p -> StateRealmlist if p != StateRealmlist =>
      val id = nextStateData.asInstanceOf[RealmsListData].account.id

      context.actorSelection(RealmServer.ActorPathAll) ! GetCharacterCount(id)
  }

  override def receive: Receive = tcpSessionReceiver orElse super[FSM].receive

  def sendPacket[A <: ServerPacket](packet: A)(implicit codec: Codec[A]): Unit = {
    log.debug(s"Sending $packet")
    val bits = PacketSerializer.serialize(packet)(codec)

    outgoing(bits)
  }

  // Make it look like this is an event that came normally, for the FSM
  override def incoming(data: BitVector): Unit = receive.apply(EventIncoming(data))
}

object AuthSession extends TCPSessionFactory {
  def props(connection: ActorRef): Props = Props(new AuthSession(connection))

  val PreferredName: String = "authsession"

  /**
    * Incoming packet read from the network
    *
    * @param bits bits read
    */
  case class EventIncoming(bits: BitVector)

  /**
    * Sets the number of character of a player in an account
    *
    * @param realmId realm id
    * @param count   character count
    */
  case class SetCharacterCount(realmId: Int, count: Int)

}

