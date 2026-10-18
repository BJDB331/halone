package dev.halone.glasses.fusion

import dev.halone.glasses.api.DeviceCalibration
import dev.halone.glasses.api.Eye
import dev.halone.glasses.api.EyeCalibration
import dev.halone.glasses.api.Optics
import dev.halone.glasses.api.TrackingSample
import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos

/**
 * An IMU mounted at a large angle to the displays, as on the XREAL One Pro
 * (~35° in pitch). The head yaws about the world's vertical; whatever the mount,
 * the published pose must do the same and stay level.
 */
class HeadTrackerMountTest {

    private val optics = Optics(diagonalFovDegrees = 50f, perEyeWidth = 1920, perEyeHeight = 1080, imuRateHz = 1000)

    /** Display axes as the IMU sees them: head-up comes out tilted towards the front, as measured on a One Pro. */
    private val displayInImu = Quaternion.fromPitchDegrees(-35f)

    private fun calibration(rotation: Quaternion) = DeviceCalibration(
        serial = null,
        source = DeviceCalibration.Source.DEVICE,
        eyes = Eye.entries.associateWith { eye ->
            EyeCalibration(
                eye = eye, fx = 2200f, fy = 2200f, cx = 960f, cy = 540f, widthPx = 1920, heightPx = 1080,
                positionFromImu = Vec3(if (eye == Eye.LEFT) -0.032f else 0.032f, 0f, 0f),
                rotationFromImu = rotation,
            )
        },
        imu = null,
    )

    private fun degreesBetween(a: Vec3, b: Vec3): Double {
        val cos = (a.x * b.x + a.y * b.y + a.z * b.z) / (a.length * b.length)
        return Math.toDegrees(acos(cos.coerceIn(-1f, 1f).toDouble()))
    }

    /**
     * Settles on gravity with a strong pull, then turns on the gyro alone, so
     * what is measured is the mount composition and not the filter's patience.
     */
    private fun tracker() = HeadTracker(optics, OrientationFilter(accelWeight = 0.05f), calibration(displayInImu))

    /** Head still and level, then a 90° turn to the left over a second, then still again. */
    private fun turnLeft(tracker: HeadTracker) {
        val gravity = displayInImu.rotate(Vec3(0f, 9.81f, 0f))
        val turn = displayInImu.rotate(Vec3(0f, (Math.PI / 2).toFloat(), 0f))
        var t = 1_000_000L
        fun feed(seconds: Double, gyro: Vec3) {
            repeat((seconds * 1000).toInt()) {
                tracker.onSample(TrackingSample.Inertial(t, gyro, gravity))
                t += 1_000_000L
            }
        }
        feed(2.0, Vec3.ZERO)
        tracker.accelWeight = 0f
        feed(1.0, turn)
        feed(0.5, Vec3.ZERO)
    }

    @Test
    fun `a turn about the vertical stays level through a steep mount`() {
        val tracker = tracker()
        turnLeft(tracker)

        val pose = tracker.pose.orientation
        val up = pose.rotate(Vec3(0f, 1f, 0f))
        val forward = pose.rotate(Vec3(0f, 0f, -1f))
        val tilt = degreesBetween(up, Vec3(0f, 1f, 0f))
        val heading = degreesBetween(forward, Vec3(-1f, 0f, 0f))
        assertTrue("head tilted %.1f° after a level turn".format(tilt), tilt < 3.0)
        assertTrue("forward is %.1f° from where a left turn puts it".format(heading), heading < 5.0)
    }

    @Test
    fun `a level head reads level before any turn`() {
        val tracker = tracker()
        val gravity = displayInImu.rotate(Vec3(0f, 9.81f, 0f))
        var t = 1_000_000L
        repeat(2000) {
            tracker.onSample(TrackingSample.Inertial(t, Vec3.ZERO, gravity))
            t += 1_000_000L
        }
        val up = tracker.pose.orientation.rotate(Vec3(0f, 1f, 0f))
        assertTrue("tilted %.1f° at rest".format(degreesBetween(up, Vec3(0f, 1f, 0f))), degreesBetween(up, Vec3(0f, 1f, 0f)) < 2.0)
    }
}
