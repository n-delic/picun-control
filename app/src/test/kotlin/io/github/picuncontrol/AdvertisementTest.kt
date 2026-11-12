package io.github.picuncontrol

import io.github.picuncontrol.protocol.Advertisement
import io.github.picuncontrol.protocol.Protocol.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvertisementTest {
  @Test fun legacyManufacturerData() {
    // Android strips the 0xFFFF company ID; the payload starts with the second FF FF.
    val payload = byteArrayOf(
      0xFF.toByte(), 0xFF.toByte(), // remaining company-ID bytes as the official app sees them
      0x00, 0x01, 0x00, 0x03, // product type 0001, product number 0003
      0, 0, 0, 0, 0, 0, 0, 0, // unknown
      0x11, 0x22, 0x33, 0x44, 0x55, 0x66, // MAC
    )
    val advert = Advertisement.parseManufacturer(0xFFFF, payload)!!
    assertEquals(Family.A, advert.family)
    assertEquals("0001", advert.productType)
    assertEquals("0003", advert.productNumber)
  }

  @Test fun an03ManufacturerData() {
    val raw = byteArrayOf(
      0x43, 0x58, 0x57, // "CXW"
      0x00, // company id
      0x01, // product type "01"
      0x01, // product number "01"
      0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
    )
    val advert = Advertisement.parseRaw(raw)!!
    assertEquals(Family.B, advert.family)
    assertEquals("01", advert.productType)
    assertEquals("01", advert.productNumber)
  }

  @Test fun an03WrongProductTypeIsRejected() {
    val raw = byteArrayOf(
      0x43, 0x58, 0x57, 0x00, 0x02, 0x01,
      0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
    )
    assertNull(Advertisement.parseRaw(raw))
  }

  @Test fun legacyRequiresFourFFBytes() {
    val raw = ByteArray(22) { 0x00 }
    assertNull(Advertisement.parseRaw(raw))
    raw[0] = 0xFF.toByte()
    raw[1] = 0xFF.toByte()
    raw[2] = 0xFF.toByte()
    raw[3] = 0xFE.toByte()
    assertNull(Advertisement.parseRaw(raw))
  }

  @Test fun garbageIsRejected() {
    assertNull(Advertisement.parseRaw(byteArrayOf()))
    assertNull(Advertisement.parseRaw(byteArrayOf(1, 2, 3, 4, 5, 6)))
    assertNull(Advertisement.parseManufacturer(0x1234, byteArrayOf(1, 2, 3, 4, 5, 6)))
  }
}
