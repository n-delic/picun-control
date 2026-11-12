package io.github.picuncontrol.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EqPreset(val name: String, val gains: List<Int>) {
  fun normalized(): EqPreset = EqPreset(name, List(10) { (gains.getOrNull(it) ?: 0).coerceIn(-6, 6) })
}

object EqPresets {
  val FLAT = EqPreset("Flat", List(10) { 0 })
  val BASS = EqPreset("Bass", listOf(4, 4, 3, 2, 1, 0, 0, 0, 0, 0))
  val VOCAL = EqPreset("Vocal", listOf(-2, -1, 0, 2, 3, 3, 2, 1, 0, -1))
  val TREBLE = EqPreset("Treble", listOf(0, 0, 0, 0, 0, 1, 2, 3, 4, 4))
  val LOUDNESS = EqPreset("Loudness", listOf(4, 3, 1, 0, -1, 0, 1, 2, 3, 4))
  val BUILT_IN = listOf(FLAT, BASS, VOCAL, TREBLE, LOUDNESS)
}

/**
 * Local-only preferences. No account, no network, nothing leaves the device.
 * Custom EQ presets are keyed by BT address so each pair of earbuds keeps its own.
 */
class Prefs(context: Context) {
  private val sp = context.getSharedPreferences("picun_control", Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true }

  var autoApplyGameMode: Boolean
    get() = sp.getBoolean(KEY_AUTO_GAME, false)
    set(value) = sp.edit().putBoolean(KEY_AUTO_GAME, value).apply()

  fun experimentalControls(address: String): Boolean =
    sp.getBoolean("experimental_$address", false)

  fun setExperimentalControls(address: String, enabled: Boolean) {
    sp.edit().putBoolean("experimental_$address", enabled).apply()
  }

  fun lastDevice(): Pair<String, String>? {
    val address = sp.getString(KEY_LAST_ADDRESS, null) ?: return null
    val name = sp.getString(KEY_LAST_NAME, "") ?: ""
    return address to name
  }

  fun lastProduct(): Pair<String, String>? {
    val type = sp.getString(KEY_LAST_PRODUCT_TYPE, null) ?: return null
    val number = sp.getString(KEY_LAST_PRODUCT_NUMBER, null) ?: return null
    return type to number
  }

  fun setLastDevice(address: String, name: String) {
    sp.edit().putString(KEY_LAST_ADDRESS, address).putString(KEY_LAST_NAME, name).apply()
  }

  fun setLastProduct(productType: String, productNumber: String) {
    sp.edit()
      .putString(KEY_LAST_PRODUCT_TYPE, productType)
      .putString(KEY_LAST_PRODUCT_NUMBER, productNumber)
      .apply()
  }

  fun clearLastProduct() {
    sp.edit().remove(KEY_LAST_PRODUCT_TYPE).remove(KEY_LAST_PRODUCT_NUMBER).apply()
  }

  fun customPresets(address: String): List<EqPreset> {
    val raw = sp.getString("eq_$address", null) ?: return emptyList()
    return runCatching { json.decodeFromString<List<EqPreset>>(raw) }
      .getOrDefault(emptyList())
      .map { it.normalized() }
  }

  fun saveCustomPreset(address: String, preset: EqPreset) {
    val next = customPresets(address).filterNot { it.name.equals(preset.name, ignoreCase = true) } +
      preset.normalized()
    sp.edit().putString("eq_$address", json.encodeToString(next)).apply()
  }

  fun deleteCustomPreset(address: String, name: String) {
    val next = customPresets(address).filterNot { it.name == name }
    sp.edit().putString("eq_$address", json.encodeToString(next)).apply()
  }

  private companion object {
    const val KEY_AUTO_GAME = "auto_apply_game_mode"
    const val KEY_LAST_ADDRESS = "last_address"
    const val KEY_LAST_NAME = "last_name"
    const val KEY_LAST_PRODUCT_TYPE = "last_product_type"
    const val KEY_LAST_PRODUCT_NUMBER = "last_product_number"
  }
}
