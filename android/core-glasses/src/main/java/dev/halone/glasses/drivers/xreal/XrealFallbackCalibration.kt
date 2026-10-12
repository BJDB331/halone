package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.DeviceCalibration
import dev.halone.glasses.api.Eye
import dev.halone.glasses.api.EyeCalibration
import dev.halone.glasses.api.ImuCalibration
import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3

/**
 * A real capture from one XREAL Air 2 Pro, bundled for when the attached
 * glasses will not hand theirs over.
 *
 * Using another unit's numbers is a compromise, and worth being clear about
 * which parts of it are safe. The optics are close to per-model: two units'
 * intrinsics differ in the third significant figure. The per-unit parts — IPD,
 * accelerometer bias, principal point — are not, and tuning against them would
 * be tuning against someone else's glasses. Everything loaded this way is
 * stamped [DeviceCalibration.Source.FALLBACK] so a caller can tell.
 *
 * Still far better than what it replaces: before this, the field of view was a
 * hand-measured constant, the frustum was symmetric, IPD was a round 63 mm and
 * the accelerometer bias was zero.
 */
internal object XrealFallbackCalibration {

    private const val RESOURCE = "calibration-air2pro-fallback.json"

    @Volatile
    private var cached: Pair<AxisConvention, DeviceCalibration>? = null

    fun load(axes: AxisConvention): DeviceCalibration {
        cached?.let { (forAxes, calibration) -> if (forAxes == axes) return calibration }

        val parsed = readResource()
            ?.let {
                XrealCalibrationParser
                    .parse(it, axes, DeviceCalibration.Source.FALLBACK)
                    .getOrNull()
            }
            ?: hardcoded(axes)

        cached = axes to parsed
        return parsed
    }

    private fun readResource(): ByteArray? =
        XrealFallbackCalibration::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }

    /**
     * Last resort if even the bundled resource cannot be read — a stripped
     * build, a broken packaging step.
     *
     * The numbers are the same unit's, transcribed, minus the distortion grid
     * that is too large to inline. Something has to render, and a symmetric
     * guess at this point would undo the entire exercise.
     */
    private fun hardcoded(axes: AxisConvention) = DeviceCalibration(
        serial = null,
        source = DeviceCalibration.Source.FALLBACK,
        eyes = mapOf(
            Eye.LEFT to EyeCalibration(
                eye = Eye.LEFT,
                fx = 2691.43f, fy = 2705.31f, cx = 964.043f, cy = 560.216f,
                widthPx = 1920, heightPx = 1080,
                positionFromImu = axes.apply(Vec3(-0.0589281f, 0.0179372f, -0.0179345f)),
                rotationFromImu = axes.apply(
                    Quaternion(0.999916f, -0.00861935f, 0.00804455f, 0.0054062f)
                ),
            ),
            Eye.RIGHT to EyeCalibration(
                eye = Eye.RIGHT,
                fx = 2689.94f, fy = 2703.82f, cx = 965.404f, cy = 561.492f,
                widthPx = 1920, heightPx = 1080,
                positionFromImu = axes.apply(Vec3(0.0047253f, 0.018986f, -0.0182181f)),
                rotationFromImu = axes.apply(
                    Quaternion(0.999945f, -0.00883285f, 0.000348083f, 0.00567003f)
                ),
            ),
        ),
        imu = ImuCalibration(
            accelBias = axes.apply(Vec3(0.028184952f, -0.27583191f, 0.012632324f)),
            gyroBias = axes.apply(Vec3(-0.0048627577f, -0.0074488874f, 0.0005333641f)),
            magBias = Vec3.ZERO,
            accelScale = Vec3(1f, 1f, 1f),
            gyroScale = Vec3(1f, 1f, 1f),
            magScale = Vec3(1f, 1f, 1f),
            gyroToMag = axes.apply(Quaternion(0.5f, -0.5f, 0.5f, -0.5f).conjugate()),
            noises = listOf(0.00035f, 1e-05f, 0.00667f, 0.00068f),
            hardwareAccelBias = Vec3(0.028184952f, -0.27583191f, 0.012632324f),
            hardwareGyroBias = Vec3(-0.0048627577f, -0.0074488874f, 0.0005333641f),
        ),
        calibratedAt = "2024-08-31 15:41:24",
        configVersion = "5.0",
    )
}
