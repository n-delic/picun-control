package io.github.picuncontrol.protocol

import io.github.picuncontrol.protocol.Protocol.Family

/**
 * Typed packet builders from `docs/PROTOCOL.md` §5.
 *
 * Family B methods return null where the official app has no Family B path.
 * The official app often sends Family A bytes to Family B devices anyway; that
 * is a bug we do not copy.
 */
object Commands {
  /** Noise-control modes. Family A and B use different numeric values. */
  enum class NoiseMode(val a: Int, val b: Int) {
    OFF(0, 6),
    ANC(1, 0),
    TRANSPARENCY(2, 3),
  }

  // ------------------------------------------------------------ basics

  fun beep(family: Family, on: Boolean): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x6F, if (on) 1 else 0)
    Family.B -> Protocol.an03(0x01, 0x0A, 0x01, if (on) 1 else 0)
  }

  /** ⚠ DANGEROUS: factory reset. UI must confirm first. */
  fun factoryReset(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x70, 0x00)
    Family.B -> Protocol.an03(0x3F, 0x0D, 0x01)
  }

  /** ⚠ DANGEROUS: clears pairing records and may drop the classic BT link. */
  fun clearPairing(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x6E, 0x00)
    Family.B -> Protocol.an03(0x3F, 0x0D, 0x02)
  }

  fun queryName(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x71)
    Family.B -> Protocol.an03(0x01, 0x01, 0x01)
  }

  /** ⚠ DANGEROUS: persistent rename. Keep it short and plain ASCII. */
  fun rename(family: Family, name: String): ByteArray {
    require(name.isNotEmpty()) { "name must not be empty" }
    val bytes = name.toByteArray(Charsets.UTF_8)
    return when (family) {
      Family.A -> Protocol.legacy(0x71, bytes)
      Family.B -> Protocol.an03(0x3F, 0x01, 0x01, bytes)
    }
  }

  fun queryPlayState(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x72)
    Family.B -> Protocol.an03(0x03, 0x02, 0x09)
  }

  fun play(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x73, 0x01)
    Family.B -> Protocol.an03(0x03, 0x02, 0x03)
  }

  fun pause(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x73, 0x00)
    Family.B -> Protocol.an03(0x03, 0x02, 0x04)
  }

  fun next(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x73, 0x03)
    Family.B -> Protocol.an03(0x03, 0x02, 0x02)
  }

  fun previous(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x73, 0x02)
    Family.B -> Protocol.an03(0x03, 0x02, 0x01)
  }

  fun queryBattery(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x74)
    Family.B -> Protocol.an03(0x01, 0x05, 0x01)
  }

  fun queryVolume(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x75)
    Family.B -> Protocol.an03(0x03, 0x02, 0x08)
  }

  fun setVolume(family: Family, volume: Int): ByteArray {
    val v = volume.coerceIn(0, 100)
    return when (family) {
      Family.A -> Protocol.legacy(0x75, v)
      Family.B -> Protocol.an03(0x03, 0x02, 0x07, v)
    }
  }

  // ------------------------------------------------------------ noise control

  fun queryNoise(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x76)
    Family.B -> Protocol.an03(0x04, 0x01, 0x01)
  }

  /**
   * Family A sends [mode, scene, level]. Level presets are 2/8/14 (low/med/high)
   * or 1/3/6/9/12/16 when driven by the continuous slider.
   * Family B only knows on/off/transparency and ignores scene/level.
   */
  fun setNoise(family: Family, mode: NoiseMode, level: Int = 0, scene: Int = 0): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x76, mode.a, scene.coerceIn(0, 16), level.coerceIn(0, 16))
    Family.B -> Protocol.an03(0x04, 0x02, 0x01, mode.b)
  }

  /** Adaptive-ANC detection trigger (Family A; Family B reuses the normal query). */
  fun queryAdaptiveNoise(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x80, 0x00)
    Family.B -> Protocol.an03(0x04, 0x01, 0x01)
  }

  // ------------------------------------------------------------ sound / EQ

  fun queryEq(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x77)
    Family.B -> Protocol.an03(0x03, 0x01, 0x02)
  }

  /** Applies ten gains in dB, each clamped to −6…+6 and encoded as signed int8. */
  fun setEq(family: Family, enabled: Boolean, gains: IntArray): ByteArray {
    require(gains.size == 10) { "EQ needs exactly 10 bands" }
    val payload = IntArray(11)
    payload[0] = if (enabled) 1 else 0
    for (i in 0 until 10) payload[i + 1] = gains[i].coerceIn(-6, 6) and 0xFF
    return when (family) {
      Family.A -> Protocol.legacy(0x77, payload.toBytes())
      Family.B -> Protocol.an03(0x03, 0x01, 0x02, payload.toBytes())
    }
  }

  /**
   * Index-preset models accept a preset number instead of gains:
   * [flag, sound_id, 0…]. Flag 0 for the "device preset" models.
   */
  fun setEqPreset(family: Family, presetId: Int, flag: Int = 0): ByteArray {
    val payload = IntArray(11)
    payload[0] = flag
    payload[1] = presetId.coerceIn(0, 255)
    return when (family) {
      Family.A -> Protocol.legacy(0x77, payload.toBytes())
      Family.B -> Protocol.an03(0x03, 0x01, 0x02, payload.toBytes())
    }
  }

  fun querySpatial(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x79)
    Family.B -> Protocol.an03(0x03, 0x01, 0x01)
  }

  fun setSpatial(family: Family, on: Boolean): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x79, if (on) 1 else 0)
    Family.B -> Protocol.an03(0x03, 0x01, 0x03, if (on) 1 else 0)
  }

  fun queryGameMode(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x78)
    Family.B -> Protocol.an03(0x01, 0x0B, 0x01)
  }

  fun setGameMode(family: Family, on: Boolean): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x78, if (on) 1 else 0)
    Family.B -> Protocol.an03(0x01, 0x0B, 0x02, if (on) 1 else 0)
  }

  fun querySomatosensory(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x83)
    Family.B -> null // official Family B bytes are a copy/paste game-mode bug
  }

  fun setSomatosensory(family: Family, on: Boolean): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x83, if (on) 1 else 0)
    Family.B -> null
  }

  fun queryBassBoost(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x97)
    Family.B -> null
  }

  fun setBassBoost(family: Family, on: Boolean): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x97, if (on) 1 else 0)
    Family.B -> null
  }

  fun queryLeakMode(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x90)
    Family.B -> null
  }

  fun setLeakMode(family: Family, mode: Int): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x90, mode.coerceIn(0, 2))
    Family.B -> null
  }

  fun queryLdac(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x9B)
    Family.B -> null
  }

  fun setLdac(family: Family, on: Boolean): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x9B, if (on) 1 else 0)
    Family.B -> null
  }

  // ------------------------------------------------------------ head / language / mic

  fun queryHeadTracking(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x7D)
    Family.B -> null
  }

  fun setHeadTracking(family: Family, on: Boolean): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x7D, if (on) 1 else 0)
    Family.B -> null
  }

  fun resetHeadTracking(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x7E, 0x01)
    Family.B -> null // official Family B bytes are identical to query-language (bug)
  }

  fun queryLanguage(family: Family): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x7C)
    Family.B -> Protocol.an03(0x01, 0x0C, 0x01)
  }

  /** Family A: 0 = English, 1 = Chinese. Family B: the values are inverted. */
  fun setEnglish(family: Family, english: Boolean): ByteArray = when (family) {
    Family.A -> Protocol.legacy(0x7C, if (english) 0 else 1)
    Family.B -> Protocol.an03(0x01, 0x0C, 0x02, if (english) 1 else 0)
  }

  fun queryMicMode(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x7A)
    Family.B -> null
  }

  fun setMicMode(family: Family, mode: Int): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x7A, mode.coerceIn(0, 2))
    Family.B -> null
  }

  fun queryVolumeLimit(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x81)
    Family.B -> null
  }

  fun setVolumeLimit(family: Family, value: Int): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x81, value)
    Family.B -> null
  }

  // ------------------------------------------------------------ controls / sensors / other

  fun queryTouchControls(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x98)
    Family.B -> null
  }

  fun setTouchControls(family: Family, value: Int): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x98, value.coerceIn(0, 2))
    Family.B -> null
  }

  fun queryWearDetection(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x99)
    Family.B -> null
  }

  fun setWearDetection(family: Family, on: Boolean): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x99, if (on) 1 else 0)
    Family.B -> null
  }

  fun queryWindproof(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x9A)
    Family.B -> null
  }

  fun setWindproof(family: Family, on: Boolean): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x9A, if (on) 1 else 0)
    Family.B -> null
  }

  fun querySleep(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x91)
    Family.B -> null
  }

  fun setSleep(family: Family, on: Boolean): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x91, if (on) 1 else 0)
    Family.B -> null
  }

  fun queryHeating(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x93)
    Family.B -> null
  }

  fun setHeating(family: Family, on: Boolean, level: Int = 1): ByteArray? = when (family) {
    Family.A ->
      if (on) Protocol.legacy(0x93, 0x01, level.coerceIn(1, 3))
      else Protocol.legacy(0x93, 0x00, 0x01)
    Family.B -> null
  }

  fun queryLight(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x92)
    Family.B -> null
  }

  fun setLight(family: Family, value: Int): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x92, value.coerceIn(0, 2))
    Family.B -> null
  }

  fun queryWallpaper(family: Family): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x95)
    Family.B -> null
  }

  fun setWallpaper(family: Family, index: Int): ByteArray? = when (family) {
    Family.A -> Protocol.legacy(0x95, 0x01, index.coerceIn(0, 255))
    Family.B -> null
  }
}

private fun IntArray.toBytes(): ByteArray = ByteArray(size) { this[it].toByte() }
