package io.github.capptainncwc.wavemap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.capptainncwc.wavemap.AppViewModel
import io.github.capptainncwc.wavemap.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalScreen(vm: AppViewModel) {
    val networks by vm.networks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var windowMs by rememberSaveable { mutableLongStateOf(5 * 60_000L) }
    // null = automatic: the strongest networks are shown.
    var picked by remember { mutableStateOf<Set<String>?>(null) }

    val visible = picked ?: networks.take(AUTO_COUNT).map { it.bssid }.toSet()
    val series = networks.filter { it.bssid in visible }.map {
        ChartSeries(it.bssid, networkColor(it.bssid), history[it.bssid].orEmpty())
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.signal_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SignalChart(series, windowMs, Modifier.fillMaxWidth().height(260.dp).padding(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(1, 5, 10, 15).forEach { m ->
                    FilterChip(windowMs == m * 60_000L, { windowMs = m * 60_000L }, { Text(stringResource(R.string.minutes_short, m)) })
                }
                FilterChip(picked == null, { picked = null }, { Text(stringResource(R.string.auto_top, AUTO_COUNT)) })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                items(networks, key = { it.bssid }) { n ->
                    val on = n.bssid in visible
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { picked = if (on) visible - n.bssid else visible + n.bssid }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        ColorDot(networkColor(n.bssid))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ssidLabel(n.ssid), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${n.bssid} · ${stringResource(R.string.ch_short)} ${n.channel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${n.rssi} dBm", color = signalColor(n.rssi))
                    }
                }
            }
        }
    }
}

private const val AUTO_COUNT = 6
