package dev.halone.glasses.fusion

import dev.halone.glasses.api.TrackingSample
import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import kotlin.math.sqrt

/**
 * Complementary filter: gyro integration corrected toward the accelerometer's
 * gravity direction, and — when the hardware has one — held against the
 * magnetic field in yaw.
 *
 * Gravity can only anchor pitch and roll; rotation about it is unobservable, so
 * yaw was dead reckoning and drifted at whatever the residual gyro bias
 * integrated to. [magnetometer] is the heading reference that closes that loop.
 * It is heavily gated and deliberately slow — read its own documentation before
 * changing anything about it, because a bad heading correction rotates the
 * entire world rather than tilting it slightly.
 */
class OrientationFilter(accelWeight: Float = DEFAULT_ACCEL_WEIGHT) {

    /**
     * How hard the accelerometer pulls the estimate toward level, per sample.
     *
     * This governs *visibility*, not accuracy. Gravity only has to cancel slow
     * drift — measured at 0.05 deg per 30 s while stationary — so there is no
     * benefit to correcting quickly, and a real cost: while the wearer is
     * turning the accelerometer is distrusted, so error accumulates unopposed,
     * and a fast correction then dumps it all the moment they stop. That reads
     * as the world snapping into place a beat after the head settles.
     *
     * Slow enough and the same correction is imperceptible. At ~1 kHz a weight
     * of 0.0005 gives a time constant of roughly two seconds.
     */
    @Volatile
    var accelWeight: Float = accelWeight

    var orientation: Quaternion = Quaternion.IDENTITY
        private set

    var gyroBias: Vec3 = Vec3.ZERO

    /**
     * Factory accelerometer bias to subtract, canonical frame.
     *
     * Unlike the gyro's, this one is genuinely constant — the vendor measures
     * it once and stores it, `scale_accel` is identity, and nothing about it
     * moves with temperature the way gyro bias does. Left at zero the
     * accelerometer reads a couple of percent off, which matters less for
     * levelling (only the direction is used) than for anything that checks its
     * magnitude, such as stillness detection.
     */
    var accelBias: Vec3 = Vec3.ZERO

    /**
     * Rotation from the magnetometer's own frame into the body frame.
     *
     * From the calibration blob's `gyro_q_mag`. Identity is a safe default:
     * [MagnetometerHeading] learns its reference in whatever frame it is handed,
     * so a missing or even wrong constant rotation is absorbed rather than
     * producing a wrong heading — it costs accuracy when the head is tilted and
     * turned at once, not correctness.
     */
    var magnetToBody: Quaternion = Quaternion.IDENTITY

    /** Yaw reference; see [MagnetometerHeading]. */
    val magnetometer = MagnetometerHeading()

    private var lastTimestampNs: Long = 0

    /**
     * Vector from the wearer's neck pivot to the sensor, in the canonical body
     * frame; null disables compensation.
     *
     * The alternative to distrusting the accelerometer during a turn is to
     * predict what the turn is doing to it and subtract that. The head rotates
     * about the neck, not about the sensor, so the sensor is swung on a radius
     * and feels centripetal acceleration `w x (w x r)` plus tangential `a x r`.
     * Both are computable from the gyro and a single geometric constant, so the
     * accelerometer can stay usable through the turn instead of being ignored.
     *
     * Default is roughly 8 cm up and 10 cm forward of the pivot — the eyes
     * relative to the base of the neck. Canonical +Y is up and +Z points back
     * toward the wearer, so forward is negative Z.
     */
    @Volatile
    var pivotOffset: Vec3? = DEFAULT_PIVOT_OFFSET

    /**
     * Rotation rate above which the accelerometer is ignored (rad/s).
     *
     * With rotation compensation on this can be raised a long way, since the
     * contamination it was defending against is being removed instead.
     */
    @Volatile
    var trustNoneAbove: Float = DEFAULT_TRUST_NONE_ABOVE

    private var previousGyro: Vec3? = null
    private var angularAcceleration: Vec3 = Vec3.ZERO

    fun reset() {
        orientation = Quaternion.IDENTITY
        lastTimestampNs = 0
    }

    fun update(sample: TrackingSample.Inertial) {
        val dt = if (lastTimestampNs == 0L) {
            0f
        } else {
            (sample.timestampNs - lastTimestampNs).coerceAtLeast(0L) / 1_000_000_000f
        }
        lastTimestampNs = sample.timestampNs
        if (dt <= 0f || dt > MAX_PLAUSIBLE_GAP_SECONDS) return

        val gx = sample.gyro.x - gyroBias.x
        val gy = sample.gyro.y - gyroBias.y
        val gz = sample.gyro.z - gyroBias.z

        val half = dt * 0.5f
        var q = (orientation * Quaternion(1f, gx * half, gy * half, gz * half)).normalized()

        val accel = sample.accel - accelBias
        val gravity = compensateForRotation(accel, Vec3(gx, gy, gz), dt)
        if (accelWeight > 0f) {
            q = correctWithGravity(q, gravity, rotationTaper(sample.gyro))
        }

        sample.magnetRaw?.let { counts ->
            // Pre-multiplied: the correction is a rotation about the *world* up
            // axis, not about the body's. Post-multiplying would yaw the head
            // in its own frame, which is a different rotation entirely whenever
            // the wearer is not perfectly level.
            magnetometer.update(magnetToBody.rotate(counts), q, dt)
                ?.let { correction -> q = (correction * q).normalized() }
        }

        orientation = q
    }

    /**
     * Removes the acceleration the rotation itself is producing, leaving
     * something closer to pure gravity.
     *
     * Angular acceleration has to be differentiated from the gyro, which
     * amplifies noise, so it is low-passed. The centripetal term needs no
     * differentiation and dominates during a sustained turn; the tangential term
     * matters at the start and end of one.
     */
    private fun compensateForRotation(accel: Vec3, gyro: Vec3, dt: Float): Vec3 {
        val r = pivotOffset ?: return accel

        val previous = previousGyro
        previousGyro = gyro
        if (previous != null && dt > 0f) {
            val raw = Vec3((gyro.x - previous.x) / dt, (gyro.y - previous.y) / dt, (gyro.z - previous.z) / dt)
            angularAcceleration = Vec3(
                angularAcceleration.x + (raw.x - angularAcceleration.x) * ALPHA_SMOOTHING,
                angularAcceleration.y + (raw.y - angularAcceleration.y) * ALPHA_SMOOTHING,
                angularAcceleration.z + (raw.z - angularAcceleration.z) * ALPHA_SMOOTHING,
            )
        }

        val centripetal = gyro cross (gyro cross r)
        val tangential = angularAcceleration cross r
        return accel - centripetal - tangential
    }

    /**
     * How much to trust the accelerometer as a gravity reference, given how fast
     * the device is turning.
     *
     * A head turn swings the glasses about the wearer's neck, so they feel
     * centripetal and tangential acceleration on top of gravity — roughly
     * 1.5 m/s² sideways at 200°/s on a 12 cm radius. That is only a few percent
     * of gravity in magnitude, so a magnitude check waves it through, but it
     * tilts the *direction* by several degrees. The filter then dutifully rolls
     * the horizon toward it, and rolls back when the motion stops: a yaw turn
     * shows up as roll that springs back, which is exactly what it looks like
     * on the glasses.
     *
     * The gyro is entirely reliable over the fraction of a second a turn lasts,
     * so the fix is to stop believing the accelerometer while turning. Tapered
     * rather than switched, so the horizon does not visibly snap as the
     * threshold is crossed.
     */
    private fun rotationTaper(gyro: Vec3): Float {
        val rate = gyro.length
        if (rate <= TRUST_FULLY_BELOW) return 1f
        if (rate >= trustNoneAbove) return 0f
        return 1f - (rate - TRUST_FULLY_BELOW) / (trustNoneAbove - TRUST_FULLY_BELOW)
    }

    private fun correctWithGravity(q: Quaternion, accel: Vec3, taper: Float): Quaternion {
        if (taper <= 0f) return q
        val magnitude = accel.length
        if (magnitude < MIN_GRAVITY || magnitude > MAX_GRAVITY) return q

        val ax = accel.x / magnitude
        val ay = accel.y / magnitude
        val az = accel.z / magnitude

        // Where the estimate thinks "up" (world +Y) points, expressed in the
        // body frame: the second row of the rotation matrix. This must track the
        // canonical frame — a Z-up formulation here silently drives the estimate
        // to a 90 degree error even when the device is perfectly still.
        val vx = 2f * (q.x * q.y + q.w * q.z)
        val vy = 1f - 2f * (q.x * q.x + q.z * q.z)
        val vz = 2f * (q.y * q.z - q.w * q.x)

        val ex = ay * vz - az * vy
        val ey = az * vx - ax * vz
        val ez = ax * vy - ay * vx

        val weight = accelWeight * taper
        return (q * Quaternion(1f, ex * weight, ey * weight, ez * weight)).normalized()
    }

    private companion object {
        /** Longer than this and we have missed samples; integrating would fling the view. */
        const val MAX_PLAUSIBLE_GAP_SECONDS = 0.5f

        /** Slow enough that the correction cannot be seen; see [accelWeight]. */
        const val DEFAULT_ACCEL_WEIGHT = 0.0005f

        /** Accept only readings plausibly dominated by gravity, in m/s². */
        const val MIN_GRAVITY = 5f
        const val MAX_GRAVITY = 15f

        /** Below this rotation rate the accelerometer is fully trusted (rad/s, ~11°/s). */
        const val TRUST_FULLY_BELOW = 0.2f

        /** Smoothing for the differentiated angular acceleration. */
        const val ALPHA_SMOOTHING = 0.02f

        /**
         * Roughly the eyes relative to the base of the neck: 8 cm up, 10 cm
         * forward. This is anthropometry, not a device property, so it belongs
         * in per-wearer settings eventually rather than baked in here.
         */
        val DEFAULT_PIVOT_OFFSET = Vec3(0f, 0.08f, -0.10f)

        /**
         * ~172°/s. Compensation handles ordinary turns, so gravity stays usable
         * far longer than the original 57°/s; the cap remains as a backstop for
         * violent motion, where the compensation's single geometric constant is
         * least likely to hold.
         */
        const val DEFAULT_TRUST_NONE_ABOVE = 3.0f
    }
}

/**
 * Estimates gyro bias from samples taken while the glasses are known to be
 * still. Not an optimisation: the measured yaw bias on an Air 2 Pro is
 * 0.47°/s, which is ~28° of drift per minute if left uncorrected.
 */
class GyroBiasEstimator(private val target: Int = 300) {
    private var count = 0
    private var sx = 0.0
    private var sy = 0.0
    private var sz = 0.0

    val progress: Float get() = if (target == 0) 1f else count.toFloat() / target
    val done: Boolean get() = count >= target

    fun reset() {
        count = 0; sx = 0.0; sy = 0.0; sz = 0.0
    }

    fun add(sample: TrackingSample.Inertial) {
        if (done) return
        sx += sample.gyro.x
        sy += sample.gyro.y
        sz += sample.gyro.z
        count++
    }

    fun bias(): Vec3 =
        if (count == 0) Vec3.ZERO
        else Vec3((sx / count).toFloat(), (sy / count).toFloat(), (sz / count).toFloat())
}

/**
 * Rejects a bias estimate if the device was actually moving.
 *
 * The threshold matters more than it looks. Set it loosely and a slow sustained
 * turn — well under what feels like "moving" to a wearer — gets absorbed as
 * bias, and the world then drifts the other way once they stop. Set it too
 * tightly and a worn device never qualifies as still, so bias is never
 * refreshed. 1.1 deg/s is comfortably above sensor noise and below any
 * deliberate head movement.
 *
 * The accelerometer is checked too: a device being carried can hold a steady
 * rotation rate while plainly not being still.
 */
class StillnessDetector(
    private val gyroThreshold: Float = 0.02f,
    private val accelDeviationThreshold: Float = 0.35f,
    private val requiredSamples: Int = 200,
) {
    private var consecutive = 0

    /**
     * This device's accelerometer magnitude at rest, learned rather than
     * assumed.
     *
     * Comparing against a nominal 9.81 conflates two different things: real
     * acceleration, which *changes* the magnitude, and a calibration scale
     * error, which offsets it constantly. The Air 2 Pro reads 10.14 m/s^2 lying
     * still on a table — 3.4% high, so a fixed 0.35 tolerance around 9.81 sits
     * right on the limit and sensor noise trips it. Stillness was then never
     * detected on that hardware, so gyro bias was never calibrated and yaw drift
     * ran untreated at tens of degrees per minute. Nothing failed loudly; the
     * horizon just wandered.
     */
    private var restingMagnitude = Float.NaN

    val isStill: Boolean get() = consecutive >= requiredSamples

    fun add(sample: TrackingSample.Inertial) {
        val magnitude = sample.accel.length
        if (restingMagnitude.isNaN()) restingMagnitude = magnitude

        val rotating = sample.gyro.length > gyroThreshold
        val accelerating =
            kotlin.math.abs(magnitude - restingMagnitude) > accelDeviationThreshold
        // A scale error is a few percent; free fall or a hard shake is not. This
        // keeps the learned reference from anchoring to something absurd.
        val plausible = magnitude > EARTH_GRAVITY * MIN_PLAUSIBLE_SCALE &&
            magnitude < EARTH_GRAVITY * MAX_PLAUSIBLE_SCALE

        consecutive = if (rotating || accelerating || !plausible) 0 else consecutive + 1

        // Refined only while still, so sustained real acceleration cannot drag
        // the reference along behind it.
        if (consecutive > 0) {
            restingMagnitude += (magnitude - restingMagnitude) * RESTING_ALPHA
        }
    }

    fun reset() {
        consecutive = 0
    }

    private companion object {
        const val EARTH_GRAVITY = 9.81f

        /** A calibration scale error lives in here; free fall does not. */
        const val MIN_PLAUSIBLE_SCALE = 0.7f
        const val MAX_PLAUSIBLE_SCALE = 1.3f

        /** Per-sample blend; at ~1kHz, a time constant of about a second. */
        const val RESTING_ALPHA = 0.001f
    }
}
