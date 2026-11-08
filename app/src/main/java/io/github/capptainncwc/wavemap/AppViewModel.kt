package io.github.capptainncwc.wavemap

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.capptainncwc.wavemap.speed.SpeedPhase
import io.github.capptainncwc.wavemap.speed.SpeedState
import io.github.capptainncwc.wavemap.speed.SpeedTest
import io.github.capptainncwc.wavemap.survey.Exporter
import io.github.capptainncwc.wavemap.survey.Metric
import io.github.capptainncwc.wavemap.survey.Project
import io.github.capptainncwc.wavemap.survey.ProjectRepository
import io.github.capptainncwc.wavemap.survey.Reading
import io.github.capptainncwc.wavemap.survey.SurveyPoint
import io.github.capptainncwc.wavemap.survey.Target
import io.github.capptainncwc.wavemap.survey.VirtualAp
import io.github.capptainncwc.wavemap.wifi.ConnectionInfo
import io.github.capptainncwc.wavemap.wifi.WifiScanner
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceStatus(val wifiEnabled: Boolean = true, val locationEnabled: Boolean = true)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val scanner = WifiScanner(app)
    val repo = ProjectRepository(app)
    private val exporter = Exporter(app, repo)

    val networks = scanner.networks
    val history = scanner.history
    val throttled = scanner.throttled

    private val _scanning = MutableStateFlow(true)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _intervalSec = MutableStateFlow(5)
    val intervalSec: StateFlow<Int> = _intervalSec.asStateFlow()

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionInfo?>(null)
    val connection: StateFlow<ConnectionInfo?> = _connection.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var permitted = false
    private var foreground = true
    private var scanJob: Job? = null

    fun onPermissionGranted() {
        if (permitted) return
        permitted = true
        scanner.start()
        restartLoop()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        restartLoop()
    }

    fun toggleScanning() {
        _scanning.value = !_scanning.value
        restartLoop()
    }

    fun setInterval(seconds: Int) {
        _intervalSec.value = seconds
        restartLoop()
    }

    fun refreshConnection() {
        viewModelScope.launch(Dispatchers.IO) { _connection.value = runCatching { scanner.connectionInfo() }.getOrNull() }
    }

    private fun restartLoop() {
        scanJob?.cancel()
        if (!permitted || !foreground) return
        scanJob = viewModelScope.launch {
            while (isActive) {
                _status.value = DeviceStatus(scanner.isWifiEnabled, scanner.isLocationEnabled)
                if (_scanning.value) scanner.requestScan() else scanner.publish(addToHistory = false)
                _connection.value = withContext(Dispatchers.IO) { runCatching { scanner.connectionInfo() }.getOrNull() }
                delay(_intervalSec.value * 1000L)
            }
        }
    }

    override fun onCleared() {
        scanner.stop()
    }

    // ---------------------------------------------------------------- survey projects

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _measuring = MutableStateFlow(false)
    val measuring: StateFlow<Boolean> = _measuring.asStateFlow()

    init {
        refreshProjects()
    }

    fun refreshProjects() {
        viewModelScope.launch(Dispatchers.IO) { _projects.value = repo.list() }
    }

    fun createProject(name: String, image: Uri?, widthMeters: Float, heightMeters: Float, error: String) {
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) {
                runCatching {
                    if (image != null) repo.createFromImage(name, image, widthMeters)
                    else repo.createBlank(name, widthMeters, heightMeters)
                }
            }
            created.onSuccess {
                refreshProjects()
                _project.value = it
            }.onFailure { _messages.tryEmit("$error: ${it.message}") }
        }
    }

    fun openProject(project: Project) {
        _project.value = repo.load(project.id) ?: project
    }

    fun closeProject() {
        _project.value = null
        refreshProjects()
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(project.id)
            _projects.value = repo.list()
        }
    }

    private fun update(transform: (Project) -> Project) {
        val current = _project.value ?: return
        val next = transform(current)
        _project.value = next
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.save(next) }.onFailure { _messages.tryEmit(it.message ?: "Save failed") }
        }
    }

    fun renameProject(name: String, widthMeters: Float) = update { it.copy(name = name, widthMeters = widthMeters) }

    /** Scans at the spot the user tapped and stores the readings as a survey point. */
    fun measureAt(x: Float, y: Float, throttledMessage: String) {
        if (_measuring.value) return
        _measuring.value = true
        viewModelScope.launch {
            try {
                val outcome = scanner.scanAndAwait()
                if (!outcome.fresh) _messages.tryEmit(throttledMessage)
                val readings = outcome.networks.map { Reading(it.bssid, it.ssid, it.rssi, it.frequency, it.widthMhz) }
                val point = SurveyPoint(UUID.randomUUID().toString(), x, y, System.currentTimeMillis(), readings, outcome.fresh)
                update { it.copy(points = it.points + point) }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _measuring.value = false
            }
        }
    }

    fun deletePoint(id: String) = update { p -> p.copy(points = p.points.filterNot { it.id == id }) }

    fun undoPoint() = update { p -> p.copy(points = p.points.dropLast(1)) }

    fun addAp(x: Float, y: Float) = update { p ->
        p.copy(aps = p.aps + VirtualAp(UUID.randomUUID().toString(), x, y, "AP ${p.aps.size + 1}"))
    }

    fun updateAp(ap: VirtualAp) = update { p -> p.copy(aps = p.aps.map { if (it.id == ap.id) ap else it }) }

    fun deleteAp(id: String) = update { p -> p.copy(aps = p.aps.filterNot { it.id == id }) }

    fun exportPng(metric: Metric, target: Target, title: String, chooser: String) {
        val p = _project.value ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.Default) { exporter.heatmapPng(p, metric, target, title) } }
                .onSuccess { exporter.share(it, "image/png", chooser) }
                .onFailure { _messages.tryEmit(it.message ?: "Export failed") }
        }
    }

    fun exportCsv(chooser: String) {
        val p = _project.value ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { exporter.csv(p) } }
                .onSuccess { exporter.share(it, "text/csv", chooser) }
                .onFailure { _messages.tryEmit(it.message ?: "Export failed") }
        }
    }

    // ---------------------------------------------------------------- speed test

    private val _speed = MutableStateFlow(SpeedState())
    val speed: StateFlow<SpeedState> = _speed.asStateFlow()
    private var speedJob: Job? = null

    fun startSpeedTest() {
        speedJob?.cancel()
        speedJob = viewModelScope.launch {
            try {
                SpeedTest().run { _speed.value = it }
            } catch (e: CancellationException) {
                _speed.value = _speed.value.copy(phase = SpeedPhase.IDLE)
                throw e
            } catch (e: Exception) {
                _speed.value = _speed.value.copy(phase = SpeedPhase.ERROR, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun stopSpeedTest() {
        speedJob?.cancel()
    }
}
