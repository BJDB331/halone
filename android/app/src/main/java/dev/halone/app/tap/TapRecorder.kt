package dev.halone.app.tap

import dev.halone.glasses.api.TrackingSample
import java.io.File

/**
 * Raw inertial samples and cue marks, for finding out what a tap on the frame
 * looks like before deciding how to detect one.
 *
 * Preallocated: [record] runs on the driver's reader thread at ~1 kHz and must
 * not allocate. Host time is kept beside the device's own so the cues, which
 * only have host time, line up with the samples.
 */
class TapRecorder(private val capacity: Int) {
    private val hostNs = LongArray(capacity)
    private val deviceNs = LongArray(capacity)
    private val values = FloatArray(capacity * 6)

    @Volatile
    var count = 0
        private set

    private val marks = mutableListOf<Pair<Long, String>>()

    fun record(sample: TrackingSample.Inertial, receivedNs: Long) {
        val index = count
        if (index >= capacity) return
        hostNs[index] = receivedNs
        deviceNs[index] = sample.timestampNs
        val base = index * 6
        values[base] = sample.gyro.x
        values[base + 1] = sample.gyro.y
        values[base + 2] = sample.gyro.z
        values[base + 3] = sample.accel.x
        values[base + 4] = sample.accel.y
        values[base + 5] = sample.accel.z
        count = index + 1
    }

    @Synchronized
    fun mark(label: String, atNs: Long = System.nanoTime()) {
        marks += atNs to label
    }

    /** Writes `<name>-imu.csv` and `<name>-marks.csv` into [directory]. */
    @Synchronized
    fun write(directory: File, name: String): List<File> {
        val imu = File(directory, "$name-imu.csv")
        imu.bufferedWriter().use { out ->
            out.write("host_ns,device_ns,gx,gy,gz,ax,ay,az\n")
            for (i in 0 until count) {
                val base = i * 6
                out.write("${hostNs[i]},${deviceNs[i]}")
                for (j in 0 until 6) out.write(",${values[base + j]}")
                out.write("\n")
            }
        }
        val cues = File(directory, "$name-marks.csv")
        cues.bufferedWriter().use { out ->
            out.write("host_ns,label\n")
            marks.forEach { (at, label) -> out.write("$at,$label\n") }
        }
        return listOf(imu, cues)
    }
}
