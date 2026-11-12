package io.github.picuncontrol.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.picuncontrol.ble.PicunConnection

@Composable
fun ScanScreen(state: AppUiState, vm: AppViewModel) {
  val context = LocalContext.current
  val adapter = remember(context) {
    context.getSystemService(BluetoothManager::class.java)?.adapter
  }
  val permissions = remember {
    if (Build.VERSION.SDK_INT >= 31) {
      arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
  }
  var permissionDenied by rememberSaveable { mutableStateOf(false) }
  var bluetoothDenied by rememberSaveable { mutableStateOf(false) }
  var hasScanned by rememberSaveable { mutableStateOf(false) }

  fun hasPermissions(): Boolean = permissions.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }

  fun scanNow() {
    hasScanned = true
    when {
      !hasPermissions() -> permissionDenied = true
      adapter?.isEnabled != true -> bluetoothDenied = true
      else -> {
        permissionDenied = false
        bluetoothDenied = false
        vm.startScan()
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { result ->
    if (result.values.all { it }) {
      permissionDenied = false
      if (adapter?.isEnabled == true) {
        hasScanned = true
        vm.startScan()
      } else {
        bluetoothDenied = true
      }
    } else {
      permissionDenied = true
    }
  }

  val enableBluetooth = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) {
    if (adapter?.isEnabled == true) {
      bluetoothDenied = false
      hasScanned = true
      vm.startScan()
    }
  }

  LaunchedEffect(Unit) {
    if (hasPermissions()) {
      if (runCatching { adapter?.isEnabled == true }.getOrDefault(false)) {
        if (state.scanDevices.isEmpty() && !state.scanning) {
          hasScanned = true
          vm.startScan()
        }
      } else {
        bluetoothDenied = true
      }
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Spacer(Modifier.height(24.dp))
      Text(
        "Picun Control",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        "Direct Bluetooth control for Picun earbuds.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("No account", style = MaterialTheme.typography.labelMedium)
        Text("No internet", style = MaterialTheme.typography.labelMedium)
        Text("No tracking", style = MaterialTheme.typography.labelMedium)
      }
      Spacer(Modifier.height(16.dp))
    }

    if (permissionDenied) {
      item {
        ElevatedCard {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bluetooth permission needed", style = MaterialTheme.typography.titleSmall)
            Text(
              if (Build.VERSION.SDK_INT >= 31) {
                "Allow Nearby devices so the app can scan for and connect to your earbuds."
              } else {
                "Allow location while scanning. Android 11 and older require it for Bluetooth scans; the app never reads or stores location."
              },
              style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = { permissionLauncher.launch(permissions) }) { Text("Grant permission") }
              OutlinedButton(onClick = {
                context.startActivity(
                  Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                  ),
                )
              }) { Text("App settings") }
            }
          }
        }
      }
    }

    if (bluetoothDenied) {
      item {
        ElevatedCard {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bluetooth is off", style = MaterialTheme.typography.titleSmall)
            Button(onClick = {
              enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }) { Text("Turn on Bluetooth") }
          }
        }
      }
    }

    val last = state.lastDevice
    if (last != null && !state.scanning) {
      item {
        ElevatedCard {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(Modifier.weight(1f)) {
              Text("Last device", style = MaterialTheme.typography.labelMedium)
              Text(
                last.second.ifBlank { last.first },
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                last.first,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Button(onClick = vm::connectLast) { Text("Connect") }
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (state.scanning) {
          Button(onClick = vm::stopScan, modifier = Modifier.weight(1f)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text("Stop scan")
          }
        } else {
          Button(onClick = ::scanNow, modifier = Modifier.weight(1f)) { Text("Scan for earbuds") }
        }
      }
    }

    if (state.scanning) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          Text("Looking for Picun devices…", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }

    if (state.scanDevices.isNotEmpty()) {
      item {
        Text(
          "Found devices",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      items(state.scanDevices, key = { it.address }) { device ->
        DeviceRow(device, modelLabel = vm.knownModel(device)?.let { "${it.name} · ${it.family.name}" }, onClick = {
          vm.selectDevice(device)
        })
      }
    } else if (!state.scanning && hasScanned) {
      item {
        Text(
          "Nothing found yet. Make sure the earbuds are on, nearby, and not connected to another phone.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
    item {
      InfoText(
        "This app only talks to devices that expose the Picun control service or advertise as Picun. " +
          "It bundles the public model catalogue, so it never needs the network.",
      )
    }
    item { Spacer(Modifier.height(32.dp)) }
  }
}

@Composable
private fun DeviceRow(device: PicunConnection.ScanDevice, modelLabel: String?, onClick: () -> Unit) {
  val advert = device.advert
  val subtitle = buildString {
    if (modelLabel != null) append(modelLabel)
    else if (advert != null) append("Family ${advert.family.name} · ${advert.productType}/${advert.productNumber}")
    else append("Picun-compatible device")
    append(" · ")
    append(device.address)
  }
  ListItem(
    headlineContent = { Text(device.name.trim().ifBlank { device.address }) },
    supportingContent = { Text(subtitle) },
    trailingContent = {
      Text(
        "${device.rssi} dBm",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  )
}
