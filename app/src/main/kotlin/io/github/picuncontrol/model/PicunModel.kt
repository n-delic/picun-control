package io.github.picuncontrol.model

import io.github.picuncontrol.protocol.Protocol.Family
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry from the bundled Picun model catalogue (`docs/data/models.json`). */
data class PicunModel(
  val id: Int,
  val name: String,
  val bluetoothName: String,
  val deviceModel: String,
  val deviceNo: String,
  val status: Int,
  val category: String,
  val modules: List<String>,
  val spatialAudio: Boolean,
  val headTracking: Boolean,
  val voiceSwitching: Boolean,
  val ldac: Boolean,
  val windproofReduction: Boolean,
  val touchControls: Boolean,
  val wearDetection: Boolean,
  val heating: Boolean,
  val enhancement: Boolean,
  val gameSound: Boolean,
  val sixNoise: Boolean,
  val deferSwitch: Boolean,
  val listeningSwitch: Boolean,
  val memory: Boolean,
  val fast: Boolean,
) {
  val family: Family get() = if (deviceModel == "01") Family.B else Family.A
  val isDelisted: Boolean get() = status == 20

  /** The official app uses server-side presets for these product numbers. */
  val supportsIndexPresets: Boolean get() = deviceNo in INDEX_PRESET_MODELS

  /** For these models `byte1` of the EQ reply is a preset index, not a gain. */
  val isDevicePresetModel: Boolean get() = deviceNo in DEVICE_PRESET_MODELS

  val displayName: String get() = name.ifBlank { bluetoothName.ifBlank { "Picun device" } }

  companion object {
    val INDEX_PRESET_MODELS = setOf("0028", "0046", "0055", "0056", "0071", "0062", "0069", "0081")
    val DEVICE_PRESET_MODELS = setOf("0062", "0069", "0081")
  }
}

/** Wire DTO. Unknown JSON fields are ignored by the Json configuration. */
@Serializable
internal data class ModelJson(
  @SerialName("device_id") val id: Int = 0,
  @SerialName("device_name") val name: String = "",
  @SerialName("bluetooth_name") val bluetoothName: String = "",
  @SerialName("device_model") val deviceModel: String = "",
  @SerialName("device_no") val deviceNo: String = "",
  @SerialName("device_status") val status: Int = 0,
  @SerialName("category") val category: String? = null,
  @SerialName("spatial_audio") val spatialAudio: Int = 0,
  @SerialName("head_tracking") val headTracking: Int = 0,
  @SerialName("voice_switching") val voiceSwitching: Int = 0,
  @SerialName("is_ldac") val ldac: Int = 0,
  @SerialName("is_windproofreduction") val windproofReduction: Int = 0,
  @SerialName("is_touchcontrols") val touchControls: Int = 0,
  @SerialName("is_weardetection") val wearDetection: Int = 0,
  @SerialName("is_heating") val heating: Int = 0,
  @SerialName("is_benhancement") val enhancement: Int = 0,
  @SerialName("is_gameSound") val gameSound: Int = 0,
  @SerialName("six_noise") val sixNoise: Int = 0,
  @SerialName("defer_switch") val deferSwitch: Int = 0,
  @SerialName("listening_switch") val listeningSwitch: Int = 0,
  @SerialName("is_memory") val memory: Int = 0,
  @SerialName("is_fast") val fast: Int = 0,
  val modules: List<ModuleJson> = emptyList(),
) {
  fun toModel(): PicunModel = PicunModel(
    id = id,
    name = name.trim(),
    bluetoothName = bluetoothName.trim(),
    deviceModel = deviceModel.trim(),
    deviceNo = deviceNo.trim(),
    status = status,
    category = category.orEmpty(),
    modules = modules.mapNotNull { it.title.trim().takeIf(String::isNotEmpty) },
    spatialAudio = spatialAudio == 1,
    headTracking = headTracking == 1,
    voiceSwitching = voiceSwitching == 1,
    ldac = ldac == 1,
    windproofReduction = windproofReduction == 1,
    touchControls = touchControls == 1,
    wearDetection = wearDetection == 1,
    heating = heating == 1,
    enhancement = enhancement == 1,
    gameSound = gameSound == 1,
    sixNoise = sixNoise == 1,
    deferSwitch = deferSwitch == 1,
    listeningSwitch = listeningSwitch == 1,
    memory = memory == 1,
    fast = fast == 1,
  )
}

@Serializable
internal data class ModuleJson(val title: String = "")
