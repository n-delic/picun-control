package io.github.picuncontrol.protocol

import io.github.picuncontrol.protocol.Protocol.Family

/** Identity extracted from the manufacturer-specific advertisement data. */
data class AdvertInfo(
  val family: Family,
  /** `device_model` in the model database, e.g. "0001" or "01". */
  val productType: String,
  /** `device_no` in the model database, e.g. "0003". */
  val productNumber: String,
)

/**
 * Advertisement parser from `docs/PROTOCOL.md` §3.
 *
 * Android's [android.bluetooth.le.ScanRecord.getManufacturerSpecificData]
 * strips the company ID, so [parseManufacturer] puts it back before applying
 * the official app's rules. This object is pure Kotlin so the byte layout is
 * unit-tested on the JVM.
 */
object Advertisement {
  private const val COMPANY_LEGACY = 0xFFFF
  private const val COMPANY_AN03 = 0x5843

  /** @param companyId 16-bit manufacturer ID as returned by Android. */
  fun parseManufacturer(companyId: Int, data: ByteArray): AdvertInfo? {
    val raw = ByteArray(2 + data.size)
    raw[0] = (companyId and 0xFF).toByte()
    raw[1] = ((companyId ushr 8) and 0xFF).toByte()
    data.copyInto(raw, 2)
    return parseRaw(raw)
  }

  /** @param raw company-ID bytes followed by the manufacturer payload. */
  fun parseRaw(raw: ByteArray): AdvertInfo? {
    if (raw.size < 6) return null
    val t = raw.map { it.toInt() and 0xFF }

    // Family B ("AN03"): 43 58 57 <company> 01 <product number> <MAC…>.
    if (t.size >= 12 && t[0] == 0x43 && t[1] == 0x58 && t[2] == 0x57) {
      if (t[4] != 0x01) return null
      return AdvertInfo(Family.B, productType = "01", productNumber = hex2(t[5]))
    }

    // Family A (legacy): FF FF FF FF <product type 2> <product number 2> …
    if (t.size >= 22 && t[0] == 0xFF && t[1] == 0xFF && t[2] == 0xFF && t[3] == 0xFF) {
      return AdvertInfo(
        family = Family.A,
        productType = hex2(t[4]) + hex2(t[5]),
        productNumber = hex2(t[6]) + hex2(t[7]),
      )
    }
    return null
  }

  fun companyIdOf(raw: ByteArray): Int? {
    if (raw.size < 2) return null
    return (raw[0].toInt() and 0xFF) or ((raw[1].toInt() and 0xFF) shl 8)
  }

  fun isKnownCompany(companyId: Int): Boolean =
    companyId == COMPANY_LEGACY || companyId == COMPANY_AN03

  private fun hex2(value: Int): String = "%02X".format(value and 0xFF)
}
