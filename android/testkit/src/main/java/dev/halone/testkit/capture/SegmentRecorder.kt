package dev.halone.testkit.capture

fun interface ImuListener {
    fun onImu(timestampNs: Long, gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float)
}

/** Raw IMU samples in the hardware's own frame, kept per labelled segment for analysis on the phone. */
class SegmentRecorder : ImuListener {

    class Samples(val timestampsNs: LongArray, val gyro: FloatArray, val accel: FloatArray) {
        val size: Int get() = timestampsNs.size
    }

    private val lock = Any()
    private var label: String? = null
    private var times = LongArray(INITIAL)
    private var gyro = FloatArray(INITIAL * 3)
    private var accel = FloatArray(INITIAL * 3)
    private var count = 0
    private val finished = mutableMapOf<String, Samples>()

    /** A later segment with the same label replaces the earlier one, so a retried motion keeps its last attempt. */
    fun begin(label: String) = synchronized(lock) {
        this.label = label
        count = 0
    }

    fun end() = synchronized(lock) {
        val current = label ?: return@synchronized
        finished[current] = Samples(times.copyOf(count), gyro.copyOf(count * 3), accel.copyOf(count * 3))
        label = null
    }

    fun samples(label: String): Samples? = synchronized(lock) { finished[label] }

    override fun onImu(timestampNs: Long, gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float) {
        synchronized(lock) {
            if (label == null) return
            if (count == times.size) {
                times = times.copyOf(count * 2)
                gyro = gyro.copyOf(count * 6)
                accel = accel.copyOf(count * 6)
            }
            times[count] = timestampNs
            gyro[count * 3] = gx
            gyro[count * 3 + 1] = gy
            gyro[count * 3 + 2] = gz
            accel[count * 3] = ax
            accel[count * 3 + 1] = ay
            accel[count * 3 + 2] = az
            count++
        }
    }

    private companion object {
        const val INITIAL = 8 * 1024
    }
}
