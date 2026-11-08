package io.github.capptainncwc.wavemap.survey

import android.graphics.Bitmap
import io.github.capptainncwc.wavemap.wifi.WifiMath
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Metric { SIGNAL, SNR, SIR, AP_COUNT, PLANNED }

/** Which network a heatmap describes. */
sealed interface Target {
    data object Any : Target
    data class Ssid(val ssid: String) : Target
    data class Bssid(val bssid: String, val ssid: String) : Target
}

object Heatmap {
    /** Readings at or above this level count towards [Metric.AP_COUNT]. */
    const val USABLE_DBM = -75

    /** Path loss exponent for a typical office/home with internal walls. */
    const val INDOOR_EXPONENT = 3.0

    fun range(metric: Metric): ClosedFloatingPointRange<Float> = when (metric) {
        Metric.SIGNAL, Metric.PLANNED -> -90f..-30f
        Metric.SNR -> 0f..60f
        Metric.SIR -> 0f..40f
        Metric.AP_COUNT -> 0f..10f
    }

    fun unit(metric: Metric): String = when (metric) {
        Metric.SIGNAL, Metric.PLANNED -> "dBm"
        Metric.SNR, Metric.SIR -> "dB"
        Metric.AP_COUNT -> ""
    }

    fun matches(r: Reading, target: Target): Boolean = when (target) {
        Target.Any -> true
        is Target.Ssid -> r.ssid == target.ssid
        is Target.Bssid -> r.bssid.equals(target.bssid, ignoreCase = true)
    }

    /** Value of a metric at one survey point. */
    fun value(point: SurveyPoint, metric: Metric, target: Target): Float {
        val best = point.readings.filter { matches(it, target) }.maxByOrNull { it.rssi }
        return when (metric) {
            Metric.SIGNAL, Metric.PLANNED -> (best?.rssi ?: -100).toFloat()
            Metric.SNR -> ((best?.rssi ?: WifiMath.NOISE_FLOOR_DBM) - WifiMath.NOISE_FLOOR_DBM).toFloat()
            Metric.SIR -> {
                if (best == null) return 0f
                val interferenceMw = point.readings
                    .filter { other ->
                        other.bssid != best.bssid &&
                            (other.ssid != best.ssid || other.ssid.isEmpty()) &&
                            WifiMath.overlaps(best.frequency, best.widthMhz, other.frequency, other.widthMhz)
                    }
                    .sumOf { WifiMath.dbmToMw(it.rssi.toDouble()) }
                if (interferenceMw <= 0.0) 40f
                else (best.rssi - WifiMath.mwToDbm(interferenceMw)).toFloat().coerceIn(-10f, 40f)
            }
            Metric.AP_COUNT -> point.readings.count { it.rssi >= USABLE_DBM }.toFloat()
        }
    }

    /** Predicted RSSI from the virtual access points (log-distance path loss model). */
    fun planned(project: Project, x: Float, yInWidthUnits: Float): Float {
        if (project.aps.isEmpty()) return -100f
        var best = -120.0
        for (ap in project.aps) {
            val d = hypot(x - ap.x, yInWidthUnits - ap.y * project.aspect) * project.widthMeters
            val loss = WifiMath.pathLossAt1m(ap.frequency) + 10 * INDOOR_EXPONENT * log10(max(d.toDouble(), 0.5))
            best = max(best, ap.txPowerDbm - loss)
        }
        return best.toFloat()
    }

    /**
     * Renders a low-resolution heatmap; draw it stretched over the plan with bilinear filtering.
     * Measured metrics use inverse distance weighting and fade out far from any measurement.
     */
    fun render(project: Project, metric: Metric, target: Target, gridWidth: Int = 220): Bitmap {
        val aspect = project.aspect
        val gw = gridWidth
        val gh = max(1, (gw * aspect).roundToInt())
        val pixels = IntArray(gw * gh)
        val range = range(metric)

        val samples = if (metric == Metric.PLANNED) emptyList() else project.points.map {
            floatArrayOf(it.x, it.y * aspect, value(it, metric, target))
        }
        if (metric != Metric.PLANNED && samples.isEmpty()) {
            return Bitmap.createBitmap(pixels, gw, gh, Bitmap.Config.ARGB_8888)
        }
        // Fade radius: enough to bridge neighbouring points, never covering the whole plan from one point.
        val fade = (0.12f + 0.5f / sqrt(samples.size.coerceAtLeast(1).toFloat())).coerceAtMost(0.45f)

        for (gy in 0 until gh) {
            val fy = (gy + 0.5f) / gh * aspect
            for (gx in 0 until gw) {
                val fx = (gx + 0.5f) / gw
                var alpha = 0.7f
                val v: Float
                if (metric == Metric.PLANNED) {
                    v = planned(project, fx, fy)
                } else {
                    var num = 0.0
                    var den = 0.0
                    var minD2 = Float.MAX_VALUE
                    var exact = Float.NaN
                    for (s in samples) {
                        val dx = fx - s[0]
                        val dy = fy - s[1]
                        val d2 = dx * dx + dy * dy
                        if (d2 < minD2) minD2 = d2
                        if (d2 < 1e-7f) {
                            exact = s[2]
                            break
                        }
                        val w = 1.0 / (d2 * d2) // power 4 on distance keeps local detail
                        num += w * s[2]
                        den += w
                    }
                    v = if (!exact.isNaN()) exact else (num / den).toFloat()
                    val d = sqrt(minD2)
                    alpha *= 1f - smoothstep(fade * 0.6f, fade, d)
                }
                if (alpha <= 0.01f) continue
                val t = ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                pixels[gy * gw + gx] = withAlpha(color(t), alpha)
            }
        }
        return Bitmap.createBitmap(pixels, gw, gh, Bitmap.Config.ARGB_8888)
    }

    private val stops = intArrayOf(
        0xFFD32F2F.toInt(), // bad
        0xFFFF7043.toInt(),
        0xFFFFCA28.toInt(),
        0xFF9CCC65.toInt(),
        0xFF2E7D32.toInt(), // good
    )

    /** Colour for t in 0..1, red (bad) to green (good). */
    fun color(t: Float): Int {
        val scaled = t.coerceIn(0f, 1f) * (stops.size - 1)
        val i = scaled.toInt().coerceAtMost(stops.size - 2)
        return lerpColor(stops[i], stops[i + 1], scaled - i)
    }

    val legendColors: IntArray get() = stops.copyOf()

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
