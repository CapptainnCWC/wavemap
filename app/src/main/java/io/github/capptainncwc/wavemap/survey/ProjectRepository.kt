package io.github.capptainncwc.wavemap.survey

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

/** Stores each project as projects/<id>/project.json plus the floor plan image. */
class ProjectRepository(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "projects").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(): List<Project> =
        root.listFiles().orEmpty().mapNotNull { load(it.name) }.sortedByDescending { it.created }

    fun load(id: String): Project? = runCatching {
        json.decodeFromString<Project>(File(root, "$id/project.json").readText())
    }.getOrNull()

    fun save(project: Project) {
        val dir = File(root, project.id).apply { mkdirs() }
        val tmp = File(dir, "project.json.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), project))
        if (!tmp.renameTo(File(dir, "project.json"))) throw IOException("Cannot save project")
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    fun planFile(project: Project) = File(root, "${project.id}/$PLAN_FILE")

    fun loadPlan(project: Project): Bitmap? = BitmapFactory.decodeFile(planFile(project).path)

    /** Creates a project from a floor plan image picked by the user. */
    fun createFromImage(name: String, uri: Uri, widthMeters: Float): Project {
        val bitmap = decodeScaled(uri) ?: throw IOException("Cannot read image")
        return createWith(name, bitmap, widthMeters)
    }

    /** Creates a project on a blank sheet with a one-meter grid. */
    fun createBlank(name: String, widthMeters: Float, heightMeters: Float): Project {
        val w = 1600
        val h = max(200, (w * heightMeters / widthMeters).roundToInt())
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val pxPerMeter = w / widthMeters
        val thin = Paint().apply { color = 0xFFE0E0E0.toInt(); strokeWidth = 2f }
        val thick = Paint().apply { color = 0xFFB0B0B0.toInt(); strokeWidth = 3f }
        var m = 0
        while (m * pxPerMeter <= w) {
            canvas.drawLine(m * pxPerMeter, 0f, m * pxPerMeter, h.toFloat(), if (m % 5 == 0) thick else thin)
            m++
        }
        m = 0
        while (m * pxPerMeter <= h) {
            canvas.drawLine(0f, m * pxPerMeter, w.toFloat(), m * pxPerMeter, if (m % 5 == 0) thick else thin)
            m++
        }
        return createWith(name, bitmap, widthMeters)
    }

    private fun createWith(name: String, bitmap: Bitmap, widthMeters: Float): Project {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            created = System.currentTimeMillis(),
            planWidth = bitmap.width,
            planHeight = bitmap.height,
            widthMeters = widthMeters,
        )
        val dir = File(root, project.id).apply { mkdirs() }
        FileOutputStream(File(dir, PLAN_FILE)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        save(project)
        return project
    }

    /** Decodes an image no larger than [MAX_PLAN_SIDE], honouring EXIF rotation on Android 9+. */
    private fun decodeScaled(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(app.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_PLAN_SIDE) {
                    val scale = MAX_PLAN_SIDE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_PLAN_SIDE * 2) sample *= 2
        val decoded = app.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val longest = max(decoded.width, decoded.height)
        if (longest <= MAX_PLAN_SIDE) return decoded
        val scale = MAX_PLAN_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true,
        ).also { if (it != decoded) decoded.recycle() }
    }

    private companion object {
        const val PLAN_FILE = "plan.jpg"
        const val MAX_PLAN_SIDE = 2048
    }
}
