package dev.halone.compositor.gl

import android.opengl.GLES20
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Depth maps for 2D-to-3D conversion, as `GL_TEXTURE_2D`s of one byte per
 * texel: 255 nearest, 128 at the window's own distance, 0 farthest. Rows run
 * top down, as window pixels do. Render thread only.
 *
 * Until a window's own estimate arrives it is drawn with a guess that needs no look at the picture: the
 * middle of a window nearer than its edges, and its bottom nearer than its
 * top, the way most photos and scenes are framed. It is smooth, so text stays
 * whole and readable — the window reads as gently shaped rather than as a
 * scene with objects in it. A map estimated from each window's own picture
 * goes in the same slot later.
 */
internal class DepthMaps {

    var framing = 0
        private set

    fun initialise() {
        val size = SIZE
        val texels = ByteBuffer.allocateDirect(size * size).order(ByteOrder.nativeOrder())
        for (row in 0 until size) for (column in 0 until size) {
            texels.put((framingDepth((column + 0.5f) / size, (row + 0.5f) / size) * 255f).roundToInt().coerceIn(0, 255).toByte())
        }
        texels.position(0)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_R8, size, size, 0, GLES30.GL_RED, GLES20.GL_UNSIGNED_BYTE, texels)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        framing = ids[0]
    }

    /** Each converted window's estimated map, once one has arrived. */
    private val estimated = HashMap<String, Int>()

    /** The map to draw [panelId] with: its estimate, or the framing guess until there is one. */
    fun forPanel(panelId: String): Int = estimated[panelId] ?: framing

    /** Uploads [texels] ([size] squared, rows top down) as [panelId]'s map. */
    fun upload(panelId: String, size: Int, texels: ByteArray) {
        val texture = estimated.getOrPut(panelId) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            ids[0]
        }
        val buffer = ByteBuffer.allocateDirect(texels.size).order(ByteOrder.nativeOrder()).put(texels)
        buffer.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_R8, size, size, 0, GLES30.GL_RED, GLES20.GL_UNSIGNED_BYTE, buffer)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
    }

    /** Frees the maps of windows not in [keep]. */
    fun retain(keep: Set<String>) {
        val gone = estimated.keys.filter { it !in keep }
        if (gone.isEmpty()) return
        GLES20.glDeleteTextures(gone.size, gone.map { estimated.remove(it)!! }.toIntArray(), 0)
    }

    companion object {
        const val SIZE = 64

        /**
         * The framing guess at ([u], [v]) across the window, 0..1 with v down:
         * a dome over the middle plus a ramp toward the bottom, averaging the
         * window's own distance so that turning it on does not move the window.
         */
        fun framingDepth(u: Float, v: Float): Float {
            val dx = u - 0.5f
            val dy = v - 0.5f
            val dome = 1f - (dx * dx + dy * dy) * 2f // 1 at the middle, 0 at the corners
            val ramp = v - 0.5f // -0.5 at the top, 0.5 at the bottom
            return (0.5f + 0.35f * (dome - 2f / 3f) + 0.25f * ramp).coerceIn(0f, 1f)
        }
    }
}
