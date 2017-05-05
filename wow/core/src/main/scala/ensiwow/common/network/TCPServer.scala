package ensiwow.common.network

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}

import scala.language.postfixOps

case object GetAddress

/**
  * This class defines the behaviour of the TCP server.
  *
  * @constructor send a Bind command to the TCP manager
  */
class TCPServer[A <: SessionActorCompanion](val companion: A, val address: String, val port: Int)
  extends Actor with ActorLogging {
  log.debug("Binding server with socket")
  IO(Tcp)(context.system) ! Bind(self, new InetSocketAddress(address, port))

  override def postStop(): Unit = log.debug("Stopped")

  def receive: PartialFunction[Any, Unit] = {
    case Bound(localAddress) =>
      log.debug(s"TCP port opened at: ${localAddress.getHostString}:${localAddress.getPort}")

    case Connected(remote, local) =>
      log.debug(s"Remote connection set from $remote to $local")
      val handlerRef = context.actorOf(TCPHandler.props(companion, sender), TCPHandler.PreferredName(remote))
      sender ! Register(handlerRef)

    case CommandFailed(_: Bind) => context stop self
  }
}

object TCPServer {
  def props[A <: SessionActorCompanion](companion: A, address: String, port: Int): Props = Props(classOf[TCPServer[A]],
    companion,
    address,
    port)

  val PreferredName = "TCP"
}


