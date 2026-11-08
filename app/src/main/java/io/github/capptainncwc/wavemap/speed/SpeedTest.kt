package io.github.capptainncwc.wavemap.speed

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SpeedPhase { IDLE, PING, DOWNLOAD, UPLOAD, DONE, ERROR }

data class SpeedResult(
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
)

data class SpeedState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    /** Live value for the running phase: ms for ping, Mbit/s otherwise. */
    val current: Double = 0.0,
    val progress: Float = 0f,
    val result: SpeedResult = SpeedResult(),
    val error: String? = null,
)

/**
 * Throughput test against Cloudflare's public speed test endpoints
 * (the same ones speed.cloudflare.com uses). Several parallel streams are used
 * so that a single TCP connection's ramp-up does not cap the result.
 */
class SpeedTest(
    private val baseUrl: String = "https://speed.cloudflare.com",
    private val durationMs: Long = 10_000,
    private val streams: Int = 4,
) {
    suspend fun run(onUpdate: (SpeedState) -> Unit): SpeedResult = withContext(Dispatchers.IO) {
        var result = SpeedResult()
        onUpdate(SpeedState(SpeedPhase.PING))
        val (ping, jitter) = ping()
        result = result.copy(pingMs = ping, jitterMs = jitter)
        onUpdate(SpeedState(SpeedPhase.PING, ping, 1f, result))

        val down = measure(SpeedPhase.DOWNLOAD, result, onUpdate) { bytes, stopped ->
            val c = open("$baseUrl/__down?bytes=$DOWN_CHUNK")
            try {
                c.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (!stopped()) {
                        val n = input.read(buf)
                        if (n < 0) break
                        bytes.addAndGet(n.toLong())
                    }
                }
            } finally {
                c.disconnect()
            }
        }
        result = result.copy(downloadMbps = down)

        val payload = ByteArray(64 * 1024).also { Random.nextBytes(it) }
        val up = measure(SpeedPhase.UPLOAD, result, onUpdate) { bytes, stopped ->
            val c = open("$baseUrl/__up")
            try {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setFixedLengthStreamingMode(UP_CHUNK)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.outputStream.use { out ->
                    var sent = 0
                    while (sent < UP_CHUNK && !stopped()) {
                        val n = minOf(payload.size, UP_CHUNK - sent)
                        out.write(payload, 0, n)
                        sent += n
                        bytes.addAndGet(n.toLong())
                    }
                }
                if (!stopped()) c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }
        result = result.copy(uploadMbps = up)
        onUpdate(SpeedState(SpeedPhase.DONE, 0.0, 1f, result))
        result
    }

    /**
     * Latency as TCP handshake time, which is one network round trip. Timing HTTP requests instead
     * would add TLS handshakes whenever a connection is not reused.
     */
    private fun ping(): Pair<Double, Double> {
        val url = URL(baseUrl)
        val address = InetAddress.getByName(url.host)
        val port = if (url.port > 0) url.port else url.defaultPort
        val times = mutableListOf<Double>()
        repeat(PING_COUNT) {
            Socket().use { socket ->
                val t0 = System.nanoTime()
                socket.connect(InetSocketAddress(address, port), 3_000)
                times += (System.nanoTime() - t0) / 1e6
            }
        }
        val sorted = times.sorted()
        val median = (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
        val jitter = times.zipWithNext { a, b -> abs(a - b) }.average()
        return median to jitter
    }

    private suspend fun measure(
        phase: SpeedPhase,
        partial: SpeedResult,
        onUpdate: (SpeedState) -> Unit,
        worker: (AtomicLong, () -> Boolean) -> Unit,
    ): Double = coroutineScope {
        val bytes = AtomicLong()
        val stop = AtomicBoolean(false)
        val errors = AtomicLong()
        val start = System.nanoTime()
        repeat(streams) {
            launch(Dispatchers.IO) {
                while (!stop.get()) {
                    try {
                        worker(bytes) { stop.get() }
                    } catch (e: IOException) {
                        if (!stop.get()) {
                            errors.incrementAndGet()
                            delay(300)
                        }
                    }
                }
            }
        }
        var elapsed: Long
        try {
            do {
                delay(250)
                elapsed = (System.nanoTime() - start) / 1_000_000
                val mbps = bytes.get() * 8.0 / (elapsed / 1000.0) / 1e6
                onUpdate(SpeedState(phase, mbps, (elapsed.toFloat() / durationMs).coerceAtMost(1f), partial))
            } while (elapsed < durationMs)
        } finally {
            stop.set(true)
        }
        if (bytes.get() == 0L) throw IOException("No data transferred (${errors.get()} failed requests)")
        bytes.get() * 8.0 / (elapsed / 1000.0) / 1e6
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("User-Agent", "WaveMap")
        }

    private companion object {
        const val PING_COUNT = 8
        const val DOWN_CHUNK = 25_000_000
        const val UP_CHUNK = 8_000_000
    }
}
