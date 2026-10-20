package dev.halone.compositor

import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Estimates depth from a window's picture, for 2D-to-3D conversion. The
 * compositor hands it a frame now and then; the work happens off the render
 * thread, and the answer comes back as a [DepthMap] whenever it is ready.
 */
interface DepthEstimator {
    /** Side of the square input picture, in pixels. */
    val inputSize: Int

    /** True when it can take a picture now: loaded, and not busy with the last one. */
    fun ready(): Boolean

    /**
     * Estimates [panelId]'s depth from [rgba], [inputSize] squared pixels of
     * RGBA, rows top down. The buffer is the estimator's until [done] runs,
     * which may be on any thread.
     */
    fun estimate(panelId: String, rgba: ByteBuffer, done: (DepthMap) -> Unit)
}

/** [size] squared texels, rows top down: 255 nearest, 128 at the window's own distance. */
class DepthMap(val size: Int, val texels: ByteArray)

/**
 * Turns a depth model's raw output into a [DepthMap] the renderer can use
 * comfortably. Pure arithmetic, so it is tested off the device.
 *
 * Models like Depth Anything give relative inverse depth — bigger is nearer,
 * with no fixed scale — so it is normalised per frame around the median,
 * which keeps the window's own distance where it was. Two things keep it
 * comfortable rather than literal: the map is blurred, because the renderer
 * shifts whole regions and a sharp depth edge through text would split
 * letters; and a picture with little depth in it (a page of text, a settings
 * screen) leans on the gentle framing guess instead, rather than having its
 * noise stretched to full depth — or going flat, which read as the
 * conversion switching itself off.
 */
object DepthPost {
    /** What the last [normalise] found, for diagnostics. */
    @Volatile
    var lastRelief = 0f
        private set

    @Volatile
    var lastGain = 0f
        private set

    /**
     * The guess that needs no look at the picture, at ([u], [v]) across the
     * window, 0..1 with v down: a dome over the middle plus a ramp toward the
     * bottom, averaging the window's own distance.
     */
    fun framingDepth(u: Float, v: Float): Float {
        val dx = u - 0.5f
        val dy = v - 0.5f
        val dome = 1f - (dx * dx + dy * dy) * 2f
        val ramp = v - 0.5f
        return (0.5f + 0.35f * (dome - 2f / 3f) + 0.25f * ramp).coerceIn(0f, 1f)
    }

    const val OUTPUT_SIZE = 128

    /** Of the new frame, against the last; the rest is the last, so depth does not flicker. */
    const val BLEND_NEW = 0.4f

    /** Mean change, 0..1, above which a frame is a new scene and replaces the last outright. */
    const val CUT_THRESHOLD = 0.18f

    /**
     * [raw] is [srcSize] squared values, rows top down, bigger nearer;
     * [previous] is the last map for the same window, or null.
     */
    fun normalise(raw: FloatArray, srcSize: Int, previous: ByteArray? = null, outSize: Int = OUTPUT_SIZE): ByteArray {
        val small = downsample(raw, srcSize, outSize)
        val sorted = small.copyOf().also { it.sort() }
        val low = sorted[(sorted.size * 0.02f).toInt()]
        val high = sorted[(sorted.size * 0.98f).toInt().coerceAtMost(sorted.size - 1)]
        val median = sorted[sorted.size / 2]
        val spread = high - low
        // How much depth the picture has, relative to its overall level:
        // near zero for a flat page, around one or more for a scene.
        val relief = if (median > 1e-6f) spread / median else if (spread > 1e-6f) 1f else 0f
        val gain = ((relief - RELIEF_FLAT) / (RELIEF_FULL - RELIEF_FLAT)).coerceIn(0f, 1f)
        val scale = if (spread > 1e-9f) 1f / spread else 0f
        lastRelief = relief
        lastGain = gain

        var depth = FloatArray(small.size) {
            val estimated = (0.5f + (small[it] - median) * scale).coerceIn(0f, 1f)
            val framing = framingDepth(((it % outSize) + 0.5f) / outSize, ((it / outSize) + 0.5f) / outSize)
            framing + (estimated - framing) * gain
        }
        repeat(BLUR_PASSES) { depth = boxBlur(depth, outSize) }

        if (previous != null && previous.size == depth.size) {
            var change = 0f
            for (i in depth.indices) change += abs(depth[i] - (previous[i].toInt() and 0xFF) / 255f)
            if (change / depth.size < CUT_THRESHOLD) {
                for (i in depth.indices) {
                    depth[i] = (previous[i].toInt() and 0xFF) / 255f * (1f - BLEND_NEW) + depth[i] * BLEND_NEW
                }
            }
        }
        return ByteArray(depth.size) { (depth[it] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte() }
    }

    private const val RELIEF_FLAT = 0.1f
    private const val RELIEF_FULL = 0.6f
    private const val BLUR_PASSES = 2

    /** Area average onto [outSize] squared. */
    internal fun downsample(raw: FloatArray, srcSize: Int, outSize: Int): FloatArray {
        val out = FloatArray(outSize * outSize)
        for (oy in 0 until outSize) {
            val y0 = oy * srcSize / outSize
            val y1 = maxOf(y0 + 1, (oy + 1) * srcSize / outSize)
            for (ox in 0 until outSize) {
                val x0 = ox * srcSize / outSize
                val x1 = maxOf(x0 + 1, (ox + 1) * srcSize / outSize)
                var sum = 0f
                for (y in y0 until y1) for (x in x0 until x1) sum += raw[y * srcSize + x]
                out[oy * outSize + ox] = sum / ((y1 - y0) * (x1 - x0))
            }
        }
        return out
    }

    /** 3x3, edges clamped. */
    private fun boxBlur(values: FloatArray, size: Int): FloatArray {
        val out = FloatArray(values.size)
        for (y in 0 until size) for (x in 0 until size) {
            var sum = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val sy = (y + dy).coerceIn(0, size - 1)
                val sx = (x + dx).coerceIn(0, size - 1)
                sum += values[sy * size + sx]
            }
            out[y * size + x] = sum / 9f
        }
        return out
    }
}
