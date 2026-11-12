package io.github.picuncontrol

import io.github.picuncontrol.protocol.Protocol
import io.github.picuncontrol.protocol.Protocol.Family
import io.github.picuncontrol.protocol.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolTest {
  @Test fun crcCheckValues() {
    assertEquals(0xA1, Protocol.crc8Maxim("123456789".toByteArray()))
    assertEquals(0xBB3D, Protocol.crc16Arc("123456789".toByteArray()))
  }

  @Test fun familyAVectorsFromSpec() {
    assertEquals("FCFA017801D8", Protocol.legacy(0x78, 1).toHex())
    assertEquals("FCFA01780086", Protocol.legacy(0x78, 0).toHex())
    assertEquals("FCFA0078BE", Protocol.legacy(0x78).toHex())
  }

  @Test fun familyBVectorsFromSpec() {
    assertEquals("010B0201000135A8", Protocol.an03(0x01, 0x0B, 0x02, 1).toHex())
    assertEquals("010B02010000F468", Protocol.an03(0x01, 0x0B, 0x02, 0).toHex())
    assertEquals("010B0100006E24", Protocol.an03(0x01, 0x0B, 0x01).toHex())
  }

  @Test fun parsesFamilyAReply() {
    val reply = Protocol.parse("FCFA04740150014B2F".hexBytes())
    assertNotNull(reply)
    assertEquals(Family.A, reply!!.family)
    assertEquals("74", reply.command)
    assertEquals("0150014B", reply.payload.toHex())
  }

  @Test fun parsesFamilyBReply() {
    val reply = Protocol.parse("010B0101000135EC".hexBytes())
    assertNotNull(reply)
    assertEquals(Family.B, reply!!.family)
    assertEquals("010B01", reply.command)
    assertEquals("01", reply.payload.toHex())
  }

  @Test fun dropsBadCrc() {
    assertNull(Protocol.parse("FCFA017801D9".hexBytes()))
    assertNull(Protocol.parse("010B0201000135A9".hexBytes()))
  }

  @Test fun dropsWrongLength() {
    // Claims two payload bytes but contains one.
    assertNull(Protocol.parse("FCFA027801D8".hexBytes()))
    // Family B claims 2 payload bytes but contains 1.
    assertNull(Protocol.parse("010B0202000135A8".hexBytes()))
  }

  @Test fun dropsGarbage() {
    assertNull(Protocol.parse(byteArrayOf()))
    assertNull(Protocol.parse(byteArrayOf(1, 2, 3)))
    assertNull(Protocol.parse("FFFFFFFF".hexBytes()))
  }

  @Test fun replyEqualityUsesContent() {
    val a = Protocol.parse("FCFA017801D8".hexBytes())
    val b = Protocol.parse("FCFA017801D8".hexBytes())
    assertEquals(a, b)
  }
}

fun String.hexBytes(): ByteArray {
  require(length % 2 == 0) { "odd hex length" }
  return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
