package io.github.capptainncwc.wavemap.survey

import kotlinx.serialization.Serializable

/** One access point heard at a survey point. */
@Serializable
data class Reading(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequency: Int,
    val widthMhz: Int = 20,
)

/** A measurement taken at a spot on the plan. x and y are 0..1 fractions of the plan's width and height. */
@Serializable
data class SurveyPoint(
    val id: String,
    val x: Float,
    val y: Float,
    val time: Long,
    val readings: List<Reading>,
    /** False when Android throttled the scan and cached results were used. */
    val fresh: Boolean = true,
)

/** A virtual access point used by the planning mode. */
@Serializable
data class VirtualAp(
    val id: String,
    val x: Float,
    val y: Float,
    val name: String,
    val txPowerDbm: Int = 20,
    val frequency: Int = 2437,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val created: Long,
    val planWidth: Int,
    val planHeight: Int,
    /** Real width of the plan in meters, used by the planning model and the scale bar. */
    val widthMeters: Float = 20f,
    val points: List<SurveyPoint> = emptyList(),
    val aps: List<VirtualAp> = emptyList(),
) {
    val aspect: Float get() = planHeight.toFloat() / planWidth
    val heightMeters: Float get() = widthMeters * aspect
}
