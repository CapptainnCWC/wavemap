package io.github.capptainncwc.wavemap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.capptainncwc.wavemap.AppViewModel
import io.github.capptainncwc.wavemap.R
import io.github.capptainncwc.wavemap.wifi.Band
import io.github.capptainncwc.wavemap.wifi.WifiMath
import io.github.capptainncwc.wavemap.wifi.WifiNetwork

private class BandSpec(
    val band: Band,
    val minFreq: Int,
    val maxFreq: Int,
    /** 20 MHz channels that get an axis tick. */
    val ticks: List<Int>,
    val labelEvery: Int,
    /** Candidate primary channels considered for the recommendation. */
    val candidates: List<Int>,
    val chartWidth: Dp?,
)

private fun freqOf(band: Band, ch: Int): Int = when (band) {
    Band.GHZ_2_4 -> if (ch == 14) 2484 else 2407 + ch * 5
    Band.GHZ_5 -> 5000 + ch * 5
    Band.GHZ_6 -> 5950 + ch * 5
    else -> 0
}

private val specs = listOf(
    BandSpec(Band.GHZ_2_4, 2397, 2497, (1..14).toList(), 1, listOf(1, 6, 11), null),
    BandSpec(
        Band.GHZ_5, 5160, 5895,
        (36..64 step 4) + (100..144 step 4) + (149..177 step 4), 2,
        (36..64 step 4) + (100..144 step 4) + (149..165 step 4), 1300.dp,
    ),
    BandSpec(Band.GHZ_6, 5945, 7125, (1..233 step 4).toList(), 4, (5..229 step 16).toList(), 1500.dp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(vm: AppViewModel) {
    val networks by vm.networks.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val spec = specs[tab]
    val inBand = remember(networks, tab) { networks.filter { it.band == spec.band } }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.channels_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                specs.forEachIndexed { i, s ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text("${s.band.label} (${networks.count { it.band == s.band }})") },
                    )
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BoxWithConstraints(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    val width = spec.chartWidth?.coerceAtLeast(maxWidth) ?: maxWidth
                    ChannelGraph(inBand, spec, Modifier.width(width).height(320.dp).padding(8.dp))
                }
                Recommendations(inBand, spec)
            }
        }
    }
}

@Composable
private fun ChannelGraph(networks: List<WifiNetwork>, spec: BandSpec, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val nameStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val minDbm = -100
    val maxDbm = -20

    Canvas(modifier) {
        val left = 32.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val w = size.width - left
        val h = size.height - bottomPad
        fun x(freq: Float) = left + w * (freq - spec.minFreq) / (spec.maxFreq - spec.minFreq)
        fun y(dbm: Int) = h * (maxDbm - dbm.coerceIn(minDbm, maxDbm)) / (maxDbm - minDbm).toFloat()

        for (db in -90..-30 step 10) {
            drawLine(grid, Offset(left, y(db)), Offset(size.width, y(db)), 1f)
            drawText(measurer, "$db", Offset(0f, y(db) - 7.dp.toPx()), labelStyle)
        }
        spec.ticks.forEachIndexed { i, ch ->
            val xx = x(freqOf(spec.band, ch).toFloat())
            drawLine(grid, Offset(xx, h), Offset(xx, h + 4.dp.toPx()), 1f)
            if (i % spec.labelEvery == 0) {
                val layout = measurer.measure("$ch", labelStyle)
                drawText(layout, topLeft = Offset(xx - layout.size.width / 2f, h + 5.dp.toPx()))
            }
        }
        drawLine(labelColor, Offset(left, h), Offset(size.width, h), 1.5f)

        // Weakest first, so strong networks are drawn on top.
        for (n in networks.sortedBy { it.rssi }) {
            val half = WifiMath.occupiedWidth(n.frequency, n.widthMhz) / 2f
            val x0 = x(n.centerFrequency - half)
            val x1 = x(n.centerFrequency + half)
            val top = y(n.rssi)
            val edge = (x1 - x0) * 0.12f
            val path = Path().apply {
                moveTo(x0, h)
                cubicTo(x0 + edge * 0.5f, top, x0 + edge, top, x0 + edge * 2, top)
                lineTo(x1 - edge * 2, top)
                cubicTo(x1 - edge, top, x1 - edge * 0.5f, top, x1, h)
            }
            val color = networkColor(n.bssid)
            drawPath(path, color.copy(alpha = 0.18f))
            drawPath(path, color, style = Stroke(if (n.connected) 3.dp.toPx() else 1.5.dp.toPx()))
            val label = measurer.measure(
                n.ssid.ifEmpty { "•" }, nameStyle.copy(color = color),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = (x1 - x0).toInt().coerceAtLeast(40)),
            )
            drawText(
                label,
                topLeft = Offset(
                    ((x0 + x1) / 2 - label.size.width / 2f).coerceIn(left, size.width - label.size.width),
                    (top - label.size.height - 1.dp.toPx()).coerceAtLeast(0f),
                ),
            )
        }
    }
}

@Composable
private fun Recommendations(networks: List<WifiNetwork>, spec: BandSpec) {
    // Load on a channel = summed power of every AP whose occupied spectrum overlaps that 20 MHz channel.
    val ranked = remember(networks, spec) {
        spec.candidates.map { ch ->
            val f = freqOf(spec.band, ch)
            val overlapping = networks.filter { WifiMath.overlaps(f, 20, it.centerFrequency, it.widthMhz) }
            val mw = overlapping.sumOf { WifiMath.dbmToMw(it.rssi.toDouble()) }
            Triple(ch, overlapping.size, if (mw > 0) WifiMath.mwToDbm(mw) else null)
        }.sortedWith(compareBy({ it.third ?: -200.0 }, { it.second }))
    }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.best_channels), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.best_channels_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            ranked.take(if (spec.band == Band.GHZ_2_4) 3 else 6).forEachIndexed { i, (ch, count, dbm) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}. ${stringResource(R.string.ch_short)} $ch",
                        fontWeight = if (i == 0) FontWeight.Bold else null,
                        modifier = Modifier.width(110.dp),
                    )
                    Text(
                        if (count == 0) stringResource(R.string.channel_free)
                        else stringResource(R.string.channel_load, count, dbm?.toInt() ?: 0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (count == 0) signalColor(-40) else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (spec.band == Band.GHZ_5) {
                Box(Modifier.padding(top = 4.dp)) {
                    Text(
                        stringResource(R.string.dfs_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
