package io.github.picuncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.picuncontrol.model.Feature
import io.github.picuncontrol.protocol.Protocol.Family
import io.github.picuncontrol.protocol.Replies

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(state: AppUiState, vm: AppViewModel) {
  var menuOpen by remember { mutableStateOf(false) }
  var renameOpen by remember { mutableStateOf(false) }
  var aboutOpen by remember { mutableStateOf(false) }
  var resetOpen by remember { mutableStateOf(false) }
  var pairingOpen by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxSize()) {
    TopAppBar(
      navigationIcon = {
        IconButton(onClick = vm::disconnect) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Disconnect and go back")
        }
      },
      title = {
        Column {
          Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            state.model?.let { "${it.name} · ${it.deviceModel}/${it.deviceNo}" }
              ?: "Unknown model · ${state.advert?.productType ?: "?"}/${state.advert?.productNumber ?: "?"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      },
      actions = {
        IconButton(onClick = vm::refresh, enabled = state.isReady) {
          Icon(Icons.Filled.Refresh, contentDescription = "Refresh all settings")
        }
        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More")
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Diagnostics & raw log") },
              onClick = {
                menuOpen = false
                vm.setAdvanced(true)
              },
            )
            DropdownMenuItem(
              text = { Text("Rename earbuds") },
              enabled = state.isReady && state.controlsEnabled,
              onClick = {
                menuOpen = false
                renameOpen = true
              },
            )
            DropdownMenuItem(
              text = { Text("About & privacy") },
              onClick = {
                menuOpen = false
                aboutOpen = true
              },
            )
            HorizontalDivider()
            DropdownMenuItem(
              text = { Text("Disconnect") },
              onClick = {
                menuOpen = false
                vm.disconnect()
              },
            )
          }
        }
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { ConnectionBanner(state.connection, vm::reconnect) }

      if (!state.modelKnown) {
        item { UnknownModelCard(state, vm) }
      }

      item { StatusCard(state, vm) }

      if (state.has(Feature.GAME_MODE)) {
        item {
          SectionCard(
            title = "Low latency",
            subtitle = "Game mode lowers audio delay. Confirmed on the ANC-05Max.",
          ) {
            SwitchRow(
              title = "Low latency mode",
              description = "The switch reflects what the earbuds report, not what was tapped.",
              checked = state.values.gameMode == true,
              enabled = state.controlsEnabled && state.isReady,
              onCheckedChange = vm::setGameMode,
            )
          }
        }
      }

      if (state.has(Feature.ANC)) item { NoiseCard(state, vm) }
      if (state.has(Feature.EQ)) item { EqCard(state, vm) }
      if (state.has(Feature.VOLUME)) item { VolumeCard(state, vm) }

      if (state.has(Feature.SPATIAL)) {
        item {
          SimpleSwitchCard(
            state = state,
            title = "Spatial audio",
            subtitle = "Widens the stereo image.",
            checked = state.values.spatial,
            onCheckedChange = vm::setSpatial,
          )
        }
      }

      if (state.has(Feature.LANGUAGE)) item { LanguageCard(state, vm) }
      if (state.has(Feature.PLAY_CONTROLS)) item { PlaybackCard(state, vm) }
      if (state.has(Feature.FIND_MY_BUDS)) item { FindCard(state, vm) }
      if (state.has(Feature.MIC_MODE)) item { MicModeCard(state, vm) }
      if (state.has(Feature.VOLUME_LIMIT)) item { VolumeLimitCard(state, vm) }
      if (state.has(Feature.LEAK_MODE)) item { LeakModeCard(state, vm) }
      if (state.has(Feature.LIGHT)) item { LightCard(state, vm) }
      if (state.has(Feature.HEATING)) item { HeatingCard(state, vm) }
      if (state.has(Feature.HEAD_TRACKING)) item { HeadTrackingCard(state, vm) }
      if (state.has(Feature.TOUCH)) item { TouchCard(state, vm) }

      if (state.has(Feature.SOMATOSENSORY)) {
        item {
          SimpleSwitchCard(
            state = state,
            title = "Motion control",
            subtitle = "Somatosensory gesture mode.",
            checked = state.values.somatosensory,
            onCheckedChange = vm::setSomatosensory,
          )
        }
      }

      if (state.has(Feature.WEAR)) {
        item {
          SimpleSwitchCard(
            state = state,
            title = "Wear detection",
            subtitle = "Pause automatically when an earbud is removed.",
            checked = state.values.wearDetection,
            onCheckedChange = vm::setWearDetection,
          )
        }
      }

      if (state.has(Feature.WINDPROOF)) {
        item {
          SimpleSwitchCard(
            state = state,
            title = "Wind noise reduction",
            subtitle = "Reduces wind rumble in the microphones.",
            checked = state.values.windproof,
            onCheckedChange = vm::setWindproof,
          )
        }
      }

      if (state.has(Feature.LDAC)) {
        item {
          SimpleSwitchCard(
            state = state,
            title = "LDAC",
            subtitle = "High-bitrate codec. Needs a compatible source and may reduce range.",
            checked = state.values.ldac,
            onCheckedChange = vm::setLdac,
          )
        }
      }

      if (state.has(Feature.BASS_BOOST)) {
        item {
          SimpleSwitchCard(
            state = state,
            title = "Bass boost",
            subtitle = "Extra low-end emphasis.",
            checked = state.values.bassBoost,
            onCheckedChange = vm::setBassBoost,
          )
        }
      }

      if (state.has(Feature.SLEEP)) {
        item {
          SimpleSwitchCard(
            state = state,
            title = "Sleep mode",
            subtitle = "Low-power mode for overnight use.",
            checked = state.values.sleep,
            onCheckedChange = vm::setSleep,
          )
        }
      }

      item { SettingsCard(state, vm, onRename = { renameOpen = true }) }
      item { ModelInfoCard(state) }

      item {
        SectionCard(
          title = "Danger zone",
          subtitle = "These commands change or erase the earbuds' own settings.",
        ) {
          OutlinedButton(
            onClick = { pairingOpen = true },
            enabled = state.isReady && state.controlsEnabled,
            modifier = Modifier.fillMaxWidth(),
          ) { Text("Clear pairing records") }
          OutlinedButton(
            onClick = { resetOpen = true },
            enabled = state.isReady && state.controlsEnabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
          ) { Text("Factory reset") }
        }
      }

      item {
        Text(
          "Picun Control is unofficial and not affiliated with Picun. " +
            "No account, no network permission, no analytics.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item { Spacer(Modifier.height(24.dp)) }
    }
  }

  if (renameOpen) {
    RenameDialog(
      currentName = state.title,
      onDismiss = { renameOpen = false },
      onConfirm = {
        vm.rename(it)
        renameOpen = false
      },
    )
  }

  if (aboutOpen) {
    AboutDialog(onDismiss = { aboutOpen = false })
  }

  if (resetOpen) {
    AlertDialog(
      onDismissRequest = { resetOpen = false },
      title = { Text("Factory reset the earbuds?") },
      text = {
        Text(
          "This sends the official factory-reset command. Pairings, EQ and all stored settings on " +
            "the earbuds will be erased, and the current Bluetooth connection may drop. " +
            "The app cannot undo this.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            vm.factoryReset()
            resetOpen = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Reset") }
      },
      dismissButton = { TextButton(onClick = { resetOpen = false }) { Text("Cancel") } },
    )
  }

  if (pairingOpen) {
    AlertDialog(
      onDismissRequest = { pairingOpen = false },
      title = { Text("Clear pairing records?") },
      text = {
        Text(
          "This unpairs known phones from the earbuds and may drop the current connection. " +
            "You will need to pair again in Android's Bluetooth settings. The app cannot undo this.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            vm.clearPairing()
            pairingOpen = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Clear pairings") }
      },
      dismissButton = { TextButton(onClick = { pairingOpen = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun StatusCard(state: AppUiState, vm: AppViewModel) {
  SectionCard(
    title = "Status",
    subtitle = when {
      state.isReady && state.family != null -> "Connected · Family ${state.family.name}"
      state.isReady && state.probeFailed -> "Connected · protocol not detected"
      state.isReady -> "Connected · detecting protocol…"
      else -> "Waiting for the connection"
    },
  ) {
    BatterySummary(state.values.battery)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      Column {
        Text("Protocol", style = MaterialTheme.typography.labelSmall)
        Text(
          state.family?.let { "Family ${it.name}" } ?: "Unknown",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      Column {
        Text("Model", style = MaterialTheme.typography.labelSmall)
        Text(state.model?.name ?: "Unknown", style = MaterialTheme.typography.bodyMedium)
      }
      if (!state.controlsEnabled) {
        Column {
          Text("Mode", style = MaterialTheme.typography.labelSmall)
          Text("Read-only", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
    if (state.isReady && state.family == null) {
      OutlinedButton(onClick = vm::retryDetection, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.probeFailed) "Retry protocol detection" else "Detecting… tap to retry")
      }
    }
  }
}

@Composable
private fun UnknownModelCard(state: AppUiState, vm: AppViewModel) {
  SectionCard(
    title = "Unrecognized model",
    subtitle = "Product ${state.advert?.productType ?: "?"}/${state.advert?.productNumber ?: "?"}",
  ) {
    Text(
      if (state.controlsEnabled) {
        "Experimental controls are enabled for this device. The app is using the common control set; " +
          "some commands may do nothing or behave differently on this model."
      } else {
        "This model is not in the bundled catalogue, so the app stays read-only until you opt in. " +
          "Queries are safe and show what the earbuds report."
      },
      style = MaterialTheme.typography.bodyMedium,
    )
    if (!state.controlsEnabled) {
      OutlinedButton(
        onClick = vm::enableExperimentalControls,
        enabled = state.family != null,
        modifier = Modifier.fillMaxWidth(),
      ) { Text("Enable experimental controls") }
    }
    InfoText("Open Diagnostics to see every reply and help map this model.")
  }
}

@Composable
private fun SettingsCard(state: AppUiState, vm: AppViewModel, onRename: () -> Unit) {
  SectionCard(title = "Device settings", subtitle = "Local preferences and persistent earbud settings.") {
    if (state.has(Feature.GAME_MODE)) {
      SwitchRow(
        title = "Re-apply low latency on connect",
        description = "Off by default: we have not confirmed whether the earbuds remember game mode " +
          "after a power cycle.",
        checked = state.autoApplyGameMode,
        onCheckedChange = vm::setAutoApplyGameMode,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = onRename,
        enabled = state.isReady && state.controlsEnabled,
      ) { Text("Rename earbuds") }
      OutlinedButton(
        onClick = vm::disconnect,
        enabled = state.activeAddress != null,
      ) { Text("Disconnect") }
    }
    InfoText("Android's own Bluetooth settings still control pairing. This app never unpairs unless you ask it to.")
  }
}

@Composable
private fun ModelInfoCard(state: AppUiState) {
  val model = state.model ?: return
  SectionCard(title = "Model details", subtitle = model.category.ifBlank { null }) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      Column {
        Text("Catalogue ID", style = MaterialTheme.typography.labelSmall)
        Text("${model.id}", style = MaterialTheme.typography.bodyMedium)
      }
      Column {
        Text("Product", style = MaterialTheme.typography.labelSmall)
        Text("${model.deviceModel}/${model.deviceNo}", style = MaterialTheme.typography.bodyMedium)
      }
      Column {
        Text("Status", style = MaterialTheme.typography.labelSmall)
        Text(
          if (model.isDelisted) "Delisted" else "Listed",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    if (model.bluetoothName.isNotBlank()) {
      Text(
        "Advertised as “${model.bluetoothName}”",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (model.isDelisted) {
      InfoText(
        "Picun removed this model from its own app catalogue, which is why the official app " +
          "no longer shows it. The BLE protocol still works.",
      )
    }
    InfoText("Model data is bundled from Picun's public catalogue (2026-11-12). No network is used at runtime.")
  }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  var name by remember { mutableStateOf(currentName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename earbuds") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "This is written to the earbuds and survives restarts. Use 1–15 plain ASCII characters. " +
            "The connection may drop while the name is applied.",
          style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
          value = name,
          onValueChange = { name = it.take(15) },
          label = { Text("Bluetooth name") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(name) },
        enabled = name.trim().length in 1..15,
      ) { Text("Rename") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Picun Control") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Unofficial, offline control app for Picun earbuds.", fontWeight = FontWeight.SemiBold)
        Text("• No internet permission\n• No account or login\n• No location permission on Android 12+\n• No analytics or ads")
        Text(
          "The BLE protocol was reverse-engineered from the official app and this project's " +
            "documentation. Some features are still unverified on hardware.",
          style = MaterialTheme.typography.bodySmall,
        )
        Text(
          "Not affiliated with Picun. “Picun” is used only to describe compatibility.",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}
