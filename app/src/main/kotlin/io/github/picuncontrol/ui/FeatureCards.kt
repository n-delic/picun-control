package io.github.picuncontrol.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.picuncontrol.data.EqPresets
import io.github.picuncontrol.protocol.Commands.NoiseMode
import io.github.picuncontrol.protocol.Protocol.Family
import kotlin.math.roundToInt

@Composable
fun EqCard(state: AppUiState, vm: AppViewModel) {
  var saveOpen by remember { mutableStateOf(false) }
  val enabled = state.controlsEnabled && state.isReady
  val eqEnabled = state.values.eqEnabled
  val draft = state.eqDraft

  SectionCard(
    title = "Equalizer",
    subtitle = when {
      eqEnabled == null -> "Waiting for the earbuds…"
      eqEnabled -> "On · 10 bands, −6…+6 dB"
      else -> "Off"
    },
  ) {
    SwitchRow(
      title = "Enable equalizer",
      checked = eqEnabled == true,
      enabled = enabled,
      onCheckedChange = vm::setEqEnabled,
    )

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EqPresets.BUILT_IN.forEach { preset ->
        val selected = preset.gains == draft
        FilterChip(
          selected = selected,
          onClick = { vm.applyEqPreset(preset) },
          enabled = enabled,
          label = { Text(preset.name) },
        )
      }
      state.customPresets.forEach { preset ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          FilterChip(
            selected = preset.gains == draft,
            onClick = { vm.applyEqPreset(preset) },
            enabled = enabled,
            label = { Text(preset.name) },
          )
          TextButton(onClick = { vm.deleteCustomPreset(preset.name) }) { Text("×") }
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      EqFrequencies.forEachIndexed { index, frequency ->
        val gain = draft.getOrElse(index) { 0 }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            frequency,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(38.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Slider(
            value = gain.toFloat(),
            onValueChange = { vm.updateEqBand(index, it.roundToInt()) },
            valueRange = -6f..6f,
            steps = 11,
            enabled = enabled,
            modifier = Modifier.weight(1f),
          )
          Text(
            if (gain > 0) "+$gain" else "$gain",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
          )
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = vm::applyEq,
        enabled = enabled && state.eqDirty,
      ) { Text(if (state.eqDirty) "Apply changes" else "Applied") }
      OutlinedButton(
        onClick = { saveOpen = true },
        enabled = enabled,
      ) { Text("Save preset") }
    }

    if (state.model?.supportsIndexPresets == true) {
      InfoText(
        "This model accepts preset numbers rather than raw gains in the official app. " +
          "Custom bands may be ignored by the firmware.",
      )
    } else {
      InfoText("Custom presets are stored on this phone only.")
    }
  }

  if (saveOpen) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { saveOpen = false },
      title = { Text("Save EQ preset") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Saved locally for this pair of earbuds.")
          OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Preset name") },
            singleLine = true,
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            vm.saveCustomPreset(name)
            saveOpen = false
          },
          enabled = name.isNotBlank(),
        ) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { saveOpen = false }) { Text("Cancel") } },
    )
  }
}

@Composable
fun NoiseCard(state: AppUiState, vm: AppViewModel) {
  val enabled = state.controlsEnabled && state.isReady
  val noise = state.values.noise
  val familyA = state.family == Family.A
  val selected = when (noise?.mode) {
    NoiseMode.OFF -> 0
    NoiseMode.ANC -> 1
    NoiseMode.TRANSPARENCY -> 2
    null -> null
  }
  val ancPresets = listOf(1, 3, 6, 9, 12, 16)
  val level = noise?.level ?: 14
  var levelSlider by remember(level) { mutableFloatStateOf(nearestAncIndex(level).toFloat()) }

  SectionCard(
    title = "Noise control",
    subtitle = when (noise?.mode) {
      NoiseMode.OFF -> "Off"
      NoiseMode.ANC -> "Noise cancelling" + if (familyA && noise.level != 0) " · level ${noise.level}" else ""
      NoiseMode.TRANSPARENCY -> "Transparency" + if (familyA && noise.level != 0) " · level ${noise.level}" else ""
      null -> "Waiting for the earbuds…"
    },
  ) {
    ChoiceRow(
      labels = listOf("Off", "ANC", "Transparency"),
      selected = selected,
      enabled = enabled,
      onSelect = { index ->
        vm.setNoiseMode(
          when (index) {
            0 -> NoiseMode.OFF
            1 -> NoiseMode.ANC
            else -> NoiseMode.TRANSPARENCY
          },
        )
      },
    )

    if (familyA && noise?.mode == NoiseMode.ANC) {
      Text("Strength", style = MaterialTheme.typography.labelLarge)
      Slider(
        value = levelSlider,
        onValueChange = { levelSlider = it },
        onValueChangeFinished = { vm.setAncLevel(ancPresets[levelSlider.roundToInt().coerceIn(0, 5)]) },
        valueRange = 0f..5f,
        steps = 4,
        enabled = enabled,
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Low", style = MaterialTheme.typography.labelSmall)
        Text("High", style = MaterialTheme.typography.labelSmall)
      }
      OutlinedButton(
        onClick = vm::startAdaptiveNoise,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (state.adaptiveDetecting) "Adapting…" else "Run adaptive ANC")
      }
      if (state.values.adaptiveLevel != null) {
        InfoText("Last adaptive result: ${state.values.adaptiveLevel}")
      }
    }

    if (familyA && noise?.mode == NoiseMode.TRANSPARENCY) {
      ChoiceRow(
        labels = listOf("Low", "Medium", "High"),
        selected = when (noise.level) {
          2 -> 0
          8 -> 1
          14 -> 2
          else -> null
        },
        enabled = enabled,
        onSelect = { index -> vm.setTransparencyLevel(listOf(2, 8, 14)[index]) },
      )
    }

    if (!familyA) {
      InfoText("Family B firmware only exposes off, ANC and transparency — no levels.")
    }
  }
}

private fun nearestAncIndex(level: Int): Int {
  val presets = listOf(1, 3, 6, 9, 12, 16)
  var best = 0
  var bestDiff = Int.MAX_VALUE
  presets.forEachIndexed { index, value ->
    val diff = kotlin.math.abs(value - level)
    if (diff < bestDiff) {
      best = index
      bestDiff = diff
    }
  }
  return best
}

@Composable
fun VolumeCard(state: AppUiState, vm: AppViewModel) {
  val enabled = state.controlsEnabled && state.isReady
  val volume = state.values.volume
  var slider by remember(volume) { mutableFloatStateOf((volume ?: 0).toFloat()) }

  SectionCard(
    title = "Volume",
    subtitle = when {
      volume == null -> "Waiting for the earbuds…"
      else -> "${slider.roundToInt()}% · device reports $volume%"
    },
  ) {
    Slider(
      value = slider,
      onValueChange = { slider = it },
      onValueChangeFinished = { vm.setVolume(slider.roundToInt()) },
      valueRange = 0f..100f,
      enabled = enabled,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { vm.setVolume(0) }, enabled = enabled) { Text("Mute") }
      OutlinedButton(onClick = { vm.setVolume(50) }, enabled = enabled) { Text("50%") }
      OutlinedButton(onClick = { vm.setVolume(100) }, enabled = enabled) { Text("Max") }
    }
  }
}

@Composable
fun LanguageCard(state: AppUiState, vm: AppViewModel) {
  val enabled = state.controlsEnabled && state.isReady
  SectionCard(
    title = "Prompt language",
    subtitle = "Language of the voice prompts stored in the earbuds.",
  ) {
    ChoiceRow(
      labels = listOf("English", "中文"),
      selected = when (state.values.english) {
        true -> 0
        false -> 1
        null -> null
      },
      enabled = enabled,
      onSelect = { vm.setEnglish(it == 0) },
    )
  }
}

@Composable
fun FindCard(state: AppUiState, vm: AppViewModel) {
  val enabled = state.controlsEnabled && state.isReady
  SectionCard(
    title = "Find my earbuds",
    subtitle = "Plays a locating beep through the earbuds.",
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { vm.beep(true) }, enabled = enabled && !state.values.beeping) {
        Text("Play beep")
      }
      OutlinedButton(onClick = { vm.beep(false) }, enabled = enabled) { Text("Stop") }
    }
  }
}

@Composable
fun PlaybackCard(state: AppUiState, vm: AppViewModel) {
  val enabled = state.controlsEnabled && state.isReady
  val playing = state.values.playState
  SectionCard(title = "Playback", subtitle = "Sends media keys to the connected source.") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = vm::previousTrack, enabled = enabled) { Text("Previous") }
      Button(onClick = vm::playPause, enabled = enabled) {
        Text(if (playing == true) "Pause" else "Play")
      }
      OutlinedButton(onClick = vm::nextTrack, enabled = enabled) { Text("Next") }
    }
  }
}

@Composable
fun MicModeCard(state: AppUiState, vm: AppViewModel) {
  ChoiceCard(
    state = state,
    title = "Mic mode",
    subtitle = "How the earbud microphones are used.",
    labels = listOf("Multimedia", "Recitation", "Dictation"),
    selected = state.values.micMode,
    onSelect = vm::setMicMode,
  )
}

@Composable
fun VolumeLimitCard(state: AppUiState, vm: AppViewModel) {
  val selected = when (state.values.volumeLimit) {
    16 -> 0
    8 -> 1
    3 -> 2
    else -> null
  }
  SectionCard(
    title = "Volume limit",
    subtitle = "Caps the maximum output of the earbuds.",
  ) {
    ChoiceRow(
      labels = listOf("Normal", "93 dB", "85 dB"),
      selected = selected,
      enabled = state.controlsEnabled && state.isReady,
      onSelect = { vm.setVolumeLimit(listOf(16, 8, 3)[it]) },
    )
  }
}

@Composable
fun LeakModeCard(state: AppUiState, vm: AppViewModel) {
  ChoiceCard(
    state = state,
    title = "Sound mode",
    subtitle = "Leak-proof tuning for the earbuds' open design.",
    labels = listOf("Bass boost", "Leak-proof", "Vocal"),
    selected = state.values.leakMode,
    onSelect = vm::setLeakMode,
  )
}

@Composable
fun LightCard(state: AppUiState, vm: AppViewModel) {
  ChoiceCard(
    state = state,
    title = "RGB light",
    subtitle = "Lighting effect on the earbuds.",
    labels = listOf("Off", "Steady", "Gradient"),
    selected = state.values.lightMode,
    onSelect = vm::setLightMode,
  )
}

@Composable
fun TouchCard(state: AppUiState, vm: AppViewModel) {
  ChoiceCard(
    state = state,
    title = "Touch controls",
    subtitle = "Touch gestures on the earbuds.",
    labels = listOf("Off", "Basic", "Advanced"),
    selected = state.values.touchControls,
    onSelect = vm::setTouchControls,
  )
}

@Composable
fun HeatingCard(state: AppUiState, vm: AppViewModel) {
  val enabled = state.controlsEnabled && state.isReady
  val heating = state.values.heating
  SectionCard(title = "Heating", subtitle = "Gaming-headset ear-cup heating.") {
    SwitchRow(
      title = "Heating",
      checked = heating?.on == true,
      enabled = enabled,
      onCheckedChange = { vm.setHeating(it, heating?.level ?: 1) },
    )
    if (heating?.on == true) {
      ChoiceRow(
        labels = listOf("Low", "Medium", "High"),
        selected = (heating.level - 1).coerceIn(0, 2),
        enabled = enabled,
        onSelect = { vm.setHeating(true, it + 1) },
      )
    }
  }
}

@Composable
fun HeadTrackingCard(state: AppUiState, vm: AppViewModel) {
  val enabled = state.controlsEnabled && state.isReady
  SectionCard(title = "Head tracking", subtitle = "Spatial audio follows your head.") {
    SwitchRow(
      title = "Head tracking",
      checked = state.values.headTracking == true,
      enabled = enabled,
      onCheckedChange = vm::setHeadTracking,
    )
    OutlinedButton(
      onClick = vm::resetHeadTracking,
      enabled = enabled && state.values.headTracking == true,
    ) { Text("Recenter") }
  }
}

@Composable
fun SimpleSwitchCard(
  state: AppUiState,
  title: String,
  subtitle: String?,
  checked: Boolean?,
  onCheckedChange: (Boolean) -> Unit,
) {
  SectionCard(title = title, subtitle = subtitle) {
    SwitchRow(
      title = "Enabled",
      checked = checked == true,
      enabled = state.controlsEnabled && state.isReady,
      onCheckedChange = onCheckedChange,
    )
  }
}

@Composable
private fun ChoiceCard(
  state: AppUiState,
  title: String,
  subtitle: String?,
  labels: List<String>,
  selected: Int?,
  onSelect: (Int) -> Unit,
) {
  SectionCard(title = title, subtitle = subtitle) {
    ChoiceRow(
      labels = labels,
      selected = selected,
      enabled = state.controlsEnabled && state.isReady,
      onSelect = onSelect,
    )
  }
}
