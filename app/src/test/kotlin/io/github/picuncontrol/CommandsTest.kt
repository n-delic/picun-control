package io.github.picuncontrol

import io.github.picuncontrol.protocol.Commands
import io.github.picuncontrol.protocol.Commands.NoiseMode
import io.github.picuncontrol.protocol.Protocol.Family
import io.github.picuncontrol.protocol.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Expected bytes were generated from the CRC tables and cross-checked against
// the worked examples in docs/PROTOCOL.md §2.4.
class CommandsTest {
  private fun check(expected: String, actual: ByteArray?) {
    assertEquals(expected, actual?.toHex())
  }

  private val tenGains = intArrayOf(3, 2, 0, -1, -6, 0, 0, 0, 0, 0)

  @Test fun familyABytes() {
    check("FCFA016F015A", Commands.beep(Family.A, true))
    check("FCFA016F0004", Commands.beep(Family.A, false))
    check("FCFA017000F0", Commands.factoryReset(Family.A))
    check("FCFA016E00C0", Commands.clearPairing(Family.A))
    check("FCFA007122", Commands.queryName(Family.A))
    check("FCFA0471506F6473F2", Commands.rename(Family.A, "Pods"))
    check("FCFA0072C0", Commands.queryPlayState(Family.A))
    check("FCFA017301FB", Commands.play(Family.A))
    check("FCFA017300A5", Commands.pause(Family.A))
    check("FCFA01730347", Commands.next(Family.A))
    check("FCFA01730219", Commands.previous(Family.A))
    check("FCFA00741D", Commands.queryBattery(Family.A))
    check("FCFA007543", Commands.queryVolume(Family.A))
    check("FCFA0175320D", Commands.setVolume(Family.A, 50))
    check("FCFA0076A1", Commands.queryNoise(Family.A))
    check("FCFA037601000EA7", Commands.setNoise(Family.A, NoiseMode.ANC, 14, 0))
    check("FCFA037600000013", Commands.setNoise(Family.A, NoiseMode.OFF))
    check("FCFA03760200089E", Commands.setNoise(Family.A, NoiseMode.TRANSPARENCY, 8, 0))
    check("FCFA01800069", Commands.queryAdaptiveNoise(Family.A))
    check("FCFA0077FF", Commands.queryEq(Family.A))
    check("FCFA0B7701030200FFFA0000000000C8", Commands.setEq(Family.A, true, tenGains))
    check("FCFA0B770003000000000000000000F7", Commands.setEqPreset(Family.A, 3))
    check("FCFA0079E0", Commands.querySpatial(Family.A))
    check("FCFA0179011C", Commands.setSpatial(Family.A, true))
    check("FCFA0078BE", Commands.queryGameMode(Family.A))
    check("FCFA017801D8", Commands.setGameMode(Family.A, true))
    check("FCFA01780086", Commands.setGameMode(Family.A, false))
    check("FCFA0083EA", Commands.querySomatosensory(Family.A))
    check("FCFA01830162", Commands.setSomatosensory(Family.A, true))
    check("FCFA009716", Commands.queryBassBoost(Family.A))
    check("FCFA019701B5", Commands.setBassBoost(Family.A, true))
    check("FCFA009095", Commands.queryLeakMode(Family.A))
    check("FCFA01900239", Commands.setLeakMode(Family.A, 2))
    check("FCFA009BB5", Commands.queryLdac(Family.A))
    check("FCFA019B01F8", Commands.setLdac(Family.A, true))
    check("FCFA007D81", Commands.queryHeadTracking(Family.A))
    check("FCFA017D0127", Commands.setHeadTracking(Family.A, true))
    check("FCFA017E0172", Commands.resetHeadTracking(Family.A))
    check("FCFA007CDF", Commands.queryLanguage(Family.A))
    check("FCFA017C00BD", Commands.setEnglish(Family.A, true))
    check("FCFA017C01E3", Commands.setEnglish(Family.A, false))
    check("FCFA007A02", Commands.queryMicMode(Family.A))
    check("FCFA017A02AB", Commands.setMicMode(Family.A, 2))
    check("FCFA008156", Commands.queryVolumeLimit(Family.A))
    check("FCFA0181086F", Commands.setVolumeLimit(Family.A, 8))
    check("FCFA009857", Commands.queryTouchControls(Family.A))
    check("FCFA0198024F", Commands.setTouchControls(Family.A, 2))
    check("FCFA009909", Commands.queryWearDetection(Family.A))
    check("FCFA01990169", Commands.setWearDetection(Family.A, true))
    check("FCFA009AEB", Commands.queryWindproof(Family.A))
    check("FCFA019A013C", Commands.setWindproof(Family.A, true))
    check("FCFA0091CB", Commands.querySleep(Family.A))
    check("FCFA0191011F", Commands.setSleep(Family.A, true))
    check("FCFA009377", Commands.queryHeating(Family.A))
    check("FCFA02930102A7", Commands.setHeating(Family.A, true, 2))
    check("FCFA0293000181", Commands.setHeating(Family.A, false))
    check("FCFA009229", Commands.queryLight(Family.A))
    check("FCFA019202A8", Commands.setLight(Family.A, 2))
    check("FCFA0095AA", Commands.queryWallpaper(Family.A))
    check("FCFA0295010328", Commands.setWallpaper(Family.A, 3))
  }

  @Test fun familyBBytes() {
    check("010A01010001082C", Commands.beep(Family.B, true))
    check("3F0D0100004769", Commands.factoryReset(Family.B))
    check("3F0D020000B769", Commands.clearPairing(Family.B))
    check("01010100006DFC", Commands.queryName(Family.B))
    check("3F01010400506F64738F41", Commands.rename(Family.B, "Pods"))
    check("030209000095BA", Commands.queryPlayState(Family.B))
    check("0302030000B5B8", Commands.play(Family.B))
    check("03020400000479", Commands.pause(Family.B))
    check("0302020000E478", Commands.next(Family.B))
    check("03020100001478", Commands.previous(Family.B))
    check("01050100006CCC", Commands.queryBattery(Family.B))
    check("0302080000C47A", Commands.queryVolume(Family.B))
    check("030207010032A892", Commands.setVolume(Family.B, 50))
    check("0401010000A1FC", Commands.queryNoise(Family.B))
    check("0402010100002878", Commands.setNoise(Family.B, NoiseMode.ANC))
    check("040201010006A87A", Commands.setNoise(Family.B, NoiseMode.OFF))
    check("0402010100036879", Commands.setNoise(Family.B, NoiseMode.TRANSPARENCY))
    check("0401010000A1FC", Commands.queryAdaptiveNoise(Family.B))
    check("0301020000E43C", Commands.queryEq(Family.B))
    check("0301020B0001030200FFFA00000000006E7E", Commands.setEq(Family.B, true, tenGains))
    check("0301020B00000300000000000000000068B7", Commands.setEqPreset(Family.B, 3))
    check("0301010000143C", Commands.querySpatial(Family.B))
    check("030103010001ADB7", Commands.setSpatial(Family.B, true))
    check("010B0100006E24", Commands.queryGameMode(Family.B))
    check("010B0201000135A8", Commands.setGameMode(Family.B, true))
    check("010B02010000F468", Commands.setGameMode(Family.B, false))
    check("010C0100006F50", Commands.queryLanguage(Family.B))
    check("010C020100018068", Commands.setEnglish(Family.B, true))
    check("010C0201000041A8", Commands.setEnglish(Family.B, false))
  }

  @Test fun familyBUnsupportedCommandsAreNull() {
    assertNull(Commands.querySomatosensory(Family.B))
    assertNull(Commands.setSomatosensory(Family.B, true))
    assertNull(Commands.queryBassBoost(Family.B))
    assertNull(Commands.setBassBoost(Family.B, true))
    assertNull(Commands.queryLeakMode(Family.B))
    assertNull(Commands.setLeakMode(Family.B, 1))
    assertNull(Commands.queryLdac(Family.B))
    assertNull(Commands.setLdac(Family.B, true))
    assertNull(Commands.queryHeadTracking(Family.B))
    assertNull(Commands.setHeadTracking(Family.B, true))
    assertNull(Commands.resetHeadTracking(Family.B))
    assertNull(Commands.queryMicMode(Family.B))
    assertNull(Commands.setMicMode(Family.B, 1))
    assertNull(Commands.queryVolumeLimit(Family.B))
    assertNull(Commands.setVolumeLimit(Family.B, 8))
    assertNull(Commands.queryTouchControls(Family.B))
    assertNull(Commands.setTouchControls(Family.B, 1))
    assertNull(Commands.queryWearDetection(Family.B))
    assertNull(Commands.setWearDetection(Family.B, true))
    assertNull(Commands.queryWindproof(Family.B))
    assertNull(Commands.setWindproof(Family.B, true))
    assertNull(Commands.querySleep(Family.B))
    assertNull(Commands.setSleep(Family.B, true))
    assertNull(Commands.queryHeating(Family.B))
    assertNull(Commands.setHeating(Family.B, true))
    assertNull(Commands.queryLight(Family.B))
    assertNull(Commands.setLight(Family.B, 1))
    assertNull(Commands.queryWallpaper(Family.B))
    assertNull(Commands.setWallpaper(Family.B, 1))
  }

  @Test fun eqClampsToSignedSixAndEncodesTwosComplement() {
    val bytes = Commands.setEq(Family.A, true, intArrayOf(9, -9, 6, -6, 0, 0, 0, 0, 0, 0))
    // FC FA 0B 77 | flag g1 g2 g3 g4 g5 g6 g7 g8 g9 g10 | crc
    assertEquals(0x01, bytes[4].toInt() and 0xFF)
    assertEquals(0x06, bytes[5].toInt() and 0xFF)
    assertEquals(0xFA, bytes[6].toInt() and 0xFF)
    assertEquals(0x06, bytes[7].toInt() and 0xFF)
    assertEquals(0xFA, bytes[8].toInt() and 0xFF)
  }

  @Test fun renameUsesTrueUtf8ByteLength() {
    val bytes = Commands.rename(Family.A, "caf\u00E9")
    // 4 characters, 5 UTF-8 bytes. The official app's length quirk is not copied.
    assertEquals(5, bytes[2].toInt())
    assertEquals("636166C3A9", bytes.copyOfRange(4, 9).toHex())
  }

  @Test fun volumeIsClamped() {
    assertEquals("FCFA0175000F", Commands.setVolume(Family.A, -5).toHex())
    assertEquals("FCFA0175640B", Commands.setVolume(Family.A, 100).toHex())
    assertEquals("FCFA0175640B", Commands.setVolume(Family.A, 250).toHex())
  }
}
