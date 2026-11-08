package io.github.capptainncwc.wavemap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.capptainncwc.wavemap.wifi.SignalSample

data class ChartSeries(val key: String, val color: Color, val samples: List<SignalSample>)

/** RSSI over time. The right edge is the newest sample of any series. */
@Composable
fun SignalChart(
    series: List<ChartSeries>,
    windowMs: Long,
    modifier: Modifier = Modifier,
    minDbm: Int = -100,
    maxDbm: Int = -20,
) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val now = series.maxOfOrNull { it.samples.lastOrNull()?.time ?: 0L }?.takeIf { it > 0 } ?: System.currentTimeMillis()

    Canvas(modifier) {
        val left = 36.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val chartW = size.width - left
        val chartH = size.height - bottomPad
        fun y(dbm: Int) = chartH * (maxDbm - dbm) / (maxDbm - minDbm).toFloat()
        fun x(t: Long) = left + chartW * (1f - (now - t).toFloat() / windowMs)

        for (db in (minDbm + 10)..(maxDbm - 10) step 10) {
            val yy = y(db)
            drawLine(grid, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f)
            drawText(measurer, "$db", Offset(0f, yy - 7.dp.toPx()), labelStyle)
        }
        val minutes = (windowMs / 60_000L).toInt()
        val step = if (minutes <= 2) 1 else if (minutes <= 10) 2 else 5
        for (m in 0..minutes step step) {
            val xx = x(now - m * 60_000L)
            drawLine(grid, Offset(xx, 0f), Offset(xx, chartH), strokeWidth = 1f)
            drawText(measurer, if (m == 0) "0" else "-${m}m", Offset(xx - 8.dp.toPx(), chartH + 2.dp.toPx()), labelStyle)
        }

        for (s in series) {
            val pts = s.samples.filter { now - it.time <= windowMs }
            if (pts.isEmpty()) continue
            if (pts.size == 1) {
                drawCircle(s.color, 3.dp.toPx(), Offset(x(pts[0].time), y(pts[0].rssi)))
                continue
            }
            val path = Path()
            pts.forEachIndexed { i, p ->
                val px = x(p.time)
                val py = y(p.rssi.coerceIn(minDbm, maxDbm))
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, s.color, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
