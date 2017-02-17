package ensiwow.auth.protocol.packets

import ensiwow.auth.protocol.{ClientPacket, OpCodes}
import ensiwow.auth.protocol.codecs._
import scodec._
import scodec.codecs._

/**
  * Client logon proof packet, sent in response to server challenge.
  */
case class ClientLogonProof(clientKey: BigInt,
                            clientProof: BigInt,
                            crcHash: BigInt) extends ClientPacket

object ClientLogonProof {
  final val clientKeyLength = 32
  final val clientProofLength = 20
  final val CRCLength = 20

  implicit val codec: Codec[ClientLogonProof] = {
    constantE(OpCodes.LogonProof) ::
      ("clientKey" | fixedUBigIntL(clientKeyLength)) ::
      ("clientProof" | fixedUBigIntL(clientProofLength)) ::
      ("crcHash" | fixedUBigIntL(CRCLength)) ::
      constantE(0)(uint8L) :: // key count
      constantE(0)(uint8L)    // security flags
  }.as[ClientLogonProof]
}
