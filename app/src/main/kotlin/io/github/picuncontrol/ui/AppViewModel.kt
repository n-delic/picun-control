package io.github.picuncontrol.ui

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.picuncontrol.ble.PicunConnection
import io.github.picuncontrol.ble.PicunConnection.ScanDevice
import io.github.picuncontrol.data.EqPreset
import io.github.picuncontrol.data.Prefs
import io.github.picuncontrol.model.Feature
import io.github.picuncontrol.model.Features
import io.github.picuncontrol.model.ModelRepository
import io.github.picuncontrol.model.PicunModel
import io.github.picuncontrol.protocol.AdvertInfo
import io.github.picuncontrol.protocol.Commands
import io.github.picuncontrol.protocol.Commands.NoiseMode
import io.github.picuncontrol.protocol.Protocol.Family
import io.github.picuncontrol.protocol.Protocol.Reply
import io.github.picuncontrol.protocol.Replies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Last known values reported by the earbuds. All of these come from replies. */
data class DeviceValues(
  val battery: Replies.Battery? = null,
  val gameMode: Boolean? = null,
  val noise: Replies.Noise? = null,
  val eqEnabled: Boolean? = null,
  val eqGains: List<Int> = List(10) { 0 },
  val eqPresetId: Int? = null,
  val volume: Int? = null,
  val spatial: Boolean? = null,
  val english: Boolean? = null,
  val micMode: Int? = null,
  val volumeLimit: Int? = null,
  val leakMode: Int? = null,
  val lightMode: Int? = null,
  val heating: Replies.Heating? = null,
  val somatosensory: Boolean? = null,
  val headTracking: Boolean? = null,
  val touchControls: Int? = null,
  val wearDetection: Boolean? = null,
  val windproof: Boolean? = null,
  val ldac: Boolean? = null,
  val bassBoost: Boolean? = null,
  val sleep: Boolean? = null,
  val wallpaper: Int? = null,
  val adaptiveLevel: Int? = null,
  val playState: Boolean? = null,
  val deviceName: String? = null,
  val beeping: Boolean = false,
)

data class AppUiState(
  val connection: PicunConnection.State = PicunConnection.State.Idle,
  val scanning: Boolean = false,
  val scanDevices: List<ScanDevice> = emptyList(),
  val activeAddress: String? = null,
  val advertisedName: String = "",
  val advert: AdvertInfo? = null,
  val model: PicunModel? = null,
  val family: Family? = null,
  val features: Set<Feature> = emptySet(),
  val modelKnown: Boolean = true,
  val controlsEnabled: Boolean = true,
  val protocolDetected: Boolean = false,
  val probeFailed: Boolean = false,
  val values: DeviceValues = DeviceValues(),
  val eqDraft: List<Int> = List(10) { 0 },
  val eqDirty: Boolean = false,
  val customPresets: List<EqPreset> = emptyList(),
  val autoApplyGameMode: Boolean = false,
  val adaptiveDetecting: Boolean = false,
  val log: List<PicunConnection.LogEntry> = emptyList(),
  val showAdvanced: Boolean = false,
  val lastDevice: Pair<String, String>? = null,
  val repositoryLoaded: Boolean = false,
  val demo: Boolean = false,
) {
  val isReady: Boolean get() = connection.isReady
  val title: String
    get() = values.deviceName ?: model?.displayName ?: advertisedName.ifBlank { "Picun device" }
  val isFamilyB: Boolean get() = family == Family.B

  fun has(feature: Feature): Boolean = feature in features
}

/**
 * Owns the BLE session and turns replies into UI state. Nothing is shown as
 * "on" until the earbuds confirmed it, except local EQ editing and the
 * find-my-earbuds beep (which has no reply in the protocol).
 */
// The Compose screen requests and verifies BLUETOOTH_SCAN/BLUETOOTH_CONNECT before any
// transport call. The transport itself is also annotated and permission-checked at runtime.
@SuppressLint("MissingPermission")
class AppViewModel(app: Application) : AndroidViewModel(app) {
  private val prefs = Prefs(app)
  private val connection = PicunConnection(app)
  private var repository = ModelRepository.empty()

  private val _state = MutableStateFlow(
    AppUiState(
      lastDevice = prefs.lastDevice(),
      autoApplyGameMode = prefs.autoApplyGameMode,
    ),
  )
  val state: StateFlow<AppUiState> = _state.asStateFlow()

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  private var receivedReply = false
  private var sessionStarted = false
  private var probeJob: Job? = null
  private var adaptiveJob: Job? = null

  init {
    collectConnection()
    loadRepository()
  }

  private fun collectConnection() {
    viewModelScope.launch {
      connection.state.collect { connectionState ->
        _state.update { it.copy(connection = connectionState) }
        when (connectionState) {
          is PicunConnection.State.Ready -> onReady()
          is PicunConnection.State.Reconnecting -> {
            sessionStarted = false
            receivedReply = false
            probeJob?.cancel()
            adaptiveJob?.cancel()
            _state.update { it.copy(adaptiveDetecting = false) }
            _messages.tryEmit("Connection lost — reconnecting (${connectionState.attempt})…")
          }
          is PicunConnection.State.Failed -> {
            sessionStarted = false
            receivedReply = false
            probeJob?.cancel()
            adaptiveJob?.cancel()
            _state.update { it.copy(adaptiveDetecting = false) }
            _messages.tryEmit(connectionState.message)
          }
          is PicunConnection.State.Disconnected -> {
            sessionStarted = false
            receivedReply = false
            probeJob?.cancel()
            adaptiveJob?.cancel()
            _state.update { it.copy(adaptiveDetecting = false) }
          }
          else -> Unit
        }
      }
    }
    viewModelScope.launch {
      connection.replies.collect { reply -> handleReply(reply) }
    }
    viewModelScope.launch {
      connection.devices.collect { devices -> _state.update { it.copy(scanDevices = devices) } }
    }
    viewModelScope.launch {
      connection.scanning.collect { scanning -> _state.update { it.copy(scanning = scanning) } }
    }
    viewModelScope.launch {
      connection.log.collect { entry ->
        _state.update { it.copy(log = (listOf(entry) + it.log).take(MAX_LOG_LINES)) }
      }
    }
  }

  private fun loadRepository() {
    viewModelScope.launch {
      val repo = withContext(Dispatchers.IO) {
        runCatching {
          val text = getApplication<Application>().assets.open("models.json")
            .bufferedReader().use { it.readText() }
          ModelRepository.parse(text)
        }.getOrElse {
          _messages.tryEmit("Model database could not be loaded; continuing in unknown-model mode")
          ModelRepository.empty()
        }
      }
      repository = repo
      _state.update { it.copy(repositoryLoaded = true) }
      // If a device was selected while the catalogue was loading, resolve it now.
      val current = _state.value
      if (current.activeAddress != null && current.model == null) {
        val model = repository.findByProduct(current.advert?.productType, current.advert?.productNumber)
          ?: repository.findByBluetoothName(current.advertisedName)
        if (model != null) applyModel(model)
      }
    }
  }

  // ------------------------------------------------------------- scanning

  fun startScan() {
    runCatching { connection.startScan() }
      .onFailure { _messages.tryEmit("Scan failed: ${it.message ?: "permission denied"}") }
  }

  fun stopScan() = connection.stopScan()

  fun knownModel(device: ScanDevice): PicunModel? =
    repository.findByProduct(device.advert?.productType, device.advert?.productNumber)
      ?: repository.findByBluetoothName(device.name)

  fun selectDevice(device: ScanDevice) {
    val model = knownModel(device)
    val family = device.advert?.family ?: model?.family
    val controls = model != null || prefs.experimentalControls(device.address)
    receivedReply = false
    sessionStarted = false
    probeJob?.cancel()
    adaptiveJob?.cancel()

    _state.update {
      it.copy(
        activeAddress = device.address,
        advertisedName = device.name,
        advert = device.advert,
        model = model,
        modelKnown = model != null,
        family = family,
        features = if (model != null) {
          Features.filterForFamily(Features.forModel(model), family ?: Family.A)
        } else {
          Features.unknown(family)
        },
        controlsEnabled = controls,
        protocolDetected = family != null,
        probeFailed = false,
        values = DeviceValues(),
        eqDraft = List(10) { 0 },
        eqDirty = false,
        customPresets = prefs.customPresets(device.address),
        adaptiveDetecting = false,
        showAdvanced = false,
        log = emptyList(),
        demo = false,
      )
    }
    prefs.setLastDevice(device.address, device.name)
    val advert = device.advert
    when {
      advert != null -> prefs.setLastProduct(advert.productType, advert.productNumber)
      model != null -> prefs.setLastProduct(model.deviceModel, model.deviceNo)
      else -> prefs.clearLastProduct()
    }
    _state.update { it.copy(lastDevice = device.address to device.name) }
    runCatching { connection.connect(device.address) }
      .onFailure { _messages.tryEmit("Could not connect: ${it.message ?: "permission denied"}") }
  }

  fun connectLast() {
    val last = _state.value.lastDevice ?: return
    val product = prefs.lastProduct()
    val advert = product?.let { (type, number) ->
      AdvertInfo(
        family = if (type == "01") Family.B else Family.A,
        productType = type,
        productNumber = number,
      )
    }
    selectDevice(ScanDevice(last.second, last.first, 0, advert, false))
  }

  fun reconnect() {
    if (_state.value.demo) {
      _messages.tryEmit("Demo mode — no real device")
      return
    }
    val address = _state.value.activeAddress ?: return
    receivedReply = false
    sessionStarted = false
    _state.update { it.copy(probeFailed = false) }
    runCatching { connection.connect(address) }
      .onFailure { _messages.tryEmit("Could not reconnect: ${it.message ?: "permission denied"}") }
  }

  fun disconnect() {
    probeJob?.cancel()
    adaptiveJob?.cancel()
    connection.disconnect()
    sessionStarted = false
    _state.update {
      it.copy(
        activeAddress = null,
        advert = null,
        model = null,
        family = null,
        features = emptySet(),
        values = DeviceValues(),
        eqDraft = List(10) { 0 },
        eqDirty = false,
        protocolDetected = false,
        probeFailed = false,
        adaptiveDetecting = false,
        showAdvanced = false,
        demo = false,
      )
    }
  }

  fun setAdvanced(open: Boolean) {
    _state.update { it.copy(showAdvanced = open) }
  }

  // ------------------------------------------------------------- session

  private fun onReady() {
    val st = _state.value
    if (st.activeAddress == null) return
    if (probeJob?.isActive == true) return
    if (st.family == null) {
      probeProtocol()
    } else {
      beginSession()
    }
  }

  private fun probeProtocol() {
    probeJob?.cancel()
    _state.update { it.copy(probeFailed = false) }
    _messages.tryEmit("Detecting protocol…")
    connection.send(Commands.queryGameMode(Family.A))
    probeJob = viewModelScope.launch {
      delay(1_200)
      if (_state.value.family == null && _state.value.isReady) {
        connection.send(Commands.queryGameMode(Family.B))
        delay(1_400)
        if (_state.value.family == null) {
          _state.update { it.copy(probeFailed = true) }
          _messages.tryEmit("Could not identify the protocol. You can retry detection.")
        }
      }
    }
  }

  fun retryDetection() {
    if (!_state.value.isReady) {
      _messages.tryEmit("Not connected")
      return
    }
    probeProtocol()
  }

  private fun beginSession() {
    if (sessionStarted) return
    sessionStarted = true
    queryAll()
    if (prefs.autoApplyGameMode && _state.value.has(Feature.GAME_MODE)) {
      viewModelScope.launch {
        delay(800)
        if (_state.value.isReady && _state.value.controlsEnabled) {
          val family = _state.value.family ?: return@launch
          connection.send(Commands.setGameMode(family, true))
          delay(400)
          connection.send(Commands.queryGameMode(family))
        }
      }
    }
  }

  private fun queryAll() {
    val st = _state.value
    val family = st.family ?: return
    val queries = mutableListOf<ByteArray>()
    if (st.has(Feature.BATTERY)) queries += Commands.queryBattery(family)
    if (st.has(Feature.VOLUME)) queries += Commands.queryVolume(family)
    if (st.has(Feature.GAME_MODE)) queries += Commands.queryGameMode(family)
    if (st.has(Feature.ANC)) queries += Commands.queryNoise(family)
    if (st.has(Feature.EQ)) queries += Commands.queryEq(family)
    if (st.has(Feature.SPATIAL)) queries += Commands.querySpatial(family)
    if (st.has(Feature.LANGUAGE)) queries += Commands.queryLanguage(family)
    if (st.has(Feature.MIC_MODE)) Commands.queryMicMode(family)?.let(queries::add)
    if (st.has(Feature.VOLUME_LIMIT)) Commands.queryVolumeLimit(family)?.let(queries::add)
    if (st.has(Feature.LEAK_MODE)) Commands.queryLeakMode(family)?.let(queries::add)
    if (st.has(Feature.LIGHT)) Commands.queryLight(family)?.let(queries::add)
    if (st.has(Feature.HEATING)) Commands.queryHeating(family)?.let(queries::add)
    if (st.has(Feature.SOMATOSENSORY)) Commands.querySomatosensory(family)?.let(queries::add)
    if (st.has(Feature.HEAD_TRACKING)) Commands.queryHeadTracking(family)?.let(queries::add)
    if (st.has(Feature.TOUCH)) Commands.queryTouchControls(family)?.let(queries::add)
    if (st.has(Feature.WEAR)) Commands.queryWearDetection(family)?.let(queries::add)
    if (st.has(Feature.WINDPROOF)) Commands.queryWindproof(family)?.let(queries::add)
    if (st.has(Feature.LDAC)) Commands.queryLdac(family)?.let(queries::add)
    if (st.has(Feature.BASS_BOOST)) Commands.queryBassBoost(family)?.let(queries::add)
    if (st.has(Feature.SLEEP)) Commands.querySleep(family)?.let(queries::add)
    if (st.has(Feature.WALLPAPER)) Commands.queryWallpaper(family)?.let(queries::add)
    if (st.has(Feature.PLAY_CONTROLS)) queries += Commands.queryPlayState(family)
    queries += Commands.queryName(family)
    queries.forEach(connection::send)
  }

  fun refresh() {
    if (_state.value.demo) {
      _messages.tryEmit("Demo mode — no real device")
      return
    }
    if (!_state.value.isReady) {
      _messages.tryEmit("Not connected")
      return
    }
    queryAll()
  }

  // ------------------------------------------------------------- replies

  private fun handleReply(reply: Reply) {
    val before = _state.value
    if (!receivedReply) {
      receivedReply = true
      probeJob?.cancel()
      if (before.family != reply.family) {
        adoptFamily(reply.family)
      }
      _state.update { it.copy(protocolDetected = true, probeFailed = false) }
      if (!sessionStarted) beginSession()
      else if (before.family != reply.family) queryAll()
    }

    when (reply.family) {
      Family.A -> handleFamilyA(reply)
      Family.B -> handleFamilyB(reply)
    }
  }

  private fun adoptFamily(family: Family) {
    val st = _state.value
    val features = st.model?.let {
      Features.filterForFamily(Features.forModel(it), family)
    } ?: Features.unknown(family)
    _state.update {
      it.copy(family = family, features = features, protocolDetected = true)
    }
    _messages.tryEmit("Protocol: Family ${family.name}")
  }

  private fun handleFamilyA(reply: Reply) {
    when (reply.command) {
      "74" -> applyBattery(Replies.battery(reply))
      "78" -> Replies.onOff(reply)?.let { on -> update { it.copy(gameMode = on) } }
      "76" -> applyNoise(Replies.noise(reply))
      "77" -> applyEq(reply)
      "75" -> Replies.volume(reply)?.let { v -> update { it.copy(volume = v) } }
      "79" -> Replies.onOff(reply)?.let { on -> update { it.copy(spatial = on) } }
      "7C" -> Replies.english(reply)?.let { en -> update { it.copy(english = en) } }
      "7A" -> Replies.micMode(reply)?.let { v -> update { it.copy(micMode = v) } }
      "81" -> Replies.volumeLimit(reply)?.let { v -> update { it.copy(volumeLimit = v) } }
      "90" -> Replies.leakMode(reply)?.let { v -> update { it.copy(leakMode = v) } }
      "92" -> Replies.lightMode(reply)?.let { v -> update { it.copy(lightMode = v) } }
      "93" -> Replies.heating(reply)?.let { v -> update { it.copy(heating = v) } }
      "83" -> Replies.onOff(reply)?.let { on -> update { it.copy(somatosensory = on) } }
      "7D" -> Replies.onOff(reply)?.let { on -> update { it.copy(headTracking = on) } }
      "98" -> Replies.touchControls(reply)?.let { v -> update { it.copy(touchControls = v) } }
      "99" -> Replies.onOff(reply)?.let { on -> update { it.copy(wearDetection = on) } }
      "9A" -> Replies.onOff(reply)?.let { on -> update { it.copy(windproof = on) } }
      "9B" -> Replies.onOff(reply)?.let { on -> update { it.copy(ldac = on) } }
      "97" -> Replies.onOff(reply)?.let { on -> update { it.copy(bassBoost = on) } }
      "91" -> Replies.onOff(reply)?.let { on -> update { it.copy(sleep = on) } }
      "95" -> if (reply.size >= 2 && reply.u8(0) != 0) update { it.copy(wallpaper = reply.u8(1)) }
      "80" -> Replies.adaptiveLevel(reply)?.let { level ->
        update { it.copy(adaptiveLevel = level) }
        if (level == 2) {
          // The official app shows "detecting" for 10 s, then re-queries the mode.
          startAdaptiveWatch()
        }
      }
      "72" -> Replies.playState(reply)?.let { playing -> update { it.copy(playState = playing) } }
      "71" -> Replies.name(reply)?.let { name ->
        val address = _state.value.activeAddress
        if (address != null) prefs.setLastDevice(address, name)
        _state.update {
          it.copy(
            values = it.values.copy(deviceName = name),
            lastDevice = address?.let { addr -> addr to name } ?: it.lastDevice,
          )
        }
      }
    }
  }

  private fun handleFamilyB(reply: Reply) {
    when (reply.command) {
      "010501" -> applyBattery(Replies.battery(reply))
      "010B01" -> Replies.onOff(reply)?.let { on -> update { it.copy(gameMode = on) } }
      "040101" -> applyNoise(Replies.noise(reply))
      "030102" -> applyEq(reply)
      "030208" -> Replies.volume(reply)?.let { v -> update { it.copy(volume = v) } }
      "030101" -> Replies.onOff(reply)?.let { on -> update { it.copy(spatial = on) } }
      "010C01" -> Replies.english(reply)?.let { en -> update { it.copy(english = en) } }
      "030209" -> Replies.playState(reply)?.let { playing -> update { it.copy(playState = playing) } }
      "010101" -> Replies.name(reply)?.let { name ->
        val address = _state.value.activeAddress
        if (address != null) prefs.setLastDevice(address, name)
        _state.update {
          it.copy(
            values = it.values.copy(deviceName = name),
            lastDevice = address?.let { addr -> addr to name } ?: it.lastDevice,
          )
        }
      }
    }
  }

  private inline fun update(transform: (DeviceValues) -> DeviceValues) {
    _state.update { it.copy(values = transform(it.values)) }
  }

  private fun applyBattery(battery: Replies.Battery?) {
    if (battery != null) update { it.copy(battery = battery) }
  }

  private fun applyNoise(noise: Replies.Noise?) {
    if (noise == null) return
    update { it.copy(noise = noise) }
  }

  private fun applyEq(reply: Reply) {
    val eq = Replies.eq(reply, _state.value.model?.isDevicePresetModel == true) ?: return
    _state.update { st ->
      st.copy(
        values = st.values.copy(
          eqEnabled = eq.enabled,
          eqGains = eq.gains,
          eqPresetId = eq.presetId,
        ),
        eqDraft = if (st.eqDirty) st.eqDraft else eq.gains,
      )
    }
  }

  private fun startAdaptiveWatch() {
    adaptiveJob?.cancel()
    _state.update { it.copy(adaptiveDetecting = true) }
    adaptiveJob = viewModelScope.launch {
      delay(10_000)
      _state.update { it.copy(adaptiveDetecting = false) }
      val family = _state.value.family ?: return@launch
      if (_state.value.isReady) connection.send(Commands.queryNoise(family))
    }
  }

  // ------------------------------------------------------------- commands

  private fun familyForWrite(): Family? {
    val st = _state.value
    if (st.demo) {
      _messages.tryEmit("Demo mode — no real device")
      return null
    }
    if (!st.isReady) {
      _messages.tryEmit("Not connected")
      return null
    }
    if (!st.controlsEnabled) {
      _messages.tryEmit("Controls are locked for unknown models. Enable experimental controls below.")
      return null
    }
    val family = st.family
    if (family == null) {
      _messages.tryEmit("Protocol not detected yet")
      return null
    }
    return family
  }

  private fun sendCommand(cmd: ByteArray?, refresh: ByteArray? = null, refreshDelay: Long = 400) {
    if (cmd == null) {
      _messages.tryEmit("This device does not support that control")
      return
    }
    connection.send(cmd)
    if (refresh != null) {
      viewModelScope.launch {
        delay(refreshDelay)
        if (_state.value.isReady) connection.send(refresh)
      }
    }
  }

  fun setGameMode(on: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setGameMode(family, on), Commands.queryGameMode(family))
  }

  fun setNoiseMode(mode: NoiseMode) {
    val family = familyForWrite() ?: return
    val current = _state.value.values.noise
    val level = when (mode) {
      NoiseMode.OFF -> 0
      NoiseMode.ANC ->
        if (current?.mode == NoiseMode.ANC) current.level.takeIf { it in 1..16 } ?: 14 else 14
      NoiseMode.TRANSPARENCY ->
        if (current?.mode == NoiseMode.TRANSPARENCY) {
          current.level.takeIf { it in listOf(2, 8, 14) } ?: 8
        } else {
          8
        }
    }
    val scene = if (family == Family.A && mode == NoiseMode.ANC) (current?.scene ?: 0) else 0
    sendCommand(Commands.setNoise(family, mode, level, scene), Commands.queryNoise(family))
  }

  fun setAncLevel(level: Int) {
    val family = familyForWrite() ?: return
    val safe = level.coerceIn(1, 16)
    val scene = if (family == Family.A) (_state.value.values.noise?.scene ?: 0) else 0
    sendCommand(Commands.setNoise(family, NoiseMode.ANC, safe, scene), Commands.queryNoise(family))
  }

  fun setTransparencyLevel(level: Int) {
    val family = familyForWrite() ?: return
    val safe = level.coerceIn(1, 16)
    sendCommand(Commands.setNoise(family, NoiseMode.TRANSPARENCY, safe, 0), Commands.queryNoise(family))
  }

  fun startAdaptiveNoise() {
    val family = familyForWrite() ?: return
    connection.send(Commands.queryAdaptiveNoise(family))
    startAdaptiveWatch()
  }

  fun setEqEnabled(on: Boolean) {
    val family = familyForWrite() ?: return
    val st = _state.value
    _state.update { it.copy(eqDirty = false) }
    // The official app disables EQ by sending flag 0 with all-zero gains.
    val gains = if (on) st.eqDraft.toIntArray() else IntArray(10)
    sendCommand(
      Commands.setEq(family, on, gains),
      Commands.queryEq(family),
    )
  }

  fun updateEqBand(index: Int, gain: Int) {
    if (index !in 0..9) return
    _state.update {
      val next = it.eqDraft.toMutableList()
      next[index] = gain.coerceIn(-6, 6)
      it.copy(eqDraft = next, eqDirty = true)
    }
  }

  fun applyEq() {
    val family = familyForWrite() ?: return
    val st = _state.value
    _state.update { it.copy(eqDirty = false) }
    sendCommand(
      Commands.setEq(family, st.values.eqEnabled ?: true, st.eqDraft.toIntArray()),
      Commands.queryEq(family),
    )
  }

  fun applyEqPreset(preset: EqPreset) {
    val family = familyForWrite() ?: return
    val gains = preset.normalized().gains
    _state.update { it.copy(eqDraft = gains, eqDirty = false) }
    sendCommand(
      Commands.setEq(family, true, gains.toIntArray()),
      Commands.queryEq(family),
    )
  }

  fun saveCustomPreset(name: String) {
    val address = _state.value.activeAddress ?: return
    val clean = name.trim()
    if (clean.isEmpty()) {
      _messages.tryEmit("Preset name is empty")
      return
    }
    val preset = EqPreset(clean.take(24), _state.value.eqDraft).normalized()
    prefs.saveCustomPreset(address, preset)
    _state.update { it.copy(customPresets = prefs.customPresets(address)) }
    _messages.tryEmit("Saved preset \"${preset.name}\"")
  }

  fun deleteCustomPreset(name: String) {
    val address = _state.value.activeAddress ?: return
    prefs.deleteCustomPreset(address, name)
    _state.update { it.copy(customPresets = prefs.customPresets(address)) }
  }

  fun setVolume(value: Int) {
    val family = familyForWrite() ?: return
    sendCommand(
      Commands.setVolume(family, value),
      Commands.queryVolume(family),
      refreshDelay = 300,
    )
  }

  fun setSpatial(on: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setSpatial(family, on), Commands.querySpatial(family))
  }

  fun setEnglish(english: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setEnglish(family, english), Commands.queryLanguage(family))
  }

  fun setMicMode(mode: Int) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setMicMode(family, mode), Commands.queryMicMode(family))
  }

  fun setVolumeLimit(value: Int) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setVolumeLimit(family, value), Commands.queryVolumeLimit(family))
  }

  fun setLeakMode(mode: Int) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setLeakMode(family, mode), Commands.queryLeakMode(family))
  }

  fun setLightMode(mode: Int) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setLight(family, mode), Commands.queryLight(family))
  }

  fun setHeating(on: Boolean, level: Int = _state.value.values.heating?.level ?: 1) {
    val family = familyForWrite() ?: return
    sendCommand(
      Commands.setHeating(family, on, level.coerceIn(1, 3)),
      Commands.queryHeating(family),
    )
  }

  fun setSomatosensory(on: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setSomatosensory(family, on), Commands.querySomatosensory(family))
  }

  fun setHeadTracking(on: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setHeadTracking(family, on), Commands.queryHeadTracking(family))
  }

  fun resetHeadTracking() {
    val family = familyForWrite() ?: return
    sendCommand(Commands.resetHeadTracking(family), Commands.queryHeadTracking(family))
  }

  fun setTouchControls(mode: Int) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setTouchControls(family, mode), Commands.queryTouchControls(family))
  }

  fun setWearDetection(on: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setWearDetection(family, on), Commands.queryWearDetection(family))
  }

  fun setWindproof(on: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setWindproof(family, on), Commands.queryWindproof(family))
  }

  fun setLdac(on: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setLdac(family, on), Commands.queryLdac(family))
  }

  fun setBassBoost(on: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setBassBoost(family, on), Commands.queryBassBoost(family))
  }

  fun setSleep(on: Boolean) {
    val family = familyForWrite() ?: return
    sendCommand(Commands.setSleep(family, on), Commands.querySleep(family))
  }

  fun beep(on: Boolean) {
    val family = familyForWrite() ?: return
    connection.send(Commands.beep(family, on))
    update { it.copy(beeping = on) }
  }

  fun playPause() {
    val family = familyForWrite() ?: return
    val playing = _state.value.values.playState
    connection.send(if (playing == true) Commands.pause(family) else Commands.play(family))
    viewModelScope.launch {
      delay(500)
      if (_state.value.isReady) connection.send(Commands.queryPlayState(family))
    }
  }

  fun nextTrack() {
    val family = familyForWrite() ?: return
    connection.send(Commands.next(family))
  }

  fun previousTrack() {
    val family = familyForWrite() ?: return
    connection.send(Commands.previous(family))
  }

  fun rename(name: String) {
    val family = familyForWrite() ?: return
    val clean = name.trim()
    if (clean.length !in 1..15) {
      _messages.tryEmit("Name must be 1–15 characters")
      return
    }
    if (clean.any { it.code !in 0x20..0x7E }) {
      _messages.tryEmit("Use plain ASCII characters for the Bluetooth name")
      return
    }
    connection.send(Commands.rename(family, clean))
    _messages.tryEmit("Rename sent")
    viewModelScope.launch {
      delay(600)
      if (_state.value.isReady) connection.send(Commands.queryName(family))
    }
  }

  fun factoryReset() {
    val family = familyForWrite() ?: return
    connection.send(Commands.factoryReset(family))
    _messages.tryEmit("Factory reset command sent")
  }

  fun clearPairing() {
    val family = familyForWrite() ?: return
    connection.send(Commands.clearPairing(family))
    _messages.tryEmit("Pairing-clear command sent")
  }

  fun setAutoApplyGameMode(enabled: Boolean) {
    prefs.autoApplyGameMode = enabled
    _state.update { it.copy(autoApplyGameMode = enabled) }
    if (enabled) {
      _messages.tryEmit("Low latency will be switched on after every connect")
    }
  }

  /** Read-only diagnostics: sends a query even for features the catalogue hides. */
  fun experimentalQuery(cmd: ByteArray?) {
    if (!_state.value.isReady) {
      _messages.tryEmit("Not connected")
      return
    }
    if (cmd == null) {
      _messages.tryEmit("This query has no Family B variant")
      return
    }
    connection.send(cmd)
  }

  fun clearLog() {
    _state.update { it.copy(log = emptyList()) }
  }

  fun enableExperimentalControls() {
    val address = _state.value.activeAddress ?: return
    prefs.setExperimentalControls(address, true)
    val family = _state.value.family
    _state.update {
      it.copy(
        controlsEnabled = true,
        features = Features.unknown(family),
      )
    }
    _messages.tryEmit("Experimental controls enabled for this device")
  }

  /**
   * Debug-only UI preview (`adb shell am start … --ez demo true`).
   * It is compiled into debug builds only; release callers are guarded by
   * BuildConfig.DEBUG. No BLE traffic is sent.
   */
  fun showDemo() {
    viewModelScope.launch {
      var tries = 0
      while (!_state.value.repositoryLoaded && tries < 30) {
        delay(100)
        tries++
      }
      val model = repository.findById(132) ?: repository.models.firstOrNull()
      val gains = listOf(4, 3, 1, 0, -1, 0, 1, 2, 3, 4)
      val values = DeviceValues(
        battery = Replies.Battery(82, 76),
        gameMode = true,
        noise = Replies.Noise(NoiseMode.ANC, scene = 0, level = 14),
        eqEnabled = true,
        eqGains = gains,
        volume = 65,
        spatial = true,
        english = true,
        micMode = 0,
        volumeLimit = 16,
        leakMode = 1,
        lightMode = 2,
        heating = Replies.Heating(on = true, level = 2),
        somatosensory = false,
        headTracking = false,
        touchControls = 2,
        wearDetection = true,
        windproof = false,
        ldac = true,
        bassBoost = true,
        sleep = false,
        wallpaper = 2,
        adaptiveLevel = 2,
        playState = true,
        deviceName = "picun F8 Ultra",
      )
      _state.update {
        it.copy(
          connection = PicunConnection.State.Ready,
          activeAddress = "DEMO",
          advertisedName = "picun F8 Ultra",
          advert = AdvertInfo(Family.A, "0001", "0083"),
          model = model,
          family = Family.A,
          features = Feature.entries.toSet(),
          modelKnown = model != null,
          controlsEnabled = true,
          protocolDetected = true,
          probeFailed = false,
          values = values,
          eqDraft = gains,
          eqDirty = false,
          customPresets = listOf(EqPreset("My bass", listOf(6, 5, 3, 1, 0, -1, 0, 1, 2, 2))),
          autoApplyGameMode = false,
          adaptiveDetecting = false,
          log = listOf(
            PicunConnection.LogEntry(PicunConnection.LogDirection.RX, "FCFA04740150014B2F"),
            PicunConnection.LogEntry(PicunConnection.LogDirection.TX, "FCFA00741D"),
            PicunConnection.LogEntry(PicunConnection.LogDirection.INFO, "", "Ready"),
          ),
          showAdvanced = false,
          demo = true,
        )
      }
    }
  }

  private fun applyModel(model: PicunModel) {
    val st = _state.value
    val family = st.family ?: model.family
    _state.update {
      it.copy(
        model = model,
        modelKnown = true,
        family = family,
        features = Features.filterForFamily(Features.forModel(model), family),
        controlsEnabled = true,
      )
    }
  }

  override fun onCleared() {
    connection.close()
    super.onCleared()
  }

  private companion object {
    const val MAX_LOG_LINES = 300
  }
}

/** UI helper: 10 band labels from the official app. */
val EqFrequencies = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
