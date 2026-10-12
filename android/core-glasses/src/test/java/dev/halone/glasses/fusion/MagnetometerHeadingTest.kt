package dev.halone.glasses.fusion

import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MagnetometerHeadingTest {

    /** A plausible indoor field: mostly horizontal, some dip. */
    private val worldField = Vec3(300f, -150f, 0f)

    private val dt = 0.001f

    /** What the sensor reads when the body is at [orientation]. */
    private fun measure(orientation: Quaternion) =
        orientation.conjugate().rotate(worldField)

    private fun yaw(degrees: Float) =
        Quaternion.fromYawRadians(Math.toRadians(degrees.toDouble()).toFloat())

    private fun settle(h: MagnetometerHeading, at: Quaternion, samples: Int = 5) {
        repeat(samples) { h.update(measure(at), at, dt) }
    }

    @Test
    fun `the first usable sample anchors instead of correcting`() {
        val h = MagnetometerHeading()
        assertNull(h.update(measure(Quaternion.IDENTITY), Quaternion.IDENTITY, dt))
        assertTrue(h.anchored)
    }

    /**
     * The core behaviour. With the head truly still, an estimate that has
     * drifted in yaw must be pulled back — and in the opposite direction to the
     * drift, which is the one thing that would silently make drift worse if
     * inverted.
     */
    @Test
    fun `a drifted estimate is corrected back toward the reference`() {
        val h = MagnetometerHeading()
        settle(h, Quaternion.IDENTITY)

        // The body never moved, so the sensor still reads the same thing; only
        // our estimate of where the body is has drifted.
        val drifted = yaw(5f)
        val correction = h.update(measure(Quaternion.IDENTITY), drifted, dt)

        assertNotNull("a small, plausible error must produce a correction", correction)
        val corrected = (correction!! * drifted).normalized()
        val before = abs(yawDegrees(drifted))
        val after = abs(yawDegrees(corrected))
        assertTrue("error $before° should shrink, got $after°", after < before)
    }

    @Test
    fun `correction is rate limited so it cannot be seen`() {
        val h = MagnetometerHeading()
        settle(h, Quaternion.IDENTITY)

        val drifted = yaw(10f)
        val correction = h.update(measure(Quaternion.IDENTITY), drifted, 1f)!!
        val stepDegrees = abs(yawDegrees(correction))
        // One second at the default cap, which is deliberately well under the
        // ~1°/s at which a yaw correction starts being visible as the world
        // sliding sideways.
        assertTrue("one second of correction was $stepDegrees°", stepDegrees <= 0.3f)
    }

    @Test
    fun `a field that changes strength is interference, not rotation`() {
        val h = MagnetometerHeading()
        settle(h, Quaternion.IDENTITY)
        val before = h.rejections

        // Same direction, twice the strength: a motor, a speaker, a lift.
        assertNull(h.update(measure(Quaternion.IDENTITY) * 2f, Quaternion.IDENTITY, dt))
        assertTrue(h.rejections > before)
    }

    @Test
    fun `an implausibly large error is rejected rather than applied`() {
        val h = MagnetometerHeading()
        settle(h, Quaternion.IDENTITY)
        // 40° of apparent error in one sample is not drift.
        assertNull(h.update(measure(Quaternion.IDENTITY), yaw(40f), dt))
    }

    /**
     * If the field itself has genuinely moved — the wearer walked into another
     * room — every sample disagrees forever. Holding the old reference would
     * mean never correcting again, so persistent disagreement re-anchors.
     */
    /**
     * The behaviour a ten-minute wear test removed.
     *
     * Re-anchoring on disagreement adopts the drifted heading as truth, and
     * because this is a controller it then *drives* the estimate to the new
     * wrong target at the rate limit — worn, a panel-width jump over a minute
     * which then holds. Refusing to correct is the safe failure: the estimate
     * falls back to the gyro and recovers on its own when the wearer faces a
     * heading where the field agrees.
     */
    @Test
    fun `sustained disagreement never adopts the drifted heading`() {
        val h = MagnetometerHeading()
        settle(h, Quaternion.IDENTITY)
        assertEquals(0L, h.reanchors)

        // A full minute of disagreement, far past any plausible head turn.
        repeat(60_000) { h.update(measure(Quaternion.IDENTITY), yaw(40f), dt) }

        assertEquals("the reference must not move on its own", 0L, h.reanchors)
        assertTrue("but it is worth counting", h.sustainedDisagreements > 0)
        // Still refusing, rather than having quietly redefined north.
        assertNull(h.update(measure(Quaternion.IDENTITY), yaw(40f), dt))
    }

    /**
     * And the flip side: once the wearer faces somewhere the field agrees
     * again, correction resumes by itself. The refusal must not be a latch.
     */
    @Test
    fun `correction resumes once the disagreement passes`() {
        val h = MagnetometerHeading()
        settle(h, Quaternion.IDENTITY)
        repeat(60_000) { h.update(measure(Quaternion.IDENTITY), yaw(40f), dt) }

        assertNotNull(h.update(measure(Quaternion.IDENTITY), yaw(5f), dt))
    }

    /**
     * Regression for the failure that made the first worn A/B meaningless:
     * 151,495 samples rejected in a row and not one correction applied.
     *
     * The magnitude gate compared each sample against a running average that
     * was only updated by samples which *passed* the gate. One step change in
     * field strength — which is exactly what switching on a hard-iron estimate
     * mid-session produces — left the average stale forever, and every
     * subsequent sample failed against it. A latch that can only be released by
     * the thing it is blocking.
     */
    @Test
    fun `a step change in field strength does not deadlock the magnitude gate`() {
        val h = MagnetometerHeading()
        settle(h, Quaternion.IDENTITY, samples = 50)

        // The field abruptly reads half as strong and stays there.
        val weaker = Vec3(worldField.x * 0.5f, worldField.y * 0.5f, worldField.z * 0.5f)
        fun measureWeak(q: Quaternion) = q.conjugate().rotate(weaker)

        var corrected = false
        repeat(20_000) {
            if (h.update(measureWeak(Quaternion.IDENTITY), yaw(2f), dt) != null) corrected = true
        }
        assertTrue("the gate must recover to the new field strength", corrected)
    }

    @Test
    fun `a near-vertical field carries no heading and is rejected`() {
        val h = MagnetometerHeading()
        val vertical = Vec3(1f, 300f, 1f)
        assertNull(h.update(vertical, Quaternion.IDENTITY, dt))
        assertTrue(h.rejections > 0)
        assertTrue("must not anchor on a useless sample", !h.anchored)
    }

    @Test
    fun `disabling it stops all correction`() {
        val h = MagnetometerHeading()
        settle(h, Quaternion.IDENTITY)
        h.enabled = false
        assertNull(h.update(measure(Quaternion.IDENTITY), yaw(5f), dt))
    }

    /**
     * The design's whole premise: it never needs to know the field's true
     * direction, only that it stopped changing. A constant unknown rotation
     * between the sensor frame and the body — which is exactly what an
     * unverified `gyro_q_mag` is — must therefore not affect the correction.
     */
    @Test
    fun `an unknown constant sensor rotation does not change the correction`() {
        val misalignment = Quaternion(0.5f, -0.5f, 0.5f, -0.5f).normalized()

        val plain = MagnetometerHeading()
        settle(plain, Quaternion.IDENTITY)
        val expected = plain.update(measure(Quaternion.IDENTITY), yaw(5f), dt)!!

        val rotated = MagnetometerHeading()
        repeat(5) {
            rotated.update(misalignment.rotate(measure(Quaternion.IDENTITY)), Quaternion.IDENTITY, dt)
        }
        val actual = rotated.update(misalignment.rotate(measure(Quaternion.IDENTITY)), yaw(5f), dt)!!

        assertEquals(yawDegrees(expected), yawDegrees(actual), 0.001f)
    }

    @Test
    fun `hard iron is not guessed at until enough of the field has been seen`() {
        val h = MagnetometerHeading()
        h.useHardIron = true
        settle(h, Quaternion.IDENTITY)
        assertNull("one heading is not enough to fit an offset", h.hardIron)
    }

    /** The shipped default: the model does not fit this hardware at all. */
    @Test
    fun `hard iron correction is off by default`() {
        assertFalse(MagnetometerHeading().useHardIron)
    }

    /**
     * Yaw alone puts every sample on a horizontal circle, and a circle does not
     * determine a sphere's centre along its axis. The old min/max midpoint
     * happily produced an answer here — a confident, wrong one — which is
     * precisely the failure this refuses to repeat.
     */
    @Test
    fun `a yaw-only sweep does not produce a hard iron estimate`() {
        val h = MagnetometerHeading()
        // Off in production — no constant offset describes this hardware — but
        // the estimator's own contract is still worth pinning.
        h.useHardIron = true
        repeat(40) {
            for (degrees in 0..350 step 10) {
                val at = yaw(degrees.toFloat())
                h.update(measure(at), at, dt)
            }
        }
        assertTrue("plenty of samples", h.hardIronEstimator.samples > 1000)
        assertNull("but all in one plane", h.hardIron)
    }

    /** With pitch in the mix the cloud has extent in all three axes. */
    @Test
    fun `a sweep with pitch variation does produce one`() {
        val h = MagnetometerHeading()
        h.useHardIron = true
        repeat(40) {
            for (yawDeg in 0..350 step 15) {
                for (pitchDeg in -40..40 step 20) {
                    val at = (yaw(yawDeg.toFloat()) * pitchQuaternion(pitchDeg.toFloat())).normalized()
                    h.update(measure(at), at, dt)
                }
            }
        }
        assertNotNull(h.hardIron)
    }

    private fun pitchQuaternion(degrees: Float): Quaternion {
        val half = Math.toRadians(degrees.toDouble()).toFloat() * 0.5f
        return Quaternion(kotlin.math.cos(half), kotlin.math.sin(half), 0f, 0f)
    }

    private operator fun Vec3.times(s: Float) = Vec3(x * s, y * s, z * s)

    private fun yawDegrees(q: Quaternion) = q.toEulerDegrees().first
}
