package io.github.capptainncwc.wavemap.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.capptainncwc.wavemap.DeviceStatus
import io.github.capptainncwc.wavemap.R

/** Stable, distinguishable colour per access point. */
fun networkColor(bssid: String): Color {
    val hue = ((bssid.hashCode().toLong() and 0x7fffffff) * 137 % 360).toFloat()
    return Color.hsv(hue, 0.7f, 0.85f)
}

fun signalColor(rssi: Int): Color = when {
    rssi >= -55 -> Color(0xFF2E7D32)
    rssi >= -67 -> Color(0xFF7CB342)
    rssi >= -75 -> Color(0xFFF9A825)
    rssi >= -85 -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}

@Composable
fun ssidLabel(ssid: String): String = ssid.ifEmpty { stringResource(R.string.hidden_network) }

@Composable
fun SignalBadge(rssi: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(52.dp)
            .background(signalColor(rssi).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$rssi", color = signalColor(rssi), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("dBm", color = signalColor(rssi), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(color, CircleShape))
}

@Composable
fun InfoRow(label: String, value: String?, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
        Text(
            value ?: "—",
            fontFamily = if (mono) FontFamily.Monospace else null,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** Warnings shown above the network list when scanning cannot work properly. */
@Composable
fun StatusBanners(status: DeviceStatus, throttled: Boolean) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!status.wifiEnabled) {
            Banner(Icons.Filled.WifiOff, stringResource(R.string.wifi_off), stringResource(R.string.open_settings)) {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }
        if (!status.locationEnabled) {
            Banner(Icons.Filled.LocationOff, stringResource(R.string.location_off), stringResource(R.string.open_settings)) {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
        if (throttled) {
            Banner(Icons.Filled.Timer, stringResource(R.string.throttled), stringResource(R.string.developer_options)) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        }
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                text,
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

fun formatMbps(v: Double?): String = when {
    v == null -> "—"
    v >= 100 -> "%.0f".format(v)
    v >= 10 -> "%.1f".format(v)
    else -> "%.2f".format(v)
}
