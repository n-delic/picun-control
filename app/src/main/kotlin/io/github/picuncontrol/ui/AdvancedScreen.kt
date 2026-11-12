package io.github.picuncontrol.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.picuncontrol.ble.PicunConnection
import io.github.picuncontrol.ble.PicunConnection.LogDirection
import io.github.picuncontrol.protocol.Commands
import io.github.picuncontrol.protocol.Protocol.Family
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class QueryItem(val label: String, val bytes: ByteArray?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(state: AppUiState, vm: AppViewModel) {
  val context = LocalContext.current
  val clipboard = context.getSystemService(ClipboardManager::class.java)
  val family = state.family
  var probesOpen by rememberSaveable { mutableStateOf(false) }

  val queries = if (family == null) {
    emptyList()
  } else {
    listOf(
      QueryItem("Battery", Commands.queryBattery(family)),
      QueryItem("Volume", Commands.queryVolume(family)),
      QueryItem("Game / low latency", Commands.queryGameMode(family)),
      QueryItem("Noise mode", Commands.queryNoise(family)),
      QueryItem("Adaptive ANC", Commands.queryAdaptiveNoise(family)),
      QueryItem("Equalizer", Commands.queryEq(family)),
      QueryItem("Spatial audio", Commands.querySpatial(family)),
      QueryItem("Prompt language", Commands.queryLanguage(family)),
      QueryItem("Play state", Commands.queryPlayState(family)),
      QueryItem("Bluetooth name", Commands.queryName(family)),
      QueryItem("Mic mode", Commands.queryMicMode(family)),
      QueryItem("Volume limit", Commands.queryVolumeLimit(family)),
      QueryItem("Sound mode", Commands.queryLeakMode(family)),
      QueryItem("RGB light", Commands.queryLight(family)),
      QueryItem("Heating", Commands.queryHeating(family)),
      QueryItem("Somatosensory", Commands.querySomatosensory(family)),
      QueryItem("Head tracking", Commands.queryHeadTracking(family)),
      QueryItem("Touch controls", Commands.queryTouchControls(family)),
      QueryItem("Wear detection", Commands.queryWearDetection(family)),
      QueryItem("Windproof", Commands.queryWindproof(family)),
      QueryItem("LDAC", Commands.queryLdac(family)),
      QueryItem("Bass boost", Commands.queryBassBoost(family)),
      QueryItem("Sleep mode", Commands.querySleep(family)),
      QueryItem("Wallpaper", Commands.queryWallpaper(family)),
    )
  }

  Column(Modifier.fillMaxSize()) {
    TopAppBar(
      navigationIcon = {
        IconButton(onClick = { vm.setAdvanced(false) }) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
      title = { Text("Diagnostics") },
      actions = {
        TextButton(onClick = {
          clipboard?.setPrimaryClip(
            ClipData.newPlainText(
              "Picun Control log",
              state.log.joinToString("\n") { formatLog(it) },
            ),
          )
        }) { Text("Copy log") }
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        SectionCard(
          title = "Read-only probes",
          subtitle = "Sends the official query for each feature, including ones the catalogue hides. " +
            "Queries never change settings.",
          trailing = {
            if (family != null) {
              TextButton(onClick = { probesOpen = !probesOpen }) {
                Text(if (probesOpen) "Hide" else "Show")
              }
            }
          },
        ) {
          Text(
            "Protocol: ${family?.let { "Family ${it.name}" } ?: "not detected"} · " +
              "${state.title}",
            style = MaterialTheme.typography.bodySmall,
          )
          if (family == null) {
            InfoText("Connect first; the query bytes depend on the protocol family.")
          } else if (!probesOpen) {
            InfoText("${queries.size} probes available. Open this section to send them.")
          } else {
            InfoText("Greyed-out probes have no Family B variant in the official app.")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              queries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  row.forEach { query ->
                    OutlinedButton(
                      onClick = { vm.experimentalQuery(query.bytes) },
                      enabled = state.isReady && query.bytes != null,
                      modifier = Modifier.weight(1f),
                    ) {
                      Text(
                        query.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                      )
                    }
                  }
                  if (row.size == 1) {
                    Column(Modifier.weight(1f)) { }
                  }
                }
              }
            }
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            "Raw log (${state.log.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = vm::clearLog) { Text("Clear") }
        }
      }

      if (state.log.isEmpty()) {
        item {
          InfoText("No traffic yet. Commands and replies will appear here as hex bytes.")
        }
      } else {
        items(state.log) { entry ->
          val prefix = when (entry.direction) {
            LogDirection.TX -> "→"
            LogDirection.RX -> "←"
            LogDirection.INFO -> "•"
          }
          val text = if (entry.direction == LogDirection.INFO) {
            "${formatTime(entry.at)}  $prefix ${entry.note}"
          } else {
            "${formatTime(entry.at)}  $prefix ${entry.hex}"
          }
          Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = if (entry.direction == LogDirection.RX) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurface
            },
          )
        }
      }

      item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
      item {
        InfoText(
          "CRC-invalid packets are dropped and logged, never acted on. This section is read-only on purpose; " +
            "there is no arbitrary-hex sender.",
        )
      }
    }
  }
}

private fun formatTime(at: Long): String =
  SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(at))

private fun formatLog(entry: PicunConnection.LogEntry): String {
  val prefix = when (entry.direction) {
    LogDirection.TX -> "→"
    LogDirection.RX -> "←"
    LogDirection.INFO -> "•"
  }
  val body = if (entry.direction == LogDirection.INFO) entry.note else entry.hex
  return "${formatTime(entry.at)}  $prefix $body"
}
