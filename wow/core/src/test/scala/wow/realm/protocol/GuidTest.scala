package wow.realm.protocol

import wow.common.codecs.CodecTestUtils
import wow.realm.entities.{Guid, GuidType}
import org.scalatest.{FlatSpec, Matchers}
import scodec.Codec
import scodec.bits._

class GuidTest extends FlatSpec with Matchers {
  implicit val codec: Codec[Guid] = Guid.packedCodec

  behavior of "Guid"

  private val guid = Guid(1, GuidType.Character)
  private val bits = hex"0101".bits

  it must "serialize as expected" in CodecTestUtils.encode(bits, guid)
  it must "deserialize as expected" in CodecTestUtils.decode(bits, guid)
}
