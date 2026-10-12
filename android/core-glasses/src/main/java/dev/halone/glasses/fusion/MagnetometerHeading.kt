package dev.halone.glasses.fusion

import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Uses the magnetometer to stop yaw from wandering.
 *
 * An accelerometer anchors pitch and roll because gravity is observable in
 * them, but rotation *about* gravity is not — so yaw is dead reckoning, and
 * whatever gyro bias survives estimation integrates into it forever. Recentring
 * hides that; it does not fix it. A magnetic field fixed in the world is the
 * only heading reference the hardware has.
 *
 * ### What this deliberately does not do
 *
 * It is **not a compass**. It never claims to know where north is, and it does
 * not need the magnetometer's scale, units, or absolute alignment — all three
 * are unknown on this hardware, and the blob stores no factory magnetometer
 * calibration at all. It learns which way the local field points *once*, and
 * then reports how far the orientation estimate has since rotated away from it.
 * Anything constant — hard iron, a wrong frame rotation, the field being
 * whatever a desk full of magnets makes it — is absorbed into that reference
 * rather than needing to be measured.
 *
 * The cost of that choice: it cannot correct an error that was already there
 * when the reference was learned, only drift accumulated afterwards. That is
 * the right trade, because the error that was already there gets cancelled by
 * recentring and the drift does not.
 *
 * ### Why it is so heavily gated
 *
 * A magnetometer next to an OLED panel, a DP bridge and a phone is a noisy
 * instrument, and a bad heading correction is far worse than none: it rotates
 * the whole world. So a sample is used only if the field strength matches what
 * has been seen before, the field is not close to vertical, and the implied
 * error is small. Persistent disagreement is treated as the environment having
 * changed rather than as the estimate being wrong, and re-anchors — which is
 * the same conclusion NRSDK reaches with its outlier counter and reset.
 */
class MagnetometerHeading {

    /**
     * How fast heading error is corrected, in units of error per second.
     *
     * Slow on purpose. This is fighting drift measured in hundredths of a
     * degree per second, so there is nothing to gain from converging quickly
     * and a great deal to lose: a visible yaw correction is indistinguishable
     * from the world sliding sideways on its own.
     */
    @Volatile
    var gainPerSecond: Float = DEFAULT_GAIN

    /** Hard cap on correction rate, rad/s. Below the threshold of noticing. */
    @Volatile
    var maxRateRadPerSecond: Float = DEFAULT_MAX_RATE

    /** Beyond this implied error the sample is an outlier, not a correction. */
    @Volatile
    var outlierThresholdRadians: Float = DEFAULT_OUTLIER_THRESHOLD

    @Volatile
    var enabled: Boolean = true

    /** Field direction the estimate is held against; null until anchored. */
    private var referenceHeading: Float? = null

    private var averageMagnitude = Float.NaN
    private var secondsDisagreeing = 0f

    /** Solves for the constant offset the hardware adds to every reading. */
    val hardIronEstimator = HardIronEstimator()

    private var appliedHardIron: Vec3? = null
    private var samplesSinceSolve = 0

    /** Diagnostics, read by the `magdrift` spike. */
    @Volatile
    var lastErrorRadians: Float = 0f
        private set

    @Volatile
    var corrections: Long = 0L
        private set

    @Volatile
    var rejections: Long = 0L
        private set

    /**
     * Rejections split by cause.
     *
     * Added because "every sample rejected" is not a diagnosis. One counter
     * said the gating was too tight and nothing more; which gate, and so which
     * assumption was wrong, needed this.
     */
    @Volatile
    var rejectedWeak: Long = 0L
        private set

    @Volatile
    var rejectedMagnitude: Long = 0L
        private set

    @Volatile
    var rejectedHorizontal: Long = 0L
        private set

    @Volatile
    var rejectedOutlier: Long = 0L
        private set

    @Volatile
    var referenceMagnitude: Float = Float.NaN
        private set

    @Volatile
    var lastMagnitude: Float = Float.NaN
        private set

    /**
     * The most recent sample's heading and whether it was used, sampled by the
     * `magprobe` spike at a few tens of Hz.
     *
     * Exposed rather than binned in here on purpose: a component that measures
     * itself grows a second implementation of the thing it is measuring. The
     * spike can do the statistics.
     */
    @Volatile
    var lastHeadingRadians: Float = Float.NaN
        private set

    @Volatile
    var lastAccepted: Boolean = false
        private set

    /**
     * Times the reference was discarded — now only by [reanchor] or by adopting
     * a hard-iron offset, never automatically on disagreement.
     */
    @Volatile
    var reanchors: Long = 0L
        private set

    /**
     * Episodes of sustained disagreement. Diagnostic only: high counts mean the
     * wearer is spending time at headings where the field and the estimate do
     * not agree, which is information about the environment rather than a
     * reason to change the reference.
     */
    @Volatile
    var sustainedDisagreements: Long = 0L
        private set

    val anchored: Boolean get() = referenceHeading != null

    /**
     * The offset currently being subtracted, or null while the fit is not yet
     * posed enough to publish one.
     *
     * A constant offset in the body frame is the one error a learned reference
     * cannot absorb, because it displaces the measured direction by a different
     * amount at every heading. Measured worn, that displacement swung 21.5°
     * across 45° of head rotation — larger than the whole drift this exists to
     * fix — so correcting it is not a refinement.
     */
    val hardIron: Vec3? get() = appliedHardIron

    /**
     * Whether to subtract the estimated hard-iron offset before using the field.
     *
     * **Off, because there is no hard-iron offset to find.** The estimator and
     * everything around it are kept because the reasoning is worth preserving,
     * but on this hardware the model is simply wrong.
     *
     * Both attempts made things worse — the min/max midpoint and then a proper
     * sphere fit — and offline analysis of a recorded session says why. Scoring
     * candidate offsets by how consistently the field's implied heading tracks
     * the tracker's yaw, the optimum sits *exactly on whatever bound it is
     * given*, at every bound:
     *
     * | bound | best `|c|` | heading spread |
     * |---|---|---|
     * | 100 | 100 | 13.2° |
     * | 393 (one field radius) | 393 | 6.3° |
     * | 1200 | 1200 | 2.8° |
     * | 4000 | 4000 | 1.5° |
     *
     * There is no interior optimum, because as the offset grows the corrected
     * vector tends to a constant in the body frame, which tracks yaw perfectly
     * while carrying no field information at all. A constant additive offset is
     * not identifiable here and does not describe the distortion.
     *
     * That also rules out the obvious next step: an EKF magnetic-bias state, as
     * NRSDK carries, is the *same model* estimated online, and inherits the
     * same non-identifiability. What did help was fixing the magnetometer's
     * frame — see `gyroToMag` in `XrealCalibrationParser`.
     */
    @Volatile
    var useHardIron: Boolean = false

    /** Forgets the reference so the next good sample re-learns it. */
    fun reanchor() {
        if (referenceHeading != null) reanchors++
        referenceHeading = null
        secondsDisagreeing = 0f
        averageMagnitude = Float.NaN
    }

    fun reset() {
        reanchor()
        averageMagnitude = Float.NaN
        hardIronEstimator.reset()
        appliedHardIron = null
        corrections = 0; rejections = 0
        reanchors = 0
        rejectedWeak = 0; rejectedMagnitude = 0; rejectedHorizontal = 0; rejectedOutlier = 0
        sustainedDisagreements = 0
        lastErrorRadians = 0f
    }

    /**
     * @param magnet raw magnetometer counts in the canonical frame
     * @param orientation the current estimate, canonical, +Y up
     * @return a yaw rotation to apply to the estimate, or null to leave it alone
     */
    fun update(magnet: Vec3, orientation: Quaternion, dt: Float): Quaternion? {
        if (!enabled || dt <= 0f) return null

        observe(magnet)
        val field = magnet - (if (useHardIron) appliedHardIron ?: Vec3.ZERO else Vec3.ZERO)
        val magnitude = field.length
        lastMagnitude = magnitude
        if (magnitude < MIN_FIELD_COUNTS) {
            rejections++; rejectedWeak++
            return null
        }

        if (averageMagnitude.isNaN()) averageMagnitude = magnitude
        referenceMagnitude = averageMagnitude

        // A field that has changed strength is a field that has changed — a
        // speaker, a laptop hinge, a passing lift — not a head that has turned.
        //
        // The average is updated from *every* sample that gets this far, not
        // only from ones that pass. Updating it only on success makes it a
        // latch that can only be released by the thing it is blocking: one
        // step change in the field, and the reference is stale forever and
        // every subsequent sample fails against it. That is not hypothetical —
        // it is how the first worn run rejected 151,495 samples in a row and
        // applied not one correction.
        val deviation = abs(magnitude - averageMagnitude)
        averageMagnitude += (magnitude - averageMagnitude) * MAGNITUDE_ALPHA
        if (deviation > averageMagnitude * MAGNITUDE_TOLERANCE) {
            rejections++; rejectedMagnitude++
            return null
        }

        val world = orientation.rotate(field)
        // Only the horizontal part carries heading. Near the magnetic poles, or
        // indoors near steel, it can nearly vanish, and its direction is then
        // noise amplified without limit.
        val horizontal = hypot(world.x, world.z)
        if (horizontal < magnitude * MIN_HORIZONTAL_FRACTION) {
            rejections++; rejectedHorizontal++
            return null
        }

        // Note the negated Z. A positive rotation about canonical +Y carries +X
        // toward −Z, so `atan2(z, x)` runs *backwards* against yaw and a
        // correction built on it drives the estimate further from the
        // reference instead of toward it — silently, and only while the
        // magnetometer is doing its job. Negating Z makes this a yaw angle in
        // the same sense as `Quaternion.fromYawRadians`.
        val heading = atan2(-world.z, world.x)
        lastHeadingRadians = heading
        val reference = referenceHeading ?: run {
            referenceHeading = heading
            return null
        }

        val error = wrapToPi(heading - reference)
        lastErrorRadians = error

        if (abs(error) > outlierThresholdRadians) {
            lastAccepted = false
            rejections++; rejectedOutlier++
            // Counted, never acted on. Re-anchoring here is the single worst
            // thing this class can do, and it took a ten-minute wear test to
            // see why.
            //
            // The reasoning for it was that sustained disagreement means the
            // world's field has changed. The reality is that our own
            // measurement carries ±20° of heading-dependent error, so facing
            // one direction for a few seconds is enough to exceed any sane
            // threshold with nothing whatever having changed. Re-anchoring then
            // adopts the drifted heading as truth — and because this is a
            // *controller*, it does not merely stop helping, it actively drives
            // the world to the new wrong target at the rate limit. Worn, that
            // is a panel-width jump over about a minute which then holds.
            //
            // Refusing to correct is a safe failure: the estimate falls back to
            // the gyro, and correction resumes by itself when the wearer turns
            // to a heading where the field agrees again. Adopting a bad
            // reference is not recoverable, because everything afterwards is
            // measured against it.
            secondsDisagreeing += dt
            if (secondsDisagreeing >= REANCHOR_AFTER_SECONDS) {
                secondsDisagreeing = 0f
                sustainedDisagreements++
            }
            return null
        }
        secondsDisagreeing = 0f
        lastAccepted = true

        val rate = (error * gainPerSecond).coerceIn(-maxRateRadPerSecond, maxRateRadPerSecond)
        corrections++
        // The world's field is fixed, so a heading that has moved away from the
        // reference is the estimate drifting, and the correction runs the other
        // way.
        return Quaternion.fromYawRadians(-rate * dt)
    }

    private fun observe(m: Vec3) {
        hardIronEstimator.add(m)
        if (!useHardIron) return
        // Solving is a 4x4 elimination, which is nothing next to a kilohertz of
        // samples but is not free either, and the answer moves slowly.
        if (++samplesSinceSolve < SOLVE_EVERY) return
        samplesSinceSolve = 0
        hardIronEstimator.solve()?.let { adopt(it) }
    }

    /**
     * Takes on a new offset, and forgets everything that was measured relative
     * to the old one.
     *
     * Both the reference heading and the magnitude average describe the
     * *corrected* field, so changing the correction invalidates both. Carrying
     * them across is not a small inaccuracy — it leaves a gate comparing new
     * readings against an average that no longer exists anywhere in the data.
     */
    private fun adopt(candidate: Vec3) {
        val current = appliedHardIron
        if (current != null && (candidate - current).length < HARD_IRON_STEP_COUNTS) return
        appliedHardIron = candidate
        if (referenceHeading != null) reanchors++
        referenceHeading = null
        averageMagnitude = Float.NaN
        secondsDisagreeing = 0f
    }

    private companion object {
        /** ~1.4°/s of error corrected per second of exposure: a ~20 s constant. */
        const val DEFAULT_GAIN = 0.05f

        /** ~0.29°/s. Slower than any drift worth fixing, faster than none. */
        const val DEFAULT_MAX_RATE = 0.005f

        /** ~17°. Larger than plausible drift between samples, smaller than a turn. */
        const val DEFAULT_OUTLIER_THRESHOLD = 0.3f

        /** How long disagreement must last before it is worth counting. */
        const val REANCHOR_AFTER_SECONDS = 5f
        const val MIN_FIELD_COUNTS = 20f
        const val MAGNITUDE_TOLERANCE = 0.35f
        const val MAGNITUDE_ALPHA = 0.001f
        const val MIN_HORIZONTAL_FRACTION = 0.15f
        /** Samples between solves; ~1 kHz in, so about twice a second. */
        const val SOLVE_EVERY = 512

        /** Below this the estimate has not really moved, so leave it alone. */
        const val HARD_IRON_STEP_COUNTS = 5f

        fun wrapToPi(a: Float): Float {
            var v = a
            while (v > PI) v -= (2 * PI).toFloat()
            while (v < -PI) v += (2 * PI).toFloat()
            return v
        }
    }
}
