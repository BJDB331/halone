package dev.halone.glasses.api

import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3

/**
 * One tracking reading from the hardware, already remapped into the canonical
 * frame described by [AxisConvention].
 *
 * The split matters: some glasses stream raw inertial data and expect the host
 * to fuse it (XREAL), while others run fusion on-device and hand over a
 * finished orientation (Rokid, VITURE). Modelling both lets the shared
 * pipeline own bias estimation, recentring and prediction for raw devices
 * without forcing a fake IMU stream onto devices that do their own fusion.
 */
sealed interface TrackingSample {
    val timestampNs: Long

    data class Inertial(
        override val timestampNs: Long,
        val gyro: Vec3,
        val accel: Vec3,
        /**
         * Magnetic field in physical units. Populated only when the device has
         * a magnetometer *and* we know how to scale it — on XREAL Air-series
         * hardware we do not yet, so this stays null and [magnetRaw] carries
         * the counts instead.
         */
        val magnet: Vec3? = null,
        /**
         * Magnetometer counts in the canonical frame: de-biased from the
         * sensor's 0x8000 offset and axis-corrected like the gyro and
         * accelerometer, but **unscaled** — "raw" here means the units are
         * counts, not that the frame is the hardware's.
         *
         * Separate from [magnet] on purpose: the scale convention is unknown
         * (see `docs/protocol/xreal-hid.md`), so these are good for asking
         * "is the sensor alive and does the field move when I do" and for
         * nothing else. Anything that needs a field strength must wait for the
         * calibration blob rather than reach for these.
         */
        val magnetRaw: Vec3? = null,
    ) : TrackingSample

    data class Oriented(
        override val timestampNs: Long,
        val orientation: Quaternion,
        /** Angular velocity, when the device reports it; enables prediction. */
        val angularVelocity: Vec3? = null,
    ) : TrackingSample
}

/** Where a driver pushes samples. Called on the driver's reader thread. */
fun interface TrackingSink {
    fun onSample(sample: TrackingSample)
}

/**
 * A device's tracking stream. Implementations must not allocate per sample in
 * the reader loop — at ~1kHz that is the difference between a quiet app and a
 * GC-thrashing one.
 */
interface TrackingSource {
    /** True when the device fuses on-device and emits [TrackingSample.Oriented]. */
    val providesOrientation: Boolean

    /** True when the device can also report translation (6DoF). Not yet consumed. */
    val providesPosition: Boolean

    fun start(sink: TrackingSink)
    fun stop()
}
