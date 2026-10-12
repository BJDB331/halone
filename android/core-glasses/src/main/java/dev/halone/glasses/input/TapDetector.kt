package dev.halone.glasses.input

import dev.halone.glasses.api.TrackingSample
import kotlin.math.sqrt

enum class TapSide { LEFT, RIGHT }

data class Tap(val side: TapSide, val timestampNs: Long, val peakMps2: Float)

/**
 * A knock on the side of the frame, from the raw accelerometer.
 *
 * A tap is an impulse far sharper than anything a head does, so the signal is
 * the acceleration minus its own recent average. Measured on an Air 2 Pro, taps
 * peaked at 16–48 m/s² on that signal, and the nearest thing to one, adjusting
 * the glasses on the face, at 6. The gyro was measured too and separates them
 * worse, so it is not used.
 *
 * The side is the sign of X at the peak: a knock on the left pushes the frame
 * to the right, +X in the canonical frame.
 *
 * Runs on the driver's reader thread at ~1 kHz and allocates only when it
 * reports a tap.
 */
class TapDetector(
    private val thresholdMps2: Float = THRESHOLD_MPS2,
    private val refractoryNs: Long = REFRACTORY_NS,
) {
    private val history = FloatArray(AVERAGE_SAMPLES * 3)
    private val sums = FloatArray(3)
    private var filled = 0
    private var cursor = 0

    private var lastTapNs = Long.MIN_VALUE / 2
    private var peakSearch = 0
    private var peakMagnitude = 0f
    private var peakX = 0f
    private var onsetNs = 0L

    /** Returns a tap once its peak has been seen, a few samples after it began. */
    fun onSample(sample: TrackingSample.Inertial): Tap? {
        val ax = sample.accel.x
        val ay = sample.accel.y
        val az = sample.accel.z
        var tap: Tap? = null

        if (filled == AVERAGE_SAMPLES) {
            val hx = ax - sums[0] / AVERAGE_SAMPLES
            val hy = ay - sums[1] / AVERAGE_SAMPLES
            val hz = az - sums[2] / AVERAGE_SAMPLES
            val magnitude = sqrt(hx * hx + hy * hy + hz * hz)
            if (peakSearch > 0) {
                if (magnitude > peakMagnitude) {
                    peakMagnitude = magnitude
                    peakX = hx
                }
                if (--peakSearch == 0) {
                    tap = Tap(if (peakX > 0f) TapSide.LEFT else TapSide.RIGHT, onsetNs, peakMagnitude)
                }
            } else if (magnitude > thresholdMps2 && sample.timestampNs - lastTapNs >= refractoryNs) {
                lastTapNs = sample.timestampNs
                onsetNs = sample.timestampNs
                peakSearch = PEAK_SEARCH_SAMPLES
                peakMagnitude = magnitude
                peakX = hx
            }
        }

        val base = cursor * 3
        if (filled == AVERAGE_SAMPLES) {
            sums[0] -= history[base]
            sums[1] -= history[base + 1]
            sums[2] -= history[base + 2]
        } else {
            filled++
        }
        history[base] = ax
        history[base + 1] = ay
        history[base + 2] = az
        sums[0] += ax
        sums[1] += ay
        sums[2] += az
        cursor = (cursor + 1) % AVERAGE_SAMPLES
        return tap
    }

    companion object {
        /** Between the weakest tap measured (16) and the worst movement (6). */
        const val THRESHOLD_MPS2 = 10f

        /** A tap rings for 15–35 ms; the second of a double came 200 ms or more after the first. */
        const val REFRACTORY_NS = 100_000_000L

        /** 20 ms at the Air 2 Pro's 1 kHz. */
        private const val AVERAGE_SAMPLES = 20
        private const val PEAK_SEARCH_SAMPLES = 15
    }
}
