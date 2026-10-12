package dev.halone.app.shell

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dev.halone.compositor.EyePicture
import dev.halone.compositor.StereoCompositor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Saves what the left eye sees to the gallery, in `Pictures/Halone`. */
class ScreenCapture(
    private val context: Context,
    private val compositor: () -> StereoCompositor?,
    private val notice: TransientNotice,
    private val log: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val saving = Executors.newSingleThreadExecutor()

    fun take(delayMs: Long = 0) {
        handler.postDelayed({
            val target = compositor() ?: return@postDelayed notice.show("No display to capture")
            target.captureLeftEye { picture -> saving.execute { save(picture) } }
        }, delayMs)
    }

    private fun save(picture: EyePicture) {
        val result = runCatching {
            val raw = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(picture.pixels)
            // GL rows run bottom up, and what the optics show over black is transparent.
            val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { preScale(1f, -1f) }, false)
            val opaque = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
            Canvas(opaque).apply {
                drawColor(Color.BLACK)
                drawBitmap(upright, 0f, 0f, null)
            }
            val name = "halone_${STAMP.format(Date())}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Halone")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("the gallery refused it")
            resolver.openOutputStream(uri)!!.use { opaque.compress(Bitmap.CompressFormat.PNG, 100, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            name
        }
        handler.post {
            result.onSuccess {
                log("  capture: saved $it")
                notice.show("Screenshot saved")
            }.onFailure {
                log("  capture: failed: $it")
                notice.show("Screenshot failed")
            }
        }
    }

    private companion object {
        val STAMP = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}
