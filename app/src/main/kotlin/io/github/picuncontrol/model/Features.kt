package io.github.picuncontrol.model

import io.github.picuncontrol.protocol.Protocol.Family

/** A control surface the app can show for a device. */
enum class Feature(val label: String, val familyAOnly: Boolean = false) {
  BATTERY("Battery"),
  VOLUME("Volume"),
  PLAY_CONTROLS("Playback"),
  GAME_MODE("Low latency"),
  ANC("Noise control"),
  EQ("Equalizer"),
  SPATIAL("Spatial audio"),
  FIND_MY_BUDS("Find my earbuds"),
  LANGUAGE("Prompt language"),
  MIC_MODE("Mic mode", familyAOnly = true),
  VOLUME_LIMIT("Volume limit", familyAOnly = true),
  LEAK_MODE("Sound mode", familyAOnly = true),
  LIGHT("RGB light", familyAOnly = true),
  HEATING("Heating", familyAOnly = true),
  SOMATOSENSORY("Motion control", familyAOnly = true),
  HEAD_TRACKING("Head tracking", familyAOnly = true),
  TOUCH("Touch controls", familyAOnly = true),
  WEAR("Wear detection", familyAOnly = true),
  WINDPROOF("Wind noise reduction", familyAOnly = true),
  LDAC("LDAC", familyAOnly = true),
  BASS_BOOST("Bass boost", familyAOnly = true),
  SLEEP("Sleep mode", familyAOnly = true),
  WALLPAPER("Wallpaper", familyAOnly = true),
}

/**
 * Which cards to show. The official app decides this from server metadata, not
 * from a BLE capability query (`PROTOCOL.md` §4.4). We use the bundled copy of
 * that metadata, plus module titles, and leave unknown models in read-only mode.
 */
object Features {
  fun forModel(model: PicunModel?): Set<Feature> {
    if (model == null) return unknown(Family.A)

    val out = linkedSetOf(Feature.BATTERY, Feature.VOLUME, Feature.PLAY_CONTROLS)

    for (raw in model.modules) {
      val title = raw.lowercase()
      when {
        "low latency" in title -> out += Feature.GAME_MODE
        "denoise" in title || "noise reduction" in title -> out += Feature.ANC
        title == "eq" || "equalizer" in title -> out += Feature.EQ
        "find it" in title -> out += Feature.FIND_MY_BUDS
        "sleep" in title -> out += Feature.SLEEP
        "leak proof" in title || "leak-proof" in title -> out += Feature.LEAK_MODE
        "rgb" in title || "rbg" in title -> out += Feature.LIGHT
        "warm" in title -> out += Feature.HEATING
        "motion-sensing" in title || "motion control" in title || "somatosensory" in title ->
          out += Feature.SOMATOSENSORY
        "mode switching" in title || "earphone monitor" in title -> out += Feature.MIC_MODE
        "volume down" in title || "dynamic range" in title -> out += Feature.VOLUME_LIMIT
        "alarm" in title -> Unit // server-only feature
      }
    }

    if (model.spatialAudio) out += Feature.SPATIAL
    if (model.headTracking) out += Feature.HEAD_TRACKING
    if (model.voiceSwitching) out += Feature.LANGUAGE
    if (model.ldac) out += Feature.LDAC
    if (model.windproofReduction) out += Feature.WINDPROOF
    if (model.touchControls) out += Feature.TOUCH
    if (model.wearDetection) out += Feature.WEAR
    if (model.heating) out += Feature.HEATING
    if (model.enhancement) out += Feature.BASS_BOOST
    if (model.gameSound) out += Feature.GAME_MODE

    return filterForFamily(out, model.family)
  }

  /**
   * Unknown product: assume the common Family A feature set, but the UI keeps
   * it query-only until the user opts into experimental controls.
   */
  fun unknown(family: Family?): Set<Feature> {
    val out = linkedSetOf(
      Feature.BATTERY,
      Feature.VOLUME,
      Feature.PLAY_CONTROLS,
      Feature.GAME_MODE,
      Feature.ANC,
      Feature.EQ,
      Feature.SPATIAL,
      Feature.FIND_MY_BUDS,
      Feature.LANGUAGE,
    )
    return if (family == null) out else filterForFamily(out, family)
  }

  fun filterForFamily(features: Set<Feature>, family: Family): Set<Feature> =
    if (family == Family.B) features.filterNot { it.familyAOnly }.toSet() else features

  /** Features whose state is fetched on connect. */
  val QUERY_ON_CONNECT: Set<Feature> = setOf(
    Feature.BATTERY,
    Feature.VOLUME,
    Feature.GAME_MODE,
    Feature.ANC,
    Feature.EQ,
    Feature.SPATIAL,
    Feature.LANGUAGE,
    Feature.MIC_MODE,
    Feature.VOLUME_LIMIT,
    Feature.LEAK_MODE,
    Feature.LIGHT,
    Feature.HEATING,
    Feature.SOMATOSENSORY,
    Feature.HEAD_TRACKING,
    Feature.TOUCH,
    Feature.WEAR,
    Feature.WINDPROOF,
    Feature.LDAC,
    Feature.BASS_BOOST,
    Feature.SLEEP,
    Feature.WALLPAPER,
  )
}
