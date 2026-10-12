package dev.halone.compositor

import android.view.Surface
import java.nio.ByteBuffer

/** One eye's picture: RGBA, bottom row first, as GL reads it. */
class EyePicture(val width: Int, val height: Int, val pixels: ByteBuffer)

/**
 * Where the compositor copies the left eye every frame while recording: an
 * encoder's input surface, at most [maxFramesPerSecond] times a second.
 */
class RecordingTarget(val surface: Surface, val width: Int, val height: Int, val maxFramesPerSecond: Int = 30) {
    private val released = java.util.concurrent.CountDownLatch(1)

    internal fun release() = released.countDown()

    /** Waits until the compositor has stopped drawing into [surface], so the encoder can be let go. */
    fun awaitReleased(timeoutMs: Long): Boolean = released.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
}
