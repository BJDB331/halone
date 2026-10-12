package dev.halone.compositor.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Part of a framebuffer: what one eye sees, without the margin drawn around it for the lens warp. */
internal data class EyeRegion(val framebuffer: Int, val x: Int, val y: Int, val width: Int, val height: Int)

/** Reads an eye back for screenshots. Render thread. */
internal class EyeCopier {

    /** The region's pixels, RGBA, bottom row first. */
    fun read(region: EyeRegion): ByteBuffer {
        val pixels = ByteBuffer.allocateDirect(region.width * region.height * 4).order(ByteOrder.nativeOrder())
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, region.framebuffer)
        GLES20.glReadPixels(region.x, region.y, region.width, region.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        pixels.rewind()
        return pixels
    }
}
