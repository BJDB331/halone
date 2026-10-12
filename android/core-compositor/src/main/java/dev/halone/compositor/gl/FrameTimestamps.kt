package dev.halone.compositor.gl

import dev.halone.compositor.FrameTimes

/**
 * When a frame we swapped was actually latched and put on the display, from
 * `EGL_ANDROID_get_frame_timestamps`. Every call needs the GL thread with the
 * window surface current.
 */
object FrameTimestamps {

    private val loaded: Boolean = runCatching { System.loadLibrary("halone_timing") }.isSuccess

    private val scratch = LongArray(3)

    /** Per surface: call again whenever the EGL surface is recreated. */
    fun enable(): Boolean = loaded && nativeEnable()

    /** The id the next swap will carry, or null when timestamps are off. */
    fun nextFrameId(): Long? = if (loaded) nativeNextFrameId().takeIf { it >= 0 } else null

    /** Null once the frame has aged out of the driver's history. */
    fun query(frameId: Long): FrameTimes? {
        if (!loaded || !nativeQuery(frameId, scratch)) return null
        return FrameTimes(
            renderingCompleteNs = scratch[0],
            latchNs = scratch[1],
            presentNs = scratch[2],
        )
    }

    private external fun nativeEnable(): Boolean
    private external fun nativeNextFrameId(): Long
    private external fun nativeQuery(frameId: Long, out: LongArray): Boolean
}
