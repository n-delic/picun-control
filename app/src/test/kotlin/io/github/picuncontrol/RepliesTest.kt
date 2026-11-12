package io.github.picuncontrol

import io.github.picuncontrol.protocol.Commands.NoiseMode
import io.github.picuncontrol.protocol.Protocol
import io.github.picuncontrol.protocol.Protocol.Family
import io.github.picuncontrol.protocol.Replies
import io.github.picuncontrol.protocol.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepliesTest {
  private fun reply(hex: String): Protocol.Reply = Protocol.parse(hex.hexBytes())!!

  @Test fun familyABattery() {
    val battery = Replies.battery(reply("FCFA04740150014B2F"))!!
    assertEquals(80, battery.left)
    assertEquals(75, battery.right)
  }

  @Test fun familyABatteryAbsentSides() {
    val battery = Replies.battery(reply("FCFA0474010001FF07"))!!
    assertNull(battery.left)
    assertNull(battery.right)
    assertFalse(battery.hasAny)
  }

  @Test fun familyBBattery() {
    val battery = Replies.battery(reply("01050104005050004B8DCD"))!!
    assertEquals(80, battery.left)
    assertEquals(75, battery.right)
  }

  @Test fun noiseFamilyA() {
    val noise = Replies.noise(reply("FCFA03760104101E"))!!
    assertEquals(NoiseMode.ANC, noise.mode)
    assertEquals(4, noise.scene)
    assertEquals(16, noise.level)
  }

  @Test fun noiseFamilyB() {
    assertEquals(NoiseMode.OFF, Replies.noise(reply("040101010006EC7A"))!!.mode)
    assertEquals(NoiseMode.ANC, Replies.noise(reply("0401010100006C78"))!!.mode)
    assertEquals(NoiseMode.TRANSPARENCY, Replies.noise(reply("0401010100032C79"))!!.mode)
  }

  @Test fun eqFamilyAWithSignedGains() {
    val eq = Replies.eq(reply("FCFA0B7701030200FFFA0000000000C8"))!!
    assertTrue(eq.enabled)
    assertEquals(listOf(3, 2, 0, -1, -6, 0, 0, 0, 0, 0), eq.gains)
    assertNull(eq.presetId)
  }

  @Test fun eqDevicePresetModelExposesPresetId() {
    val eq = Replies.eq(reply("FCFA0B770003000000000000000000F7"), devicePresetModel = true)!!
    assertFalse(eq.enabled)
    assertEquals(3, eq.presetId)
  }

  @Test fun eqFamilyB() {
    val eq = Replies.eq(reply("0301020B0001030200FFFA00000000006E7E"))!!
    assertTrue(eq.enabled)
    assertEquals(listOf(3, 2, 0, -1, -6, 0, 0, 0, 0, 0), eq.gains)
  }

  @Test fun simpleValues() {
    assertTrue(Replies.onOff(reply("FCFA017801D8"))!!)
    assertFalse(Replies.onOff(reply("FCFA01780086"))!!)
    assertEquals(50, Replies.volume(reply("FCFA0175320D")))
    assertEquals(2, Replies.micMode(reply("FCFA017A02AB")))
    assertEquals(2, Replies.leakMode(reply("FCFA01900239")))
    assertEquals(2, Replies.lightMode(reply("FCFA019202A8")))
    assertEquals(2, Replies.touchControls(reply("FCFA0198024F")))
    assertEquals(8, Replies.volumeLimit(reply("FCFA0181086F")))
    assertEquals(2, Replies.adaptiveLevel(reply("FCFA018002D5")))
  }

  @Test fun heating() {
    val heating = Replies.heating(reply("FCFA02930102A7"))!!
    assertTrue(heating.on)
    assertEquals(2, heating.level)
  }

  @Test fun names() {
    assertEquals("Pods", Replies.name(reply("FCFA0471506F6473F2")))
    assertEquals("Pods", Replies.name(reply("0101010400506F64739720")))
  }

  @Test fun englishIsInvertedBetweenFamilies() {
    assertTrue(Replies.english(reply("FCFA017C00BD"))!!)
    assertFalse(Replies.english(reply("FCFA017C01E3"))!!)
    assertTrue(Replies.english(reply("010C01010001802C"))!!)
    assertFalse(Replies.english(reply("010C0101000041EC"))!!)
  }

  @Test fun playState() {
    assertTrue(Replies.playState(reply("FCFA02720001ED"))!!)
    assertFalse(Replies.playState(reply("FCFA02720000B3"))!!)
  }

  @Test fun shortPayloadDoesNotThrow() {
    // A one-byte payload is not a valid Family A battery layout, but decoding must be safe.
    assertNull(Replies.battery(reply("FCFA017400CB")))
  }
}
