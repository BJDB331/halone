package dev.halone.testkit.ab

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.Optics
import dev.halone.glasses.api.TrackingSample
import dev.halone.glasses.fusion.HeadTracker
import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import dev.halone.testkit.capture.ImuListener
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One candidate setting shown to the tester. */
data class SceneParams(
    val verticalFovDegrees: Double,
    val predictionMs: Double,
    /** Extra pitch on top of [HeadView.baseMount], about the head's X axis. */
    val mountOffsetDegrees: Double,
)

/**
 * The tester's head pose for rendering, from the raw report stream mapped
 * through the convention solved earlier in the session.
 *
 * The tracker runs with no mount of its own; the mount and the prediction
 * horizon are applied per frame here, so switching candidates is instant and
 * never makes the filter settle again.
 */
class HeadView(
    private val convention: AxisConvention,
    /** The head frame as the IMU sees it, canonical axes; from the factory calibration. */
    val baseMount: Quaternion,
) : ImuListener {

    private val tracker = HeadTracker(
        Optics(diagonalFovDegrees = 50f, perEyeWidth = 1920, perEyeHeight = 1080, imuRateHz = 1000),
    ).apply { accelWeight = SETTLE_ACCEL_WEIGHT }

    private var samples = 0

    @Volatile
    private var yawReference = Quaternion.IDENTITY

    /** Pulls hard on gravity for the first second, so the scene is level when it first appears. */
    override fun onImu(timestampNs: Long, gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float) {
        if (++samples == SETTLE_SAMPLES) tracker.accelWeight = RUNNING_ACCEL_WEIGHT
        tracker.onSample(
            TrackingSample.Inertial(
                timestampNs,
                convention.apply(Vec3(gx, gy, gz)),
                convention.apply(Vec3(ax, ay, az)),
            )
        )
    }

    /** Head orientation in the world, recentred so the last [recenter] faces forward. */
    fun head(params: SceneParams): Quaternion = (yawReference * raw(params, predict = true)).normalized()

    /** Makes the current heading forward, leaving pitch and roll to gravity. */
    fun recenter(params: SceneParams) {
        val forward = raw(params, predict = false).rotate(Vec3(0f, 0f, -1f))
        val yaw = atan2(-forward.x, -forward.z)
        yawReference = aboutY(-yaw)
    }

    private fun raw(params: SceneParams, predict: Boolean): Quaternion {
        var q = tracker.gravityOrientation
        if (predict && params.predictionMs > 0) {
            val w = tracker.pose.angularVelocity
            val half = (params.predictionMs / 1000.0 / 2).toFloat()
            q = (q * Quaternion(1f, w.x * half, w.y * half, w.z * half).normalized()).normalized()
        }
        return (q * mount(params)).normalized()
    }

    fun mount(params: SceneParams): Quaternion =
        (baseMount * Quaternion.fromPitchDegrees(params.mountOffsetDegrees.toFloat())).normalized()

    private fun aboutY(radians: Float) = Quaternion(cos(radians / 2), 0f, sin(radians / 2), 0f)

    private companion object {
        const val SETTLE_SAMPLES = 1_000
        const val SETTLE_ACCEL_WEIGHT = 0.05f
        const val RUNNING_ACCEL_WEIGHT = 0.0005f
    }
}
