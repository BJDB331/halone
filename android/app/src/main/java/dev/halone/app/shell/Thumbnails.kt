package dev.halone.app.shell

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.Size
import dev.halone.shell.FileTypes
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Small JPEGs of pictures, videos and songs for the Files grid, cached on
 * disk by path, size and modification time. `ThumbnailUtils` turns a photo
 * upright by its EXIF, reads the first picture of an MPO, takes a frame of a
 * video, and finds a song's cover art, failing when it has none. Covers are
 * larger, since the player shows them across its window. Any thread.
 */
class Thumbnails(context: Context) {
    private val cache = File(context.cacheDir, "thumbnails").apply { mkdirs() }

    fun of(file: File): ByteArray? {
        val cached = File(cache, key(file) + ".jpg")
        if (cached.isFile) return cached.readBytes()
        val type = FileTypes.mimeTypeOf(file.name).orEmpty()
        val bitmap = when {
            type.startsWith("video/") -> ThumbnailUtils.createVideoThumbnail(file, Size(SIZE_PX, SIZE_PX), null)
            type.startsWith("audio/") -> ThumbnailUtils.createAudioThumbnail(file, Size(COVER_PX, COVER_PX), null)
            else -> ThumbnailUtils.createImageThumbnail(file, Size(SIZE_PX, SIZE_PX), null)
        }
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        bitmap.recycle()
        val partial = File(cache, cached.name + ".part")
        partial.writeBytes(bytes)
        partial.renameTo(cached)
        return bytes
    }

    private fun key(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("${file.path}|${file.length()}|${file.lastModified()}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SIZE_PX = 320
        const val COVER_PX = 640
        const val QUALITY = 82
    }
}
