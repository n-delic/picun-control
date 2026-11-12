package io.github.picuncontrol.protocol

import java.util.UUID

/**
 * Picun earbud BLE protocol, reverse-engineered from the official app
 * (Picun Audio 3.5.8 / 4.8.7, plugins/BluetoothPlugin.js).
 *
 * Two framings exist; the family is selected from the BLE advertisement
 * (see [Advertisement]) and can be confirmed by the first valid reply.
 *
 * This file is pure Kotlin: no Android imports, no I/O.
 * Every builder has a known-byte test in `ProtocolTest` / `CommandsTest`.
 */
object Protocol {
  val SERVICE: UUID = UUID.fromString("0000ECD0-22ED-CCDB-79C7-71482B33C801")
  val NOTIFY: UUID = UUID.fromString("0000ECD1-22ED-CCDB-79C7-71482B33C801")
  val WRITE: UUID = UUID.fromString("0000ECD2-22ED-CCDB-79C7-71482B33C801")
  val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

  enum class Family { A, B }

  /** A decoded, CRC-validated notification from the earbuds. */
  class Reply(
    val family: Family,
    /** Family A: two hex digits. Family B: group+cmd+op, six hex digits. */
    val command: String,
    val payload: ByteArray,
  ) {
    fun u8(index: Int): Int = if (index in payload.indices) payload[index].toInt() and 0xFF else 0

    val size: Int get() = payload.size

    override fun equals(other: Any?): Boolean =
      other is Reply && family == other.family && command == other.command && payload.contentEquals(other.payload)

    override fun hashCode(): Int =
      (family.hashCode() * 31 + command.hashCode()) * 31 + payload.contentHashCode()

    override fun toString(): String = "${family.name}:$command ${payload.toHex()}"
  }

  // ---------------------------------------------------------------- builders

  /** Family A ("0001" devices): FC FA <len> <cmd> <data…> <crc8-maxim>. */
  fun legacy(cmd: Int, vararg data: Int): ByteArray = legacy(cmd, data.toBytes())

  fun legacy(cmd: Int, data: ByteArray): ByteArray {
    require(cmd in 0..0xFF) { "cmd out of range" }
    require(data.size <= 0xFF) { "Family A payload too long" }
    val body = ByteArray(4 + data.size)
    body[0] = 0xFC.toByte()
    body[1] = 0xFA.toByte()
    body[2] = data.size.toByte()
    body[3] = cmd.toByte()
    data.copyInto(body, 4)
    return body + crc8Maxim(body).toByte()
  }

  /** Family B ("01" devices, "AN03"): <group> <cmd> <op> <len lo> <len hi> <data…> <crc16-arc lo> <hi>. */
  fun an03(group: Int, cmd: Int, op: Int, vararg data: Int): ByteArray = an03(group, cmd, op, data.toBytes())

  fun an03(group: Int, cmd: Int, op: Int, data: ByteArray): ByteArray {
    require(group in 0..0xFF && cmd in 0..0xFF && op in 0..0xFF) { "byte out of range" }
    require(data.size <= 0xFFFF) { "Family B payload too long" }
    val body = ByteArray(5 + data.size)
    body[0] = group.toByte()
    body[1] = cmd.toByte()
    body[2] = op.toByte()
    body[3] = (data.size and 0xFF).toByte()
    body[4] = ((data.size ushr 8) and 0xFF).toByte()
    data.copyInto(body, 5)
    val crc = crc16Arc(body)
    return body + (crc and 0xFF).toByte() + ((crc ushr 8) and 0xFF).toByte()
  }

  // ---------------------------------------------------------------- parser

  /**
   * Parses and validates a notification. Returns null for anything that is not
   * an exact-length, CRC-correct frame. The official app never checks the CRC;
   * we deliberately do (PLAN.md safety rules).
   */
  fun parse(bytes: ByteArray): Reply? {
    if (bytes.size < 5) return null
    val b = bytes.map { it.toInt() and 0xFF }
    return when {
      b[0] == 0xFC && b[1] == 0xFA -> parseLegacy(b)
      else -> parseAn03(b)
    }
  }

  private fun parseLegacy(b: List<Int>): Reply? {
    if (b.size < 5) return null
    val len = b[2]
    if (b.size != 4 + len + 1) return null
    val expected = crc8Maxim(b.subList(0, b.size - 1).toIntArray())
    if (expected != b.last()) return null
    return Reply(Family.A, "%02X".format(b[3]), b.subList(4, 4 + len).toIntArray().toBytes())
  }

  private fun parseAn03(b: List<Int>): Reply? {
    if (b.size < 7) return null
    val len = b[3] or (b[4] shl 8)
    if (b.size != 5 + len + 2) return null
    val expected = crc16Arc(b.subList(0, b.size - 2).toIntArray())
    if (b[b.size - 2] != (expected and 0xFF) || b[b.size - 1] != ((expected ushr 8) and 0xFF)) return null
    return Reply(Family.B, "%02X%02X%02X".format(b[0], b[1], b[2]), b.subList(5, 5 + len).toIntArray().toBytes())
  }

  // ---------------------------------------------------------------- checksums

  /** CRC-8/MAXIM (Dallas 1-Wire), poly 0x31 reflected, init 0, no final XOR. */
  fun crc8Maxim(bytes: ByteArray): Int = crc8Maxim(bytes.map { it.toInt() and 0xFF }.toIntArray())

  fun crc8Maxim(bytes: IntArray): Int {
    var crc = 0
    for (value in bytes) {
      crc = crc xor (value and 0xFF)
      repeat(8) {
        crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8C else crc ushr 1
      }
    }
    return crc and 0xFF
  }

  /** CRC-16/ARC, poly 0x8005 reflected, init 0, no final XOR. */
  fun crc16Arc(bytes: ByteArray): Int = crc16Arc(bytes.map { it.toInt() and 0xFF }.toIntArray())

  fun crc16Arc(bytes: IntArray): Int {
    var crc = 0
    for (value in bytes) {
      crc = crc xor (value and 0xFF)
      repeat(8) {
        crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
      }
    }
    return crc and 0xFFFF
  }
}

fun ByteArray.toHex(separator: String = ""): String =
  joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

private fun IntArray.toBytes(): ByteArray = ByteArray(size) { this[it].toByte() }
