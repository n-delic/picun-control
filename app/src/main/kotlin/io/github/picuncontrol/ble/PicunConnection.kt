package io.github.picuncontrol.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import io.github.picuncontrol.protocol.AdvertInfo
import io.github.picuncontrol.protocol.Advertisement
import io.github.picuncontrol.protocol.Protocol
import io.github.picuncontrol.protocol.Protocol.Reply
import io.github.picuncontrol.protocol.toHex
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Plain Android BLE transport: scan, connect, discover ECD0, enable ECD1
 * notifications, serialize writes, reconnect with backoff.
 *
 * Deliberately dependency-free (no BLE library) and offline.
 */
@SuppressLint("MissingPermission")
class PicunConnection(private val context: Context) {
  sealed interface State {
    data object Idle : State
    data object Connecting : State
    data object Discovering : State
    data object Ready : State
    data class Reconnecting(val attempt: Int) : State
    data class Disconnected(val reason: String?) : State
    data class Failed(val message: String) : State

    val isReady: Boolean get() = this is Ready
  }

  data class ScanDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val advert: AdvertInfo?,
    val hasPicunService: Boolean,
  )

  enum class LogDirection { TX, RX, INFO }

  data class LogEntry(
    val direction: LogDirection,
    val hex: String,
    val note: String = "",
    val at: Long = System.currentTimeMillis(),
  )

  private val main = Handler(Looper.getMainLooper())
  private val adapter: BluetoothAdapter? =
    context.getSystemService(BluetoothManager::class.java)?.adapter

  private val _state = MutableStateFlow<State>(State.Idle)
  val state: StateFlow<State> = _state.asStateFlow()

  private val _scanning = MutableStateFlow(false)
  val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

  private val _devices = MutableStateFlow<List<ScanDevice>>(emptyList())
  val devices: StateFlow<List<ScanDevice>> = _devices.asStateFlow()

  private val _replies = MutableSharedFlow<Reply>(extraBufferCapacity = 64)
  val replies: SharedFlow<Reply> = _replies.asSharedFlow()

  private val _log = MutableSharedFlow<LogEntry>(extraBufferCapacity = 128)
  val log: SharedFlow<LogEntry> = _log.asSharedFlow()

  private var gatt: BluetoothGatt? = null
  private var writeChar: BluetoothGattCharacteristic? = null
  private var notifyChar: BluetoothGattCharacteristic? = null
  private var notificationsRequested = false

  private var desiredAddress: String? = null
  private var manualDisconnect = true
  private var reconnectAttempt = 0

  // GATT allows one outstanding operation. We serialize writes and pace them.
  private val ops = ArrayDeque<ByteArray>()
  private var currentOp: ByteArray? = null
  private var currentAttempt = 0
  private var currentNoResponse = false
  private var writing = false
  private var writeToken = 0
  private var lastWriteAt = 0L

  fun bluetoothEnabled(): Boolean = adapter?.isEnabled == true

  // ------------------------------------------------------------------ scan

  @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
  fun startScan() {
    val scanner = adapter?.bluetoothLeScanner ?: run {
      emitInfo("Bluetooth LE scanner unavailable")
      return
    }
    if (!bluetoothEnabled()) {
      emitInfo("Bluetooth is off")
      return
    }
    _devices.value = emptyList()
    _scanning.value = true
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()
    runCatching { scanner.startScan(emptyList(), settings, scanCallback) }
      .onFailure {
        _scanning.value = false
        emitInfo("Could not start scan: ${it.message}")
      }
    main.removeCallbacks(scanTimeout)
    main.postDelayed(scanTimeout, SCAN_MILLIS)
  }

  @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
  fun stopScan() {
    main.removeCallbacks(scanTimeout)
    if (_scanning.value) {
      runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
      _scanning.value = false
    }
  }

  private val scanTimeout = Runnable {
    stopScan()
    emitInfo("Scan finished")
  }

  private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      handleScanResult(result)
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>) {
      results.forEach(::handleScanResult)
    }

    override fun onScanFailed(errorCode: Int) {
      main.post {
        _scanning.value = false
        emitInfo("Scan failed (code $errorCode)")
      }
    }
  }

  private fun handleScanResult(result: ScanResult) {
    val record = result.scanRecord
    val name = record?.deviceName?.trim().orEmpty()
      .ifEmpty { runCatching { result.device.name }.getOrNull().orEmpty().trim() }
    val hasService = record?.serviceUuids?.contains(ParcelUuid(Protocol.SERVICE)) == true

    var advert: AdvertInfo? = null
    val msd = record?.manufacturerSpecificData
    if (msd != null) {
      for (i in 0 until msd.size()) {
        advert = Advertisement.parseManufacturer(msd.keyAt(i), msd.valueAt(i))
        if (advert != null) break
      }
    }

    val picunLike = advert != null || hasService || name.contains("picun", ignoreCase = true)
    if (!picunLike) return
    if (name.isEmpty() && advert == null && !hasService) return

    val address = result.device.address
    val device = ScanDevice(
      name = name.ifEmpty { "Picun device" },
      address = address,
      rssi = result.rssi,
      advert = advert,
      hasPicunService = hasService,
    )
    main.post {
      _devices.update { current ->
        val existing = current.indexOfFirst { it.address == address }
        val next = current.toMutableList()
        if (existing >= 0) {
          val old = next[existing]
          next[existing] = device.copy(
            name = device.name.takeUnless { it == "Picun device" } ?: old.name,
            advert = device.advert ?: old.advert,
          )
        } else {
          next += device
        }
        next.sortedWith(
          compareByDescending<ScanDevice> { it.advert != null }
            .thenByDescending { it.name.contains("picun", ignoreCase = true) }
            .thenByDescending { it.rssi },
        )
      }
    }
  }

  // ------------------------------------------------------------------ connect

  @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
  fun connect(address: String) {
    stopScan()
    manualDisconnect = false
    desiredAddress = address
    reconnectAttempt = 0
    openGatt(address)
  }

  @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
  private fun openGatt(address: String) {
    closeGatt()
    ops.clear()
    currentOp = null
    currentAttempt = 0
    currentNoResponse = false
    writing = false
    writeChar = null
    notifyChar = null
    notificationsRequested = false
    _state.value = State.Connecting
    emitInfo("Connecting to $address…")
    val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
      ?: run {
        fail("Invalid Bluetooth address")
        return
      }
    val newGatt = runCatching {
      device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }.getOrNull()
    gatt = newGatt
    if (newGatt == null) {
      fail("Could not start connection")
      return
    }
    main.postDelayed({
      if (gatt === newGatt && !_state.value.isReady) {
        dropConnection("Connection timed out", 0)
      }
    }, CONNECT_TIMEOUT_MS)
  }

  @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
  fun disconnect() {
    manualDisconnect = true
    desiredAddress = null
    reconnectAttempt = 0
    closeGatt()
    ops.clear()
    currentOp = null
    writing = false
    writeChar = null
    notifyChar = null
    notificationsRequested = false
    _state.value = State.Idle
    emitInfo("Disconnected")
  }

  fun close() {
    stopScan()
    manualDisconnect = true
    desiredAddress = null
    closeGatt()
    main.removeCallbacksAndMessages(null)
    _state.value = State.Idle
  }

  private fun closeGatt() {
    runCatching { gatt?.close() }
    gatt = null
  }

  private fun enableNotifications(g: BluetoothGatt) {
    if (g !== gatt || notificationsRequested) return
    val notify = notifyChar ?: return
    notificationsRequested = true
    if (!g.setCharacteristicNotification(notify, true)) {
      fail("Could not enable notifications")
      return
    }
    val cccd = notify.getDescriptor(Protocol.CCCD)
    if (cccd == null) {
      fail("Notification descriptor is missing")
      return
    }
    val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    val ok = if (Build.VERSION.SDK_INT >= 33) {
      g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
    } else {
      @Suppress("DEPRECATION")
      run {
        cccd.value = value
        g.writeDescriptor(cccd)
      }
    }
    if (!ok) fail("Could not subscribe to replies")
  }

  private val gattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
      main.post {
        if (g !== gatt) return@post
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
          reconnectAttempt = 0
          _state.value = State.Discovering
          emitInfo("Connected, discovering services…")
          if (!runCatching { g.discoverServices() }.getOrDefault(false)) {
            dropConnection("Service discovery could not start", status)
          }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
          val reason = when (status) {
            BluetoothGatt.GATT_SUCCESS -> null
            133 -> "Connection lost (status 133)"
            else -> "Disconnected (status $status)"
          }
          dropConnection(reason, status)
        }
      }
    }

    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
      main.post {
        if (g !== gatt) return@post
        if (status != BluetoothGatt.GATT_SUCCESS) {
          fail("Service discovery failed (status $status)")
          return@post
        }
        val service = g.getService(Protocol.SERVICE)
        if (service == null) {
          fail("This device does not expose the Picun control service")
          return@post
        }
        val write = service.getCharacteristic(Protocol.WRITE)
        val notify = service.getCharacteristic(Protocol.NOTIFY)
        if (write == null || notify == null) {
          fail("Picun control characteristics are missing")
          return@post
        }
        writeChar = write
        notifyChar = notify
        // Family B rename packets can exceed the default 20-byte ATT payload.
        val mtuRequested = runCatching { g.requestMtu(DESIRED_MTU) }.getOrDefault(false)
        if (!mtuRequested) {
          enableNotifications(g)
        } else {
          // Some stacks never deliver onMtuChanged; don't block the session on it.
          main.postDelayed({
            if (g === gatt && _state.value !is State.Ready) enableNotifications(g)
          }, MTU_TIMEOUT_MS)
        }
      }
    }

    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
      main.post {
        if (g !== gatt) return@post
        if (status == BluetoothGatt.GATT_SUCCESS) emitInfo("MTU $mtu")
        enableNotifications(g)
      }
    }

    override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
      main.post {
        if (g !== gatt) return@post
        if (descriptor.uuid != Protocol.CCCD) return@post
        if (status == BluetoothGatt.GATT_SUCCESS) {
          _state.value = State.Ready
          emitInfo("Ready")
        } else {
          fail("Could not subscribe to replies (status $status)")
        }
      }
    }

    override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
      main.post {
        if (g !== gatt) return@post
        if (currentNoResponse && status == BluetoothGatt.GATT_SUCCESS) {
          // Fire-and-forget writes are advanced by the timeout in pump().
          return@post
        }
        val op = currentOp
        if (status == BluetoothGatt.GATT_SUCCESS) {
          currentOp = null
          currentAttempt = 0
          currentNoResponse = false
          writing = false
          lastWriteAt = SystemClock.elapsedRealtime()
          pump()
        } else {
          emitInfo("Write failed (status $status)")
          retryOrDrop(op)
        }
      }
    }

    @Deprecated("Pre-Android 13 callback")
    override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
      if (Build.VERSION.SDK_INT >= 33) return
      @Suppress("DEPRECATION")
      onNotification(g, characteristic.value)
    }

    override fun onCharacteristicChanged(
      g: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray,
    ) {
      onNotification(g, value)
    }
  }

  private fun onNotification(g: BluetoothGatt, value: ByteArray?) {
    if (value == null || value.isEmpty()) return
    val raw = value.copyOf()
    val reply = Protocol.parse(raw)
    main.post {
      if (g !== gatt) return@post
      _log.tryEmit(LogEntry(LogDirection.RX, raw.toHex()))
      if (reply == null) {
        emitInfo("Dropped a packet with a bad length or CRC")
      } else {
        _replies.tryEmit(reply)
      }
    }
  }

  private fun dropConnection(reason: String?, status: Int) {
    closeGatt()
    writeChar = null
    notifyChar = null
    notificationsRequested = false
    ops.clear()
    currentOp = null
    currentNoResponse = false
    writing = false
    val address = desiredAddress
    if (manualDisconnect || address == null) {
      _state.value = State.Disconnected(reason)
      return
    }
    if (reconnectAttempt >= RECONNECT_DELAYS_MS.size) {
      _state.value = State.Failed(reason ?: "Connection lost")
      desiredAddress = null
      return
    }
    reconnectAttempt += 1
    val delay = RECONNECT_DELAYS_MS[reconnectAttempt - 1]
    _state.value = State.Reconnecting(reconnectAttempt)
    emitInfo("${reason ?: "Connection lost"} — retrying in ${delay / 1000}s")
    main.postDelayed({
      if (!manualDisconnect && desiredAddress == address) openGatt(address)
    }, delay)
  }

  private fun fail(message: String) {
    manualDisconnect = true
    desiredAddress = null
    closeGatt()
    writeChar = null
    notifyChar = null
    notificationsRequested = false
    ops.clear()
    currentOp = null
    currentNoResponse = false
    writing = false
    _state.value = State.Failed(message)
    emitInfo(message)
  }

  private fun retryOrDrop(op: ByteArray?) {
    if (op == null) {
      writing = false
      pump()
      return
    }
    currentAttempt += 1
    if (currentAttempt > MAX_WRITE_RETRIES) {
      emitInfo("Giving up on a write after ${MAX_WRITE_RETRIES + 1} attempts")
      currentOp = null
      currentAttempt = 0
      writing = false
      pump()
      return
    }
    currentOp = op
    writing = false
    // The op was popped when it was sent; put it back at the front for the retry.
    ops.addFirst(op)
    lastWriteAt = SystemClock.elapsedRealtime()
    main.postDelayed({ pump() }, RETRY_DELAY_MS)
  }

  // ------------------------------------------------------------------ writes

  @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
  fun send(bytes: ByteArray) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      main.post { send(bytes) }
      return
    }
    if (_state.value !is State.Ready) {
      emitInfo("Not connected; command not sent")
      return
    }
    ops.addLast(bytes.copyOf())
    pump()
  }

  @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
  private fun pump() {
    if (writing || ops.isEmpty()) return
    val now = SystemClock.elapsedRealtime()
    val wait = (WRITE_GAP_MS - (now - lastWriteAt)).coerceAtLeast(0)
    if (wait > 0) {
      main.postDelayed({ pump() }, wait)
      return
    }
    val op = ops.removeFirst()
    val g = gatt
    val characteristic = writeChar
    if (g == null || characteristic == null) {
      ops.clear()
      return
    }
    currentOp = op
    writing = true
    lastWriteAt = now
    val token = ++writeToken
    _log.tryEmit(LogEntry(LogDirection.TX, op.toHex()))
    val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
      BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    } else {
      BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    }
    currentNoResponse = type == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    val ok = runCatching {
      if (Build.VERSION.SDK_INT >= 33) {
        g.writeCharacteristic(characteristic, op, type) == BluetoothStatusCodes.SUCCESS
      } else {
        @Suppress("DEPRECATION")
        run {
          characteristic.writeType = type
          characteristic.value = op
          g.writeCharacteristic(characteristic)
        }
      }
    }.getOrDefault(false)
    if (!ok) {
      emitInfo("Write rejected by the Bluetooth stack")
      retryOrDrop(op)
      return
    }
    // No-response writes do not always get onCharacteristicWrite. Never wedge the queue.
    val timeout = if (currentNoResponse) NO_RESPONSE_ADVANCE_MS else WRITE_TIMEOUT_MS
    main.postDelayed({
      if (writing && token == writeToken) {
        currentOp = null
        currentAttempt = 0
        currentNoResponse = false
        writing = false
        lastWriteAt = SystemClock.elapsedRealtime()
        pump()
      }
    }, timeout)
  }

  private fun emitInfo(note: String) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      main.post { emitInfo(note) }
      return
    }
    _log.tryEmit(LogEntry(LogDirection.INFO, "", note))
  }

  private companion object {
    const val SCAN_MILLIS = 15_000L
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val DESIRED_MTU = 64
    const val MTU_TIMEOUT_MS = 1_500L
    const val WRITE_GAP_MS = 60L
    const val WRITE_TIMEOUT_MS = 3_000L
    const val NO_RESPONSE_ADVANCE_MS = 120L
    const val RETRY_DELAY_MS = 300L
    const val MAX_WRITE_RETRIES = 2
    val RECONNECT_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)
  }
}
