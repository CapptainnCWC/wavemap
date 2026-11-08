package io.github.capptainncwc.wavemap.ui.survey

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.capptainncwc.wavemap.AppViewModel
import io.github.capptainncwc.wavemap.R
import io.github.capptainncwc.wavemap.survey.Heatmap
import io.github.capptainncwc.wavemap.survey.Metric
import io.github.capptainncwc.wavemap.survey.Project
import io.github.capptainncwc.wavemap.survey.SurveyPoint
import io.github.capptainncwc.wavemap.survey.Target
import io.github.capptainncwc.wavemap.survey.VirtualAp
import io.github.capptainncwc.wavemap.ui.ssidLabel
import io.github.capptainncwc.wavemap.ui.signalColor
import io.github.capptainncwc.wavemap.wifi.WifiMath
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Mode { MEASURE, HEATMAP, PLAN }

private fun metricLabel(m: Metric) = when (m) {
    Metric.SIGNAL -> R.string.metric_signal
    Metric.SNR -> R.string.metric_snr
    Metric.SIR -> R.string.metric_sir
    Metric.AP_COUNT -> R.string.metric_ap_count
    Metric.PLANNED -> R.string.metric_planned
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyScreen(vm: AppViewModel, project: Project) {
    BackHandler { vm.closeProject() }
    val measuring by vm.measuring.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(Mode.MEASURE) }
    var metric by rememberSaveable { mutableStateOf(Metric.SIGNAL) }
    var target by remember { mutableStateOf<Target>(Target.Any) }
    var selectedPoint by remember { mutableStateOf<SurveyPoint?>(null) }
    var editingAp by remember { mutableStateOf<VirtualAp?>(null) }
    var editingProject by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }

    val effectiveMetric = if (mode == Mode.PLAN) Metric.PLANNED else metric
    val plan by produceState<ImageBitmap?>(null, project.id) {
        value = withContext(Dispatchers.IO) { vm.repo.loadPlan(project)?.asImageBitmap() }
    }
    val heat by produceState<ImageBitmap?>(null, project.points, project.aps, project.widthMeters, effectiveMetric, target, mode) {
        value = if (mode == Mode.MEASURE) null
        else withContext(Dispatchers.Default) { Heatmap.render(project, effectiveMetric, target).asImageBitmap() }
    }
    val throttledMsg = stringResource(R.string.measure_throttled)
    val chooser = stringResource(R.string.share)
    val metricName = stringResource(metricLabel(effectiveMetric))
    val targetName = when (val t = target) {
        Target.Any -> stringResource(R.string.target_any)
        is Target.Ssid -> ssidLabel(t.ssid)
        is Target.Bssid -> "${ssidLabel(t.ssid)} (${t.bssid})"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = vm::closeProject) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { editingProject = true }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { exportMenu = true }) { Icon(Icons.Filled.Share, stringResource(R.string.export)) }
                    DropdownMenu(exportMenu, { exportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_png)) },
                            onClick = {
                                exportMenu = false
                                vm.exportPng(effectiveMetric, target, "${project.name} — $metricName · $targetName", chooser)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_csv)) },
                            onClick = { exportMenu = false; vm.exportCsv(chooser) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (m) {
                                    Mode.MEASURE -> R.string.mode_measure
                                    Mode.HEATMAP -> R.string.mode_heatmap
                                    Mode.PLAN -> R.string.mode_plan
                                },
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                PlanView(
                    project = project,
                    plan = plan,
                    heat = heat,
                    showPoints = mode != Mode.PLAN,
                    showAps = mode == Mode.PLAN,
                    onTap = { x, y ->
                        when (mode) {
                            Mode.PLAN -> {
                                val ap = nearest(project.aps, x, y, project.aspect) { it.x to it.y }
                                if (ap != null) editingAp = ap else vm.addAp(x, y)
                            }
                            else -> {
                                val p = nearest(project.points, x, y, project.aspect) { it.x to it.y }
                                if (p != null) selectedPoint = p
                                else if (mode == Mode.MEASURE) vm.measureAt(x, y, throttledMsg)
                            }
                        }
                    },
                )
                if (measuring) {
                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(12.dp),
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 4.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.measuring))
                        }
                    }
                }
            }

            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (mode) {
                        Mode.MEASURE -> {
                            Text(stringResource(R.string.measure_hint), style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.points_count, project.points.size),
                                    Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                OutlinedButton(onClick = vm::undoPoint, enabled = project.points.isNotEmpty() && !measuring) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, null)
                                    Text(stringResource(R.string.undo), Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                        Mode.HEATMAP -> {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(Metric.SIGNAL, Metric.SNR, Metric.SIR, Metric.AP_COUNT).forEach { m ->
                                    FilterChip(metric == m, { metric = m }, { Text(stringResource(metricLabel(m))) })
                                }
                            }
                            TargetPicker(project, target, targetName) { target = it }
                            Legend(effectiveMetric)
                            if (project.points.isEmpty()) {
                                Text(stringResource(R.string.no_points_yet), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Mode.PLAN -> {
                            Text(stringResource(R.string.plan_hint), style = MaterialTheme.typography.bodySmall)
                            Legend(Metric.PLANNED)
                        }
                    }
                }
            }
        }
    }

    selectedPoint?.let { p ->
        PointSheet(
            point = p,
            index = project.points.indexOfFirst { it.id == p.id } + 1,
            onDismiss = { selectedPoint = null },
            onDelete = { vm.deletePoint(p.id); selectedPoint = null },
        )
    }
    editingAp?.let { ap ->
        ApDialog(
            ap = ap,
            onDismiss = { editingAp = null },
            onSave = { vm.updateAp(it); editingAp = null },
            onDelete = { vm.deleteAp(ap.id); editingAp = null },
        )
    }
    if (editingProject) {
        ProjectDialog(project, onDismiss = { editingProject = false }) { name, width ->
            vm.renameProject(name, width)
            editingProject = false
        }
    }
}

/** Nearest item within the tap radius, in plan-width units. */
private fun <T> nearest(items: List<T>, x: Float, y: Float, aspect: Float, pos: (T) -> Pair<Float, Float>): T? =
    items.map { it to pos(it) }
        .map { (item, p) -> item to hypot(p.first - x, (p.second - y) * aspect) }
        .filter { it.second < 0.035f }
        .minByOrNull { it.second }?.first

@Composable
private fun PlanView(
    project: Project,
    plan: ImageBitmap?,
    heat: ImageBitmap?,
    showPoints: Boolean,
    showAps: Boolean,
    onTap: (Float, Float) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 8f)
                    val maxX = (scale - 1f) * size.width / 2f
                    val maxY = (scale - 1f) * size.height / 2f
                    offset = Offset(
                        (offset.x + pan.x).coerceIn(-maxX, maxX),
                        (offset.y + pan.y).coerceIn(-maxY, maxY),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .aspectRatio(project.planWidth.toFloat() / project.planHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(project.id) {
                    detectTapGestures { pos -> onTap(pos.x / size.width, pos.y / size.height) }
                },
        ) {
            if (plan != null) {
                Image(plan, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            } else {
                Box(Modifier.fillMaxSize().background(Color.White))
            }
            heat?.let {
                Image(
                    it, null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Medium,
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val r = with(density) { 7.dp.toPx() } / scale
                val stroke = with(density) { 2.dp.toPx() } / scale
                if (showPoints) {
                    project.points.forEachIndexed { i, p ->
                        val c = Offset(p.x * size.width, p.y * size.height)
                        val best = p.readings.maxOfOrNull { it.rssi } ?: -100
                        drawCircle(signalColor(best), r, c)
                        drawCircle(Color.White, r, c, style = Stroke(stroke))
                        if (!p.fresh) drawCircle(Color.Black, r * 0.35f, c)
                        if (scale > 1.8f) {
                            drawText(
                                measurer, "${i + 1}", c + Offset(r * 1.2f, -r * 2f),
                                TextStyle(fontSize = (11 / scale).sp, color = Color.Black, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
                if (showAps) {
                    project.aps.forEach { ap ->
                        val c = Offset(ap.x * size.width, ap.y * size.height)
                        drawCircle(primary, r * 1.6f, c)
                        drawCircle(Color.White, r * 1.6f, c, style = Stroke(stroke))
                        drawText(
                            measurer, ap.name, c + Offset(r * 2f, -r * 2.4f),
                            TextStyle(fontSize = (11 / scale).sp, color = Color.Black, fontWeight = FontWeight.Bold,
                                background = Color.White.copy(alpha = 0.7f)),
                        )
                    }
                }
            }
        }
        ScaleBar(project, scale, Modifier.padding(8.dp))
    }
}

@Composable
private fun ScaleBar(project: Project, scale: Float, modifier: Modifier) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Width of the plan on screen, as laid out by aspectRatio in the parent.
        val planRatio = project.planWidth.toFloat() / project.planHeight
        val boxRatio = maxWidth / maxHeight
        val planWidthDp = if (planRatio >= boxRatio) maxWidth else maxHeight * planRatio
        val dpPerMeter = planWidthDp * scale / project.widthMeters
        val meters = listOf(0.5f, 1f, 2f, 5f, 10f, 20f, 50f, 100f).firstOrNull { dpPerMeter * it >= 60.dp } ?: 100f
        Column(modifier.align(Alignment.BottomStart)) {
            Text(
                if (meters < 1f) "${(meters * 100).roundToInt()} cm" else "${meters.roundToInt()} m",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 2.dp),
                color = Color.Black,
            )
            Box(Modifier.width(dpPerMeter * meters).height(4.dp).background(Color.Black))
        }
    }
}

@Composable
private fun Legend(metric: Metric) {
    val range = Heatmap.range(metric)
    val unit = Heatmap.unit(metric)
    Column {
        Box(
            Modifier.fillMaxWidth().height(12.dp).background(
                Brush.horizontalGradient(Heatmap.legendColors.map { Color(it) }),
                RoundedCornerShape(6.dp),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text("${range.start.roundToInt()} $unit", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("${range.endInclusive.roundToInt()} $unit", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TargetPicker(project: Project, target: Target, label: String, onPick: (Target) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val readings = remember(project.points) { project.points.flatMap { it.readings } }
    val ssids = remember(readings) { readings.map { it.ssid }.filter { it.isNotEmpty() }.distinct().sorted() }
    val bssids = remember(readings) {
        readings.groupBy { it.bssid }.map { (b, rs) -> Target.Bssid(b, rs.first().ssid) to rs.maxOf { it.rssi } }
            .sortedByDescending { it.second }.map { it.first }
    }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.network_label) + ": " + label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.target_any)) }, onClick = { onPick(Target.Any); open = false })
            ssids.forEach { s ->
                DropdownMenuItem(
                    text = { Text("SSID: $s", fontWeight = if (target == Target.Ssid(s)) FontWeight.Bold else null) },
                    onClick = { onPick(Target.Ssid(s)); open = false },
                )
            }
            bssids.forEach { b ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(ssidLabel(b.ssid))
                            Text(b.bssid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = { onPick(b); open = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointSheet(point: SurveyPoint, index: Int, onDismiss: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.point_n, index), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
            Text(
                stringResource(R.string.point_aps, point.readings.size) +
                    if (!point.fresh) " · " + stringResource(R.string.point_cached) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                items(point.readings.sortedByDescending { it.rssi }) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ssidLabel(r.ssid), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${r.bssid} · ${stringResource(R.string.ch_short)} ${WifiMath.channel(r.frequency)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text("${r.rssi} dBm", color = signalColor(r.rssi), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ApDialog(ap: VirtualAp, onDismiss: () -> Unit, onSave: (VirtualAp) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(ap.name) }
    var power by remember { mutableFloatStateOf(ap.txPowerDbm.toFloat()) }
    var freq by remember { mutableStateOf(ap.frequency) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.virtual_ap)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.project_name)) }, singleLine = true)
                Text(stringResource(R.string.tx_power, power.roundToInt()))
                Slider(power, { power = it }, valueRange = 0f..30f, steps = 29)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(freq < 3000, { freq = 2437 }, { Text("2.4 GHz") })
                    FilterChip(freq in 5000..5900, { freq = 5200 }, { Text("5 GHz") })
                    FilterChip(freq > 5900, { freq = 6115 }, { Text("6 GHz") })
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(ap.copy(name = name.ifBlank { ap.name }, txPowerDbm = power.roundToInt(), frequency = freq)) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ProjectDialog(project: Project, onDismiss: () -> Unit, onSave: (String, Float) -> Unit) {
    var name by remember { mutableStateOf(project.name) }
    var width by remember { mutableStateOf(project.widthMeters.toString()) }
    val w = width.replace(',', '.').toFloatOrNull()?.takeIf { it in 1f..2000f }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.project_name)) }, singleLine = true)
                OutlinedTextField(
                    width, { width = it },
                    label = { Text(stringResource(R.string.plan_width_m)) },
                    isError = w == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (w != null) onSave(name.ifBlank { project.name }, w) }, enabled = w != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
