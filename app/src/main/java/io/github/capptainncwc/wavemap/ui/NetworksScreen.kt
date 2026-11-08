package io.github.capptainncwc.wavemap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.capptainncwc.wavemap.AppViewModel
import io.github.capptainncwc.wavemap.R
import io.github.capptainncwc.wavemap.wifi.Band
import io.github.capptainncwc.wavemap.wifi.WifiMath
import io.github.capptainncwc.wavemap.wifi.WifiNetwork
import java.text.DateFormat
import java.util.Date

enum class SortMode { SIGNAL, NAME, CHANNEL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworksScreen(vm: AppViewModel) {
    val networks by vm.networks.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val throttled by vm.throttled.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val interval by vm.intervalSec.collectAsStateWithLifecycle()

    var band by rememberSaveable { mutableStateOf<Band?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortMode.SIGNAL) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var intervalMenu by remember { mutableStateOf(false) }

    selected?.let { bssid ->
        NetworkDetailScreen(vm, bssid) { selected = null }
        return
    }

    val shown = remember(networks, band, sort, query) {
        networks
            .filter { band == null || it.band == band }
            .filter {
                query.isBlank() || it.ssid.contains(query, ignoreCase = true) ||
                    it.bssid.contains(query, ignoreCase = true)
            }
            .let { list ->
                when (sort) {
                    SortMode.SIGNAL -> list.sortedByDescending { it.rssi }
                    SortMode.NAME -> list.sortedWith(compareBy({ it.ssid.isEmpty() }, { it.ssid.lowercase() }))
                    SortMode.CHANNEL -> list.sortedWith(compareBy({ it.frequency }, { -it.rssi }))
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tab_networks))
                        Text(
                            stringResource(R.string.networks_found, networks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleScanning) {
                        Icon(
                            if (scanning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            stringResource(if (scanning) R.string.pause_scan else R.string.resume_scan),
                        )
                    }
                    IconButton(onClick = { intervalMenu = true }) {
                        Icon(Icons.Filled.Timer, stringResource(R.string.scan_interval))
                    }
                    DropdownMenu(intervalMenu, { intervalMenu = false }) {
                        listOf(2, 5, 10, 30).forEach { s ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.every_n_seconds, s)) },
                                leadingIcon = { RadioButton(selected = interval == s, onClick = null) },
                                onClick = { vm.setInterval(s); intervalMenu = false },
                            )
                        }
                    }
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort))
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        SortMode.entries.forEach { m ->
                            val label = when (m) {
                                SortMode.SIGNAL -> R.string.sort_signal
                                SortMode.NAME -> R.string.sort_name
                                SortMode.CHANNEL -> R.string.sort_channel
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                leadingIcon = { RadioButton(selected = sort == m, onClick = null) },
                                onClick = { sort = m; sortMenu = false },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { StatusBanners(status, throttled && scanning) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(band == null, { band = null }, { Text(stringResource(R.string.all_bands)) })
                    listOf(Band.GHZ_2_4, Band.GHZ_5, Band.GHZ_6).forEach { b ->
                        val count = networks.count { it.band == b }
                        FilterChip(band == b, { band = if (band == b) null else b }, { Text("${b.label} ($count)") })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_networks),
                        Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.bssid }) { n ->
                NetworkRow(n, Modifier.padding(horizontal = 16.dp)) { selected = n.bssid }
            }
        }
    }
}

@Composable
private fun NetworkRow(n: WifiNetwork, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SignalBadge(n.rssi)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(networkColor(n.bssid))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        ssidLabel(n.ssid),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (n.connected) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.connected),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(n.bssid, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text(
                    "${stringResource(R.string.ch_short)} ${n.channel} · ${n.band.label} · ${n.widthMhz} MHz" +
                        (n.standard?.let { " · ${it.substringBefore(" (")}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    if (WifiMath.isOpen(n.security)) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(n.security, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDetailScreen(vm: AppViewModel, bssid: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val networks by vm.networks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val n = networks.firstOrNull { it.bssid == bssid }
    val samples = history[bssid].orEmpty()
    var lastKnown by remember { mutableStateOf<WifiNetwork?>(null) }
    if (n != null) lastKnown = n
    val net = n ?: lastKnown

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(net?.let { ssidLabel(it.ssid) } ?: bssid, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (net == null) {
                Text(stringResource(R.string.network_gone))
                return@Column
            }
            if (n == null) AssistChip(onClick = {}, label = { Text(stringResource(R.string.network_gone)) })
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalBadge(net.rssi)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.quality_value, net.quality), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { net.quality / 100f },
                                color = signalColor(net.rssi),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (samples.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.min_avg_max,
                                samples.minOf { it.rssi },
                                samples.map { it.rssi }.average().toInt(),
                                samples.maxOf { it.rssi },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.signal_history), style = MaterialTheme.typography.titleSmall)
                    SignalChart(
                        listOf(ChartSeries(bssid, networkColor(bssid), samples)),
                        windowMs = 5 * 60_000L,
                        modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp),
                    )
                }
            }
            Card {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow("SSID", ssidLabel(net.ssid))
                    HorizontalDivider()
                    InfoRow("BSSID", net.bssid, mono = true)
                    HorizontalDivider()
                    InfoRow(stringResource(R.string.frequency), "${net.frequency} MHz")
                    HorizontalDivider()
                    InfoRow(stringResource(R.string.channel), "${net.channel} (${net.band.label})")
                    HorizontalDivider()
                    InfoRow(stringResource(R.string.channel_width), "${net.widthMhz} MHz")
                    HorizontalDivider()
                    InfoRow(stringResource(R.string.center_frequency), "${net.centerFrequency} MHz")
                    HorizontalDivider()
                    InfoRow(stringResource(R.string.standard), net.standard)
                    HorizontalDivider()
                    InfoRow(stringResource(R.string.security), net.security)
                    HorizontalDivider()
                    InfoRow(stringResource(R.string.distance), "≈ %.1f m".format(net.distanceMeters))
                    HorizontalDivider()
                    InfoRow(stringResource(R.string.last_seen), DateFormat.getTimeInstance().format(Date(net.lastSeen)))
                    HorizontalDivider()
                    InfoRow(stringResource(R.string.capabilities), net.capabilities, mono = true)
                }
            }
        }
    }
}
