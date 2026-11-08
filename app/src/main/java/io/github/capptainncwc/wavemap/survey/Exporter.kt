package io.github.capptainncwc.wavemap.survey

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.capptainncwc.wavemap.wifi.WifiMath
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes heatmap images, CSV and project files into the cache and shares them. */
class Exporter(private val context: Context, private val repo: ProjectRepository) {
    private val dir get() = File(context.cacheDir, "export").apply { mkdirs() }

    fun heatmapPng(project: Project, metric: Metric, target: Target, title: String): Uri {
        val plan = repo.loadPlan(project) ?: error("Plan image missing")
        val legendH = (plan.width * 0.09f).toInt().coerceAtLeast(80)
        val out = Bitmap.createBitmap(plan.width, plan.height + legendH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(plan, 0f, 0f, null)

        val heat = Heatmap.render(project, metric, target, gridWidth = 400)
        canvas.drawBitmap(heat, null, Rect(0, 0, plan.width, plan.height), Paint(Paint.FILTER_BITMAP_FLAG))
        heat.recycle()

        val r = plan.width / 140f
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E5BD8.toInt() }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = r / 2
        }
        if (metric == Metric.PLANNED) {
            project.aps.forEach {
                canvas.drawCircle(it.x * plan.width, it.y * plan.height, r * 1.6f, dot)
                canvas.drawCircle(it.x * plan.width, it.y * plan.height, r * 1.6f, ring)
            }
        } else {
            project.points.forEach {
                canvas.drawCircle(it.x * plan.width, it.y * plan.height, r, dot)
                canvas.drawCircle(it.x * plan.width, it.y * plan.height, r, ring)
            }
        }

        // Legend strip
        val top = plan.height.toFloat()
        val pad = legendH * 0.18f
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202020.toInt(); textSize = legendH * 0.28f }
        canvas.drawText(title, pad, top + pad + text.textSize * 0.8f, text)
        val range = Heatmap.range(metric)
        val unit = Heatmap.unit(metric)
        val barLeft = plan.width * 0.45f
        val barRight = plan.width - pad
        val barTop = top + pad
        val barBottom = top + legendH * 0.5f
        val grad = Paint().apply {
            shader = LinearGradient(barLeft, 0f, barRight, 0f, Heatmap.legendColors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(RectF(barLeft, barTop, barRight, barBottom), grad)
        val small = Paint(text).apply { textSize = legendH * 0.22f }
        canvas.drawText("${range.start.toInt()} $unit", barLeft, top + legendH - pad, small)
        val maxLabel = "${range.endInclusive.toInt()} $unit"
        canvas.drawText(maxLabel, barRight - small.measureText(maxLabel), top + legendH - pad, small)
        plan.recycle()

        val file = File(dir, "${safe(project.name)}-${metric.name.lowercase()}.png")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out.recycle()
        return uri(file)
    }

    fun csv(project: Project): Uri {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder("point,x_m,y_m,time,fresh,bssid,ssid,rssi_dbm,frequency_mhz,channel,width_mhz\n")
        project.points.forEachIndexed { i, p ->
            val xm = "%.2f".format(Locale.US, p.x * project.widthMeters)
            val ym = "%.2f".format(Locale.US, p.y * project.heightMeters)
            for (r in p.readings) {
                sb.append(i + 1).append(',').append(xm).append(',').append(ym).append(',')
                    .append(fmt.format(Date(p.time))).append(',').append(p.fresh).append(',')
                    .append(r.bssid).append(',').append(quote(r.ssid)).append(',').append(r.rssi).append(',')
                    .append(r.frequency).append(',').append(WifiMath.channel(r.frequency)).append(',')
                    .append(r.widthMhz).append('\n')
            }
        }
        val file = File(dir, "${safe(project.name)}.csv")
        file.writeText(sb.toString())
        return uri(file)
    }

    fun share(uri: Uri, mime: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun uri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    private fun safe(s: String) = s.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").take(60).ifEmpty { "project" }
}
