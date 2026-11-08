package io.github.capptainncwc.wavemap.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.net.Inet4Address
import java.net.Inet6Address
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

data class ConnectionInfo(
    val wifi: Boolean,
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    val frequency: Int?,
    val linkSpeedMbps: Int?,
    val rxSpeedMbps: Int?,
    val txSpeedMbps: Int?,
    val standard: String?,
    val ipv4: String?,
    val ipv6: List<String>,
    val gateway: String?,
    val dns: List<String>,
)

class WifiScanner(context: Context) {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(WifiManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val location = app.getSystemService(LocationManager::class.java)

    private val _networks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val networks: StateFlow<List<WifiNetwork>> = _networks.asStateFlow()

    private val _history = MutableStateFlow<Map<String, List<SignalSample>>>(emptyMap())
    /** RSSI history per BSSID, newest last. */
    val history: StateFlow<Map<String, List<SignalSample>>> = _history.asStateFlow()

    private val _throttled = MutableStateFlow(false)
    /** True when the last scan request was refused (Android allows 4 scans per 2 minutes). */
    val throttled: StateFlow<Boolean> = _throttled.asStateFlow()

    private val _lastFreshScan = MutableStateFlow(0L)
    val lastFreshScan: StateFlow<Long> = _lastFreshScan.asStateFlow()

    private val fresh = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            publish(addToHistory = updated)
            if (updated) {
                _lastFreshScan.value = System.currentTimeMillis()
                fresh.tryEmit(Unit)
            }
        }
    }

    val isWifiEnabled: Boolean get() = wifi.isWifiEnabled
    val isLocationEnabled: Boolean get() = LocationManagerCompat.isLocationEnabled(location)

    fun start() {
        if (registered) return
        ContextCompat.registerReceiver(
            app, receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        publish(addToHistory = true)
    }

    fun stop() {
        if (!registered) return
        runCatching { app.unregisterReceiver(receiver) }
        registered = false
    }

    /** Asks the system for a new scan. Returns false when the request was throttled. */
    fun requestScan(): Boolean {
        @Suppress("DEPRECATION")
        val ok = try {
            wifi.startScan()
        } catch (_: SecurityException) {
            false
        }
        _throttled.value = !ok
        if (!ok) publish(addToHistory = false)
        return ok
    }

    /** Starts a scan and waits for fresh results. Falls back to cached results when throttled. */
    suspend fun scanAndAwait(timeoutMs: Long = 10_000): ScanOutcome = coroutineScope {
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(timeoutMs) { fresh.first() }
        }
        val isFresh = if (requestScan()) waiter.await() != null else {
            waiter.cancel()
            // Refused, but results from a scan that finished moments ago are still current.
            System.currentTimeMillis() - _lastFreshScan.value < RECENT_SCAN_MS
        }
        ScanOutcome(networks.value, isFresh)
    }

    data class ScanOutcome(val networks: List<WifiNetwork>, val fresh: Boolean)

    @SuppressLint("MissingPermission")
    fun publish(addToHistory: Boolean) {
        val results = try {
            wifi.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        val now = System.currentTimeMillis()
        val connected = connectedBssid()
        val list = results.map { it.toNetwork(connected, now) }.sortedByDescending { it.rssi }
        _networks.value = list
        if (!addToHistory) return
        val h = _history.value.toMutableMap()
        for (n in list) {
            h[n.bssid] = (h[n.bssid].orEmpty() + SignalSample(now, n.rssi)).takeLast(MAX_SAMPLES)
        }
        h.entries.removeAll { now - (it.value.lastOrNull()?.time ?: 0L) > HISTORY_TTL_MS }
        _history.value = h
    }

    private fun connectedBssid(): String? {
        @Suppress("DEPRECATION")
        return wifi.connectionInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
    }

    @SuppressLint("MissingPermission")
    fun connectionInfo(): ConnectionInfo {
        val net = connectivity.activeNetwork
        val caps = net?.let { connectivity.getNetworkCapabilities(it) }
        val props = net?.let { connectivity.getLinkProperties(it) }
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        @Suppress("DEPRECATION")
        val info = if (onWifi) wifi.connectionInfo else null
        val ssid = info?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
        val addresses = props?.linkAddresses.orEmpty().map { it.address }
        val gateway = props?.routes.orEmpty()
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        val freq = info?.frequency?.takeIf { it > 0 }
        return ConnectionInfo(
            wifi = onWifi,
            ssid = ssid,
            bssid = info?.bssid?.takeIf { it != "02:00:00:00:00:00" },
            rssi = info?.rssi?.takeIf { it > -127 },
            frequency = freq,
            linkSpeedMbps = info?.linkSpeed?.takeIf { it > 0 },
            rxSpeedMbps = if (Build.VERSION.SDK_INT >= 29) info?.rxLinkSpeedMbps?.takeIf { it > 0 } else null,
            txSpeedMbps = if (Build.VERSION.SDK_INT >= 29) info?.txLinkSpeedMbps?.takeIf { it > 0 } else null,
            standard = if (Build.VERSION.SDK_INT >= 30 && info != null && freq != null) {
                WifiMath.standard(info.wifiStandard, WifiMath.band(freq))
            } else null,
            ipv4 = addresses.firstOrNull { it is Inet4Address }?.hostAddress,
            ipv6 = addresses.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress?.substringBefore('%') },
            gateway = gateway,
            dns = props?.dnsServers.orEmpty().mapNotNull { it.hostAddress },
        )
    }

    companion object {
        private const val MAX_SAMPLES = 400
        private const val RECENT_SCAN_MS = 8_000L
        private const val HISTORY_TTL_MS = 15 * 60_000L
    }
}
