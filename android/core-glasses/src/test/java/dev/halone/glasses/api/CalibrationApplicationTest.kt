package dev.halone.glasses.api

import dev.halone.glasses.drivers.xreal.XrealDriver
import dev.halone.glasses.drivers.xreal.XrealFallbackCalibration
import dev.halone.glasses.fusion.HeadTracker
import dev.halone.glasses.fusion.OrientationFilter
import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AxisConventionQuaternionTest {

    private val axes = XrealDriver.AXES

    /**
     * A change of basis has to commute with the rotation it carries: converting
     * a vector and then rotating it must land in the same place as rotating it
     * and then converting. If it does not, the world is mirrored or twisted and
     * nothing downstream can tell.
     */
    @Test
    fun `converting a rotation commutes with converting the vectors it acts on`() {
        val hardwareRotation = Quaternion(0.9659f, 0.2588f, 0f, 0f).normalized()
        val hardwareVector = Vec3(0.3f, -0.7f, 0.5f)

        val rotatedThenConverted = axes.apply(hardwareRotation.rotate(hardwareVector))
        val convertedThenRotated = axes.apply(hardwareRotation).rotate(axes.apply(hardwareVector))

        assertEquals(rotatedThenConverted.x, convertedThenRotated.x, 1e-5f)
        assertEquals(rotatedThenConverted.y, convertedThenRotated.y, 1e-5f)
        assertEquals(rotatedThenConverted.z, convertedThenRotated.z, 1e-5f)
    }

    @Test
    fun `the xreal convention is a rotation, not a reflection`() {
        assertTrue(axes.isProper)
        assertTrue(AxisConvention.IDENTITY.isProper)
    }

    @Test
    fun `a reflecting convention is rejected rather than mirroring the world`() {
        val reflection = AxisConvention(
            AxisConvention.Axis.X_NEG,
            AxisConvention.Axis.Y_POS,
            AxisConvention.Axis.Z_POS,
        )
        assertFalse(reflection.isProper)
        val failure = runCatching { reflection.apply(Quaternion.IDENTITY) }
        assertTrue(failure.isFailure)
    }
}

class FrustumTest {

    private val calibrated = XrealFallbackCalibration.load(XrealDriver.AXES).left!!.frustum

    @Test
    fun `symmetrising keeps the field of view and removes the offset`() {
        val symmetric = calibrated.symmetrised()
        assertEquals(0f, symmetric.verticalAsymmetryDegrees, 1e-4f)
        assertEquals(0f, symmetric.horizontalAsymmetryDegrees, 1e-4f)
        // It keeps the widest extent, so nothing that was visible disappears.
        assertTrue(symmetric.verticalDegrees >= calibrated.verticalDegrees)
    }

    @Test
    fun `scaling changes the field of view but not the shape`() {
        val wide = calibrated.scaledBy(2f)
        assertTrue(wide.verticalDegrees > calibrated.verticalDegrees)
        assertEquals(
            calibrated.left / calibrated.right,
            wide.left / wide.right,
            1e-5f,
        )
    }

    /**
     * The number the compositor used to be given, against the one the unit
     * actually has. Left here because the gap is the open question and a
     * regression toward 19° should be noticed.
     */
    @Test
    fun `the calibrated field of view differs from the hand-measured one`() {
        assertEquals(22.575f, calibrated.verticalDegrees, 0.01f)
        val handMeasured = 19.0f
        assertTrue(abs(calibrated.verticalDegrees - handMeasured) > 3f)
    }
}

class HeadTrackerCalibrationTest {

    private val optics = Optics(
        diagonalFovDegrees = 37.7f,
        perEyeWidth = 1920,
        perEyeHeight = 1080,
        imuRateHz = 1000,
    )

    private val calibration = XrealFallbackCalibration.load(XrealDriver.AXES)

    @Test
    fun `without calibration nothing is seeded and the old wait applies`() {
        val tracker = HeadTracker(optics)
        assertFalse(tracker.biasSeeded)
        assertFalse(tracker.hasUsableBias)
        assertNull(tracker.factoryGyroBias)
        assertEquals(Vec3.ZERO, tracker.accelBias)
    }

    /**
     * The point of seeding: the first minute used to have no usable bias, so
     * `enterStereo` waited and the panels sat wherever the wearer happened to
     * be facing.
     */
    @Test
    fun `calibration makes a bias available immediately`() {
        val tracker = HeadTracker(optics, calibration = calibration)
        assertTrue(tracker.biasSeeded)
        assertTrue(tracker.hasUsableBias)
        assertFalse("seeded is not measured", tracker.biasCalibrated)
        assertEquals(calibration.imu!!.gyroBias, tracker.gyroBias)
        assertEquals(calibration.imu!!.accelBias, tracker.accelBias)
    }

    @Test
    fun `the seeded bias is the size we measured independently`() {
        val tracker = HeadTracker(optics, calibration = calibration)
        val degreesPerSecond = tracker.gyroBias.length * 180f / Math.PI.toFloat()
        // We measured ~0.6°/s of total bias on this unit by averaging while still.
        assertEquals(0.51f, degreesPerSecond, 0.1f)
    }

    /**
     * The sensor board is not square to the optics. `imuPitchOffsetDegrees`
     * could only ever express a rotation about one axis, and the real one is
     * about all three — so a pure-pitch correction cannot reproduce it however
     * it is tuned.
     *
     * Asserted on the rotation *angle*, which is the same number in any frame.
     * Euler components are not: the canonical frame permutes the hardware's
     * axes, so the blob's "roll" is not our roll and comparing them directly is
     * how this test failed the first time.
     */
    @Test
    fun `the mounting correction is a real three-axis rotation, not a pitch`() {
        val head = calibration.headFromImu
        val angleDegrees =
            Math.toDegrees(2.0 * kotlin.math.acos(head.w.coerceIn(-1f, 1f).toDouble())).toFloat()
        assertEquals(1.278f, angleDegrees, 0.01f)

        val (_, pitch, roll) = head.toEulerDegrees()
        assertNotEquals("a pure pitch would leave no roll", 0f, roll)
        assertTrue("both terms are of the same order", abs(roll) > 0.1f && abs(pitch) > 0.1f)

        val pitchOnly = Quaternion.fromPitchDegrees(pitch)
        assertTrue(
            "a pitch-only quaternion cannot reproduce it",
            abs(pitchOnly.dot(head)) < 0.99999f,
        )
    }

    @Test
    fun `the magnetometer alignment is taken from the calibration`() {
        val filter = OrientationFilter()
        HeadTracker(optics, filter, calibration)
        assertNotEquals(Quaternion.IDENTITY, filter.magnetToBody)
    }
}
