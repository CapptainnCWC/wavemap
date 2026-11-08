package io.github.capptainncwc.wavemap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.capptainncwc.wavemap.AppViewModel
import io.github.capptainncwc.wavemap.R
import io.github.capptainncwc.wavemap.speed.SpeedPhase
import io.github.capptainncwc.wavemap.wifi.WifiMath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedScreen(vm: AppViewModel) {
    val speed by vm.speed.collectAsStateWithLifecycle()
    val conn by vm.connection.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshConnection() }
    val running = speed.phase in setOf(SpeedPhase.PING, SpeedPhase.DOWNLOAD, SpeedPhase.UPLOAD)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.speed_title)) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val phaseLabel = when (speed.phase) {
                        SpeedPhase.IDLE -> R.string.speed_idle
                        SpeedPhase.PING -> R.string.ping
                        SpeedPhase.DOWNLOAD -> R.string.download
                        SpeedPhase.UPLOAD -> R.string.upload
                        SpeedPhase.DONE -> R.string.speed_done
                        SpeedPhase.ERROR -> R.string.speed_error
                    }
                    Text(stringResource(phaseLabel), style = MaterialTheme.typography.titleMedium)
                    if (running) {
                        Text(
                            if (speed.phase == SpeedPhase.PING) "%.0f".format(speed.current) else formatMbps(speed.current),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(if (speed.phase == SpeedPhase.PING) "ms" else "Mbit/s")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { speed.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    speed.error?.takeIf { speed.phase == SpeedPhase.ERROR }?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Metric(stringResource(R.string.ping), speed.result.pingMs?.let { "%.0f".format(it) }, "ms")
                        Metric(stringResource(R.string.jitter), speed.result.jitterMs?.let { "%.1f".format(it) }, "ms")
                        Metric(stringResource(R.string.download), speed.result.downloadMbps?.let(::formatMbps), "Mbit/s")
                        Metric(stringResource(R.string.upload), speed.result.uploadMbps?.let(::formatMbps), "Mbit/s")
                    }
                    Spacer(Modifier.height(16.dp))
                    if (running) {
                        OutlinedButton(onClick = vm::stopSpeedTest) { Text(stringResource(R.string.stop)) }
                    } else {
                        Button(onClick = vm::startSpeedTest) { Text(stringResource(R.string.start_test)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.speed_server_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.connection),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    val c = conn
                    if (c == null) {
                        Text(stringResource(R.string.no_connection))
                    } else {
                        InfoRow(stringResource(R.string.connection_type), if (c.wifi) "Wi-Fi" else stringResource(R.string.not_wifi))
                        if (c.wifi) {
                            HorizontalDivider()
                            InfoRow("SSID", c.ssid)
                            HorizontalDivider()
                            InfoRow("BSSID", c.bssid, mono = true)
                            HorizontalDivider()
                            InfoRow(stringResource(R.string.signal), c.rssi?.let { "$it dBm" })
                            HorizontalDivider()
                            InfoRow(
                                stringResource(R.string.frequency),
                                c.frequency?.let { "$it MHz · ${stringResource(R.string.ch_short)} ${WifiMath.channel(it)}" },
                            )
                            HorizontalDivider()
                            InfoRow(stringResource(R.string.standard), c.standard)
                            HorizontalDivider()
                            InfoRow(stringResource(R.string.link_speed), c.linkSpeedMbps?.let { "$it Mbit/s" })
                            HorizontalDivider()
                            InfoRow(
                                stringResource(R.string.rx_tx_speed),
                                if (c.rxSpeedMbps != null || c.txSpeedMbps != null) "${c.rxSpeedMbps ?: "—"} / ${c.txSpeedMbps ?: "—"} Mbit/s" else null,
                            )
                        }
                        HorizontalDivider()
                        InfoRow("IPv4", c.ipv4, mono = true)
                        HorizontalDivider()
                        InfoRow("IPv6", c.ipv6.joinToString("\n").ifEmpty { null }, mono = true)
                        HorizontalDivider()
                        InfoRow(stringResource(R.string.gateway), c.gateway, mono = true)
                        HorizontalDivider()
                        InfoRow("DNS", c.dns.joinToString("\n").ifEmpty { null }, mono = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String?, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(unit, style = MaterialTheme.typography.labelSmall)
    }
}
