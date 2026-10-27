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

    /** How much of the last depth to keep where a window's picture is unchanged, 0..0.9. */
    fun setSmoothing(amount: Float) = Unit

    /**
     * The least time between two estimates of one window. Short where the
     * model runs beside the GPU (an NPU), longer where it shares the GPU
     * with drawing and every estimate costs frames.
     */
    val intervalNs: Long get() = 200_000_000L
}

/** [size] squared texels, rows top down: 255 nearest, 128 at the window's own distance. */
class DepthMap(val size: Int, val texels: ByteArray)

/**
 * Turns a depth model's raw output into [DepthMap]s the renderer can use
 * comfortably, one window at a time: make one per converted window and feed
 * it that window's frames in order. Pure arithmetic, so it is tested off the
 * device.
 *
 * The steps follow StereoLab (github.com/boss720/3d, MIT), a desktop 2D-to-3D
 * converter built on the same model:
 *
 * - **Normalise** the model's relative inverse depth (bigger nearer, no fixed
 *   scale) to 0..1 between its 2nd and 98th percentiles, so one stray pixel
 *   does not flatten the rest. Where it sits against the window's own
 *   distance is the renderer's convergence, not decided here.
 * - **Refine** with a guided filter steered by the picture's brightness:
 *   depth snaps to the picture's edges and is smoothed where the picture is
 *   flat, instead of being blurred across both.
 * - **Stabilise** over time, per texel: history is kept where the picture has
 *   not changed and dropped where it has, so still parts stop shimmering and
 *   moving things leave no trail; a cut drops all of it.
 *
 * And one of Halone's own: a picture with little depth in it — a page of
 * text, a settings screen — leans on the gentle framing guess instead of
 * having its noise stretched to full depth, or going flat.
 */
class DepthStabiliser(
    /** How much of the last map is kept where the picture is unchanged, 0..0.9. */
    smoothing: Float = DEFAULT_SMOOTHING,
    private val size: Int = OUTPUT_SIZE,
) {
    @Volatile
    var smoothing: Float = smoothing.coerceIn(0f, 0.9f)
        set(value) { field = value.coerceIn(0f, 0.9f) }

    /** What the last frame found, for diagnostics. */
    @Volatile
    var lastRelief = 0f
        private set

    @Volatile
    var lastGain = 0f
        private set

    @Volatile
    var lastCut = false
        private set

    private var previousPicture: FloatArray? = null
    private var previousDepth: FloatArray? = null

    fun reset() {
        previousPicture = null
        previousDepth = null
    }

    /**
     * [raw] is [rawSize] squared model values, rows top down, bigger nearer;
     * [picture] is the same frame's brightness, 0..1, [pictureSize] squared.
     */
    fun next(raw: FloatArray, rawSize: Int, picture: FloatArray, pictureSize: Int): DepthMap {
        val guide = DepthPost.downsample(picture, pictureSize, size)
        val small = DepthPost.downsample(raw, rawSize, size)
        val sorted = small.copyOf().also { it.sort() }
        val low = sorted[(sorted.size * 0.02f).toInt()]
        val high = sorted[(sorted.size * 0.98f).toInt().coerceAtMost(sorted.size - 1)]
        val median = sorted[sorted.size / 2]
        val spread = high - low
        // How much depth the picture has, relative to its overall level:
        // near zero for a flat page, around one or more for a scene.
        val relief = if (median > 1e-6f) spread / median else if (spread > 1e-6f) 1f else 0f
        val gain = ((relief - RELIEF_FLAT) / (RELIEF_FULL - RELIEF_FLAT)).coerceIn(0f, 1f)
        lastRelief = relief
        lastGain = gain

        val scale = if (spread > 1e-9f) 1f / spread else 0f
        var depth = FloatArray(small.size) { ((small[it] - low) * scale).coerceIn(0f, 1f) }
        depth = DepthPost.guidedFilter(guide, depth, size, GUIDE_RADIUS, GUIDE_EPSILON)
        for (i in depth.indices) {
            val framing = DepthPost.framingDepth(((i % size) + 0.5f) / size, ((i / size) + 0.5f) / size)
            depth[i] = framing + (depth[i] - framing) * gain
        }

        val lastPicture = previousPicture
        val lastDepth = previousDepth
        lastCut = false
        if (smoothing > 0f && lastPicture != null && lastDepth != null) {
            val difference = FloatArray(guide.size) { abs(guide[it] - lastPicture[it]) }
            val mean = difference.average().toFloat()
            val changed = difference.count { it > CUT_PIXEL } / difference.size.toFloat()
            lastCut = mean > CUT_MEAN || changed > CUT_SHARE
            if (!lastCut) {
                val motion = DepthPost.dilate(difference, size)
                for (i in depth.indices) {
                    val m = motion[i] / MOTION_SCALE
                    val keep = smoothing * kotlin.math.exp(-m * m)
                    depth[i] = depth[i] * (1f - keep) + lastDepth[i] * keep
                }
            }
        }
        previousPicture = guide
        previousDepth = depth
        return DepthMap(size, ByteArray(depth.size) { (depth[it] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte() })
    }

    companion object {
        const val OUTPUT_SIZE = 128
        const val DEFAULT_SMOOTHING = 0.35f
        private const val RELIEF_FLAT = 0.1f
        private const val RELIEF_FULL = 0.35f
        // StereoLab's 9x9 window at full size; at 128 texels a little
        // further, since one texel here covers several of its pixels.
        private const val GUIDE_RADIUS = 2
        private const val GUIDE_EPSILON = 0.0025f
        // StereoLab's thresholds, on brightness 0..1.
        private const val CUT_MEAN = 0.24f
        private const val CUT_PIXEL = 0.18f
        private const val CUT_SHARE = 0.65f
        private const val MOTION_SCALE = 0.06f
    }
}

object DepthPost {
    const val OUTPUT_SIZE = DepthStabiliser.OUTPUT_SIZE

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

    /** Area average onto [outSize] squared. */
    fun downsample(raw: FloatArray, srcSize: Int, outSize: Int): FloatArray {
        if (srcSize == outSize) return raw.copyOf()
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

    /**
     * He et al.'s guided filter: [values] smoothed within windows of
     * (2 [radius] + 1) squared, but following edges in [guide]. [epsilon]
     * is how strong an edge must be to be kept.
     */
    fun guidedFilter(guide: FloatArray, values: FloatArray, size: Int, radius: Int, epsilon: Float): FloatArray {
        val meanI = boxMean(guide, size, radius)
        val meanP = boxMean(values, size, radius)
        val meanII = boxMean(FloatArray(guide.size) { guide[it] * guide[it] }, size, radius)
        val meanIP = boxMean(FloatArray(guide.size) { guide[it] * values[it] }, size, radius)
        val a = FloatArray(guide.size)
        val b = FloatArray(guide.size)
        for (i in guide.indices) {
            val variance = maxOf(meanII[i] - meanI[i] * meanI[i], 0f)
            a[i] = (meanIP[i] - meanI[i] * meanP[i]) / (variance + epsilon)
            b[i] = meanP[i] - a[i] * meanI[i]
        }
        val meanA = boxMean(a, size, radius)
        val meanB = boxMean(b, size, radius)
        return FloatArray(guide.size) { (meanA[it] * guide[it] + meanB[it]).coerceIn(0f, 1f) }
    }

    /** Mean over the (2 [radius] + 1) squared window, edges reflected. */
    fun boxMean(values: FloatArray, size: Int, radius: Int): FloatArray {
        val across = FloatArray(values.size)
        val n = 2 * radius + 1
        for (y in 0 until size) for (x in 0 until size) {
            var sum = 0f
            for (d in -radius..radius) sum += values[y * size + reflect(x + d, size)]
            across[y * size + x] = sum / n
        }
        val out = FloatArray(values.size)
        for (y in 0 until size) for (x in 0 until size) {
            var sum = 0f
            for (d in -radius..radius) sum += across[reflect(y + d, size) * size + x]
            out[y * size + x] = sum / n
        }
        return out
    }

    /** Each texel the largest within one texel: so a change also counts beside where it happened. */
    fun dilate(values: FloatArray, size: Int): FloatArray = FloatArray(values.size) { i ->
        val x = i % size
        val y = i / size
        var most = 0f
        for (dy in -1..1) for (dx in -1..1) {
            val sx = (x + dx).coerceIn(0, size - 1)
            val sy = (y + dy).coerceIn(0, size - 1)
            most = maxOf(most, values[sy * size + sx])
        }
        most
    }

    private fun reflect(i: Int, size: Int): Int = when {
        i < 0 -> -i - 1
        i >= size -> 2 * size - i - 1
        else -> i
    }.coerceIn(0, size - 1)
}
