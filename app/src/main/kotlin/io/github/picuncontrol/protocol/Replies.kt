package io.github.picuncontrol.protocol

import io.github.picuncontrol.protocol.Protocol.Family
import io.github.picuncontrol.protocol.Protocol.Reply

/**
 * Decoders for the reply layouts documented in `docs/PROTOCOL.md` §4.
 * Most layouts are still UNVERIFIED on hardware; decoders are deliberately
 * tolerant and never throw, so an unexpected reply can only leave a field
 * unchanged, not crash the UI.
 */
object Replies {
  data class Battery(val left: Int?, val right: Int?) {
    val hasAny: Boolean get() = left != null || right != null
  }

  data class Noise(val mode: Commands.NoiseMode, val scene: Int, val level: Int)

  data class Eq(val enabled: Boolean, val gains: List<Int>, val presetId: Int?)

  data class Heating(val on: Boolean, val level: Int)

  /** Family A battery: [left present, left pct, right present, right pct]. */
  fun battery(reply: Reply): Battery? {
    if (reply.size == 0) return null
    if (reply.family == Family.B) {
      // The official decoder applies both checks in sequence:
      // byte0 -> left; byte1 -> left = byte1, right = byte3.
      val b0 = reply.u8(0)
      val b1 = reply.u8(1)
      val b3 = reply.u8(3)
      var left = b0.takeIf { it in 1..100 }
      var right: Int? = null
      if (b1 in 1..100) {
        left = b1
        right = b3.takeIf { it in 1..100 }
      }
      return Battery(left, right)
    }
    if (reply.size < 4) return null
    val left = reply.u8(1).takeIf { it in 1..100 }
    val right = reply.u8(3).takeIf { it in 1..100 }
    return Battery(left, right)
  }

  fun noise(reply: Reply): Noise? {
    if (reply.size == 0) return null
    if (reply.family == Family.B) {
      val mode = when (reply.u8(0)) {
        0 -> Commands.NoiseMode.ANC
        3 -> Commands.NoiseMode.TRANSPARENCY
        6 -> Commands.NoiseMode.OFF
        else -> Commands.NoiseMode.OFF
      }
      return Noise(mode, scene = 0, level = 0)
    }
    val mode = when (reply.u8(0)) {
      1 -> Commands.NoiseMode.ANC
      2 -> Commands.NoiseMode.TRANSPARENCY
      else -> Commands.NoiseMode.OFF
    }
    return Noise(mode, scene = reply.u8(1), level = reply.u8(2))
  }

  /** Family A/B EQ: [enabled, gain1…gain10], gains signed int8. */
  fun eq(reply: Reply, devicePresetModel: Boolean = false): Eq? {
    if (reply.size < 2) return null
    val gains = (1..10).map { i ->
      reply.u8(i).let { if (it >= 128) it - 256 else it }.coerceIn(-6, 6)
    }
    val preset = if (devicePresetModel) reply.u8(1) else null
    return Eq(enabled = reply.u8(0) == 1, gains = gains, presetId = preset)
  }

  fun volume(reply: Reply): Int? {
    if (reply.size == 0) return null
    return reply.u8(0).takeIf { it in 0..100 }
  }

  fun onOff(reply: Reply): Boolean? {
    if (reply.size == 0) return null
    return reply.u8(0) != 0
  }

  fun heating(reply: Reply): Heating? {
    if (reply.size < 2) return null
    return Heating(on = reply.u8(0) != 0, level = reply.u8(1).coerceIn(1, 3))
  }

  /** Family A: 0 = English, 1 = Chinese. Family B (docs §4.3): values inverted. */
  fun english(reply: Reply): Boolean? {
    if (reply.size == 0) return null
    return if (reply.family == Family.A) reply.u8(0) == 0 else reply.u8(0) != 0
  }

  fun micMode(reply: Reply): Int? = valueInRange(reply, 0, 2)

  fun volumeLimit(reply: Reply): Int? = valueInRange(reply, 0, 255)

  fun leakMode(reply: Reply): Int? = valueInRange(reply, 0, 2)

  fun lightMode(reply: Reply): Int? = valueInRange(reply, 0, 2)

  fun touchControls(reply: Reply): Int? = valueInRange(reply, 0, 2)

  fun adaptiveLevel(reply: Reply): Int? = valueInRange(reply, 0, 255)

  fun playState(reply: Reply): Boolean? {
    if (reply.size < 2) return null
    return reply.u8(1) == 1
  }

  /** Name replies (0x71 / 010101) are UTF-8 strings. */
  fun name(reply: Reply): String? {
    if (reply.size == 0) return null
    return reply.payload.toString(Charsets.UTF_8).trim('\u0000', ' ', '\n', '\r').ifEmpty { null }
  }

  private fun valueInRange(reply: Reply, min: Int, max: Int): Int? {
    if (reply.size == 0) return null
    return reply.u8(0).takeIf { it in min..max }
  }
}
