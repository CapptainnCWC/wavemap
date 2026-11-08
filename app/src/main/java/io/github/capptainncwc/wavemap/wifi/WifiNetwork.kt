package io.github.capptainncwc.wavemap.wifi

import android.net.wifi.ScanResult
import android.os.Build
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

enum class Band(val label: String) {
    GHZ_2_4("2.4 GHz"),
    GHZ_5("5 GHz"),
    GHZ_6("6 GHz"),
    GHZ_60("60 GHz"),
    UNKNOWN("?"),
}

data class WifiNetwork(
    val bssid: String,
    /** Empty for hidden networks. */
    val ssid: String,
    val rssi: Int,
    val frequency: Int,
    /** Centre of the whole (possibly bonded) channel, MHz. */
    val centerFrequency: Int,
    val channel: Int,
    val widthMhz: Int,
    val band: Band,
    val security: String,
    val standard: String?,
    val capabilities: String,
    val connected: Boolean,
    val lastSeen: Long,
) {
    val quality: Int get() = WifiMath.quality(rssi)
    val distanceMeters: Double get() = WifiMath.distanceMeters(rssi, frequency)
}

data class SignalSample(val time: Long, val rssi: Int)

object WifiMath {
    /** Noise floor assumed when the platform does not report noise (Android never does). */
    const val NOISE_FLOOR_DBM = -95

    fun channel(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2407) / 5
        freq in 4910..4980 -> (freq - 4000) / 5
        freq in 5150..5895 -> (freq - 5000) / 5
        freq == 5935 -> 2
        freq in 5950..7125 -> (freq - 5950) / 5
        freq in 58320..70200 -> (freq - 56160) / 2160
        else -> 0
    }

    fun band(freq: Int): Band = when (freq) {
        in 2400..2500 -> Band.GHZ_2_4
        in 4900..5900 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        in 57000..71000 -> Band.GHZ_60
        else -> Band.UNKNOWN
    }

    /** Maps ScanResult.CHANNEL_WIDTH_* to MHz. */
    fun widthMhz(code: Int): Int = when (code) {
        0 -> 20
        1 -> 40
        2 -> 80
        3, 4 -> 160
        5 -> 320
        else -> 20
    }

    fun security(capabilities: String): String {
        val c = capabilities.uppercase()
        val kinds = buildList {
            if ("WEP" in c) add("WEP")
            if ("[WPA-" in c) add("WPA")
            if ("WPA2" in c || Regex("\\[RSN-[^\\]]*(PSK|EAP)").containsMatchIn(c)) add("WPA2")
            if ("SAE" in c || "WPA3" in c || "SUITE_B" in c) add("WPA3")
            if ("OWE" in c) add("OWE")
        }.distinct()
        if (kinds.isEmpty()) return "Open"
        val enterprise = if ("EAP" in c) " Enterprise" else ""
        return kinds.joinToString("/") + enterprise
    }

    fun isOpen(security: String) = security == "Open"

    /** Maps ScanResult.WIFI_STANDARD_* to a readable label. */
    fun standard(code: Int, band: Band): String? = when (code) {
        1 -> "802.11a/b/g"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> if (band == Band.GHZ_6) "Wi-Fi 6E (802.11ax)" else "Wi-Fi 6 (802.11ax)"
        7 -> "WiGig (802.11ad)"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> null
    }

    fun quality(rssi: Int): Int = (2 * (rssi + 100)).coerceIn(0, 100)

    /** Free-space estimate. Walls make the real distance shorter, so treat it as an upper bound. */
    fun distanceMeters(rssi: Int, freq: Int): Double =
        10.0.pow((27.55 - 20 * log10(freq.toDouble()) + abs(rssi)) / 20.0)

    /** Free-space path loss at 1 m for a frequency in MHz. */
    fun pathLossAt1m(freq: Int): Double = 20 * log10(freq.toDouble()) - 27.55

    fun dbmToMw(dbm: Double): Double = 10.0.pow(dbm / 10.0)
    fun mwToDbm(mw: Double): Double = 10 * log10(mw)

    /** Effective occupied width: 2.4 GHz channels really spread over 22 MHz. */
    fun occupiedWidth(freq: Int, width: Int): Int =
        if (band(freq) == Band.GHZ_2_4 && width <= 20) 22 else width

    fun overlaps(f1: Int, w1: Int, f2: Int, w2: Int): Boolean =
        abs(f1 - f2) * 2 < occupiedWidth(f1, w1) + occupiedWidth(f2, w2)
}

fun ScanResult.toNetwork(connectedBssid: String?, now: Long): WifiNetwork {
    @Suppress("DEPRECATION")
    val name = (SSID ?: "").removeSurrounding("\"").let { if (it == "<unknown ssid>") "" else it }
    val width = WifiMath.widthMhz(channelWidth)
    val center = if (width > 20 && centerFreq0 > 0) centerFreq0 else frequency
    val band = WifiMath.band(frequency)
    val std = if (Build.VERSION.SDK_INT >= 30) WifiMath.standard(wifiStandard, band) else null
    val mac = BSSID ?: ""
    return WifiNetwork(
        bssid = mac,
        ssid = name,
        rssi = level,
        frequency = frequency,
        centerFrequency = center,
        channel = WifiMath.channel(frequency),
        widthMhz = width,
        band = band,
        security = WifiMath.security(capabilities ?: ""),
        standard = std,
        capabilities = capabilities ?: "",
        connected = connectedBssid != null && mac.equals(connectedBssid, ignoreCase = true),
        lastSeen = now,
    )
}
