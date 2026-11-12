package io.github.picuncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.picuncontrol.ble.PicunConnection
import io.github.picuncontrol.protocol.Replies

@Composable
fun SectionCard(
  title: String,
  subtitle: String? = null,
  trailing: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.elevatedCardColors(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          if (subtitle != null) {
            Text(
              subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        trailing?.invoke()
      }
      content()
    }
  }
}

@Composable
fun SwitchRow(
  title: String,
  description: String? = null,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = onCheckedChange,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      if (description != null) {
        Text(
          description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
  }
}

@Composable
fun ChoiceRow(
  labels: List<String>,
  selected: Int?,
  enabled: Boolean = true,
  onSelect: (Int) -> Unit,
) {
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    labels.forEachIndexed { index, label ->
      SegmentedButton(
        selected = selected == index,
        onClick = { onSelect(index) },
        enabled = enabled,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
      ) {
        Text(label, maxLines = 1)
      }
    }
  }
}

@Composable
fun ToggleRow(
  title: String,
  description: String? = null,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
) = SwitchRow(title, description, checked, enabled, onCheckedChange)

@Composable
fun BatteryLine(label: String, percent: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.width(30.dp),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LinearProgressIndicator(
      progress = { (percent.coerceIn(0, 100)) / 100f },
      modifier = Modifier.weight(1f).height(8.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text("$percent%", style = MaterialTheme.typography.labelMedium)
  }
}

@Composable
fun BatterySummary(battery: Replies.Battery?) {
  if (battery == null || !battery.hasAny) {
    Text(
      "Battery: unknown",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    battery.left?.let { BatteryLine("L", it) }
    battery.right?.let { BatteryLine("R", it) }
  }
}

@Composable
fun ConnectionBanner(
  connection: PicunConnection.State,
  onRetry: () -> Unit,
) {
  if (connection is PicunConnection.State.Ready) return
  val text = when (connection) {
    is PicunConnection.State.Idle -> "Not connected"
    is PicunConnection.State.Connecting -> "Connecting…"
    is PicunConnection.State.Discovering -> "Setting up the control channel…"
    is PicunConnection.State.Reconnecting -> "Connection lost. Reconnecting (attempt ${connection.attempt})…"
    is PicunConnection.State.Disconnected -> connection.reason ?: "Disconnected"
    is PicunConnection.State.Failed -> connection.message
    is PicunConnection.State.Ready -> ""
  }
  val busy = connection is PicunConnection.State.Connecting ||
    connection is PicunConnection.State.Discovering ||
    connection is PicunConnection.State.Reconnecting
  val failed = connection is PicunConnection.State.Failed ||
    connection is PicunConnection.State.Disconnected
  val container = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
  val content = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer

  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.elevatedCardColors(containerColor = container),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (busy) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
      }
      Text(
        text,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
        color = content,
      )
      if (!busy) {
        Button(onClick = onRetry) { Text("Reconnect") }
      }
    }
  }
}

@Composable
fun InfoText(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
