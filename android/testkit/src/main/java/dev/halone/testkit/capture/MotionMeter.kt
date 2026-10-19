package dev.halone.testkit.capture

import kotlin.math.sqrt

/**
 * Just enough motion sense to steer a script: how far the head turned and
 * whether it held still. Uses only the gyro's magnitude, so it works before
 * anyone knows the device's axis convention — finding that is what the
 * recording is for.
 */
class MotionMeter {
    private val lock = Any()
    private var lastTimestampNs = 0L
    private var turnedRadians = 0.0
    private var peakRadPerSec = 0.0
    private var samples = 0L

    data class Reading(val turnedDegrees: Double, val peakDegreesPerSec: Double, val samples: Long)

    fun reset() = synchronized(lock) {
        lastTimestampNs = 0L
        turnedRadians = 0.0
        peakRadPerSec = 0.0
        samples = 0
    }

    fun onGyro(timestampNs: Long, x: Float, y: Float, z: Float) = synchronized(lock) {
        val rate = sqrt((x * x + y * y + z * z).toDouble())
        if (lastTimestampNs != 0L) {
            val dt = (timestampNs - lastTimestampNs) / 1e9
            if (dt > 0 && dt < MAX_GAP_SECONDS) turnedRadians += rate * dt
        }
        lastTimestampNs = timestampNs
        if (rate > peakRadPerSec) peakRadPerSec = rate
        samples++
    }

    fun read(): Reading = synchronized(lock) {
        Reading(Math.toDegrees(turnedRadians), Math.toDegrees(peakRadPerSec), samples)
    }

    private companion object {
        const val MAX_GAP_SECONDS = 0.1
    }
}
