package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.DeviceCalibration
import dev.halone.glasses.api.DistortionGrid
import dev.halone.glasses.api.Eye
import dev.halone.glasses.api.EyeCalibration
import dev.halone.glasses.api.ImuCalibration
import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses the JSON calibration blob an XREAL device hands back over `0x14`/`0x15`.
 *
 * The blob is self-describing and carries its own conventions in `_comment`
 * fields, which is why this is a reader rather than the empirical guessing the
 * teardown expected. See `docs/protocol/xreal-hid.md` for a full example.
 *
 * Everything is converted into the canonical frame on the way out, so nothing
 * downstream deals in hardware axes.
 */
internal object XrealCalibrationParser {

    fun parse(
        bytes: ByteArray,
        axes: AxisConvention,
        source: DeviceCalibration.Source,
    ): Result<DeviceCalibration> = runCatching {
        val root = JSONObject(String(bytes, Charsets.UTF_8))

        val display = root.optJSONObject("display")
        val resolution = display?.optJSONArray("resolution")
        val width = resolution?.optDouble(0)?.toInt() ?: DEFAULT_WIDTH
        val height = resolution?.optDouble(1)?.toInt() ?: DEFAULT_HEIGHT
        val distortion = root.optJSONObject("display_distortion")

        val eyes = buildMap {
            for (eye in Eye.entries) {
                val side = eye.name.lowercase()
                val k = display?.optJSONArray("k_${side}_display") ?: continue
                put(
                    eye,
                    EyeCalibration(
                        eye = eye,
                        fx = k.getDouble(0).toFloat(),
                        fy = k.getDouble(4).toFloat(),
                        cx = k.getDouble(2).toFloat(),
                        cy = k.getDouble(5).toFloat(),
                        widthPx = width,
                        heightPx = height,
                        positionFromImu = axes.apply(
                            vec3(display.getJSONArray("target_p_${side}_display"))
                        ),
                        // Documented in the blob as Hamilton (qx,qy,qz,qw),
                        // which is our own convention with the parts reordered.
                        rotationFromImu = axes.apply(
                            quaternionXyzw(display.getJSONArray("target_q_${side}_display"))
                        ),
                        distortion = distortion?.optJSONObject("${side}_display")
                            ?.let { grid(it) },
                    ),
                )
            }
        }
        require(eyes.isNotEmpty()) { "no display intrinsics in the calibration blob" }

        DeviceCalibration(
            serial = root.optString("FSN").takeIf { it.isNotBlank() },
            source = source,
            eyes = eyes,
            imu = root.optJSONObject("IMU")?.optJSONObject("device_1")?.let { imu(it, axes) },
            calibratedAt = root.optString("last_modified_time").takeIf { it.isNotBlank() },
            configVersion = root.opt("configuration_file_version")?.toString(),
        )
    }

    /**
     * The factory's IMU frame is not the one the sensor packets arrive in: Y and
     * Z are exchanged and all three axes are negated.
     *
     * **Measured, not documented.** The blob states conventions for its
     * quaternions and says nothing about this, and applying the bias vectors
     * as-if-packet-frame makes both sensors *worse*. Searching the 24 proper
     * signed permutations against a stationary reading picks this one out
     * unambiguously, and two independent quantities agree on it:
     *
     * | | uncorrected | this mapping | next-best mapping |
     * |---|---|---|---|
     * | gyro residual at rest | 0.580 °/s | **0.092 °/s** | 0.194 °/s |
     * | accelerometer `|a|` error | 0.345 m/s² | **0.071 m/s²** | — |
     *
     * The accelerometer is the convincing part: it was not used to find the
     * mapping, and it drops the long-standing 3.5% rest-magnitude error to
     * 0.7%. That error was never a scale error — `scale_accel` really is
     * identity — it was this bias, read in the wrong frame.
     *
     * Caveat worth keeping: one unit, one measurement. A second pair of glasses
     * would confirm the mapping is the vendor's convention rather than ours.
     */
    private val FACTORY_IMU_FRAME = AxisConvention(
        xFrom = AxisConvention.Axis.X_NEG,
        yFrom = AxisConvention.Axis.Z_NEG,
        zFrom = AxisConvention.Axis.Y_NEG,
    )

    private fun imu(o: JSONObject, axes: AxisConvention): ImuCalibration {
        val accel = FACTORY_IMU_FRAME.apply(vec3(o.getJSONArray("accel_bias")))
        val gyro = FACTORY_IMU_FRAME.apply(vec3(o.getJSONArray("gyro_bias")))
        return ImuCalibration(
            accelBias = axes.apply(accel),
            gyroBias = axes.apply(gyro),
            magBias = axes.apply(FACTORY_IMU_FRAME.apply(vec3(o.getJSONArray("mag_bias")))),
            accelScale = axes.apply(FACTORY_IMU_FRAME.apply(vec3(o.getJSONArray("scale_accel")))).abs(),
            gyroScale = axes.apply(FACTORY_IMU_FRAME.apply(vec3(o.getJSONArray("scale_gyro")))).abs(),
            magScale = axes.apply(FACTORY_IMU_FRAME.apply(vec3(o.getJSONArray("scale_mag")))).abs(),
            // The stored quaternion, carried into the canonical frame and
            // nothing else.
            //
            // It was previously conjugated — the blob's comment calls it JPL
            // where ours is Hamilton — and also put through
            // [FACTORY_IMU_FRAME]. Both were wrong, and measurably so. Against
            // a recording of the field and the pose together, the heading a
            // sample implies should differ from the tracker's yaw by a
            // constant, so the *spread* of that difference measures the
            // alignment:
            //
            // | interpretation | heading spread | pitch swing |
            // |---|---|---|
            // | conjugated, factory-framed (was) | 17.9° | 36.6° |
            // | conjugated only | 46.6° | 92.9° |
            // | factory-framed only | 28.2° | 45.2° |
            // | **as stored** | **10.6°** | **20.5°** |
            // | no transform at all | 31.7° | 283.0° |
            //
            // A free fit over all rotations reaches 7.6°, so this is close to
            // the best any rigid alignment can do and the remainder is
            // something else — most likely a field this desk bends rather than
            // one the glasses misread.
            //
            // Worth noting that [FACTORY_IMU_FRAME] genuinely is needed for the
            // bias *vectors* and genuinely is not needed here. Two conventions
            // in one file, which is annoying but is what the hardware does.
            gyroToMag = axes.apply(quaternionXyzw(o.getJSONArray("gyro_q_mag"))),
            noises = o.optJSONArray("imu_noises")?.let { array ->
                (0 until array.length()).map { array.getDouble(it).toFloat() }
            },
            // Kept in the sensor-packet frame, which is what `calibcheck`
            // compares a live reading against.
            hardwareAccelBias = accel,
            hardwareGyroBias = gyro,
        )
    }

    private fun grid(o: JSONObject): DistortionGrid? {
        if (o.optInt("type", 0) != DISTORTION_TYPE_DISPLAY) return null
        val rows = o.optInt("num_row", 0)
        val cols = o.optInt("num_col", 0)
        val data = o.optJSONArray("data") ?: return null
        if (rows <= 0 || cols <= 0) return null

        val flat = FloatArray(rows * cols * DistortionGrid.VALUES_PER_POINT)
        var i = 0
        fun push(node: Any?) {
            when (node) {
                is JSONArray -> for (j in 0 until node.length()) push(node.get(j))
                is Number -> {
                    if (i < flat.size) flat[i] = node.toFloat()
                    i++
                }
            }
        }
        push(data)
        // A grid whose size does not match its own declared dimensions is not
        // something to interpolate through; better to have none.
        if (i != flat.size) return null
        return DistortionGrid(rows, cols, flat)
    }

    private fun vec3(a: JSONArray) =
        Vec3(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat())

    /** The blob stores quaternions `(qx, qy, qz, qw)`; ours is `(w, x, y, z)`. */
    private fun quaternionXyzw(a: JSONArray) = Quaternion(
        a.getDouble(3).toFloat(),
        a.getDouble(0).toFloat(),
        a.getDouble(1).toFloat(),
        a.getDouble(2).toFloat(),
    ).normalized()

    /** Scales are magnitudes; the axis permutation may flip a sign, which they do not have. */
    private fun Vec3.abs() = Vec3(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(z))

    private const val DISTORTION_TYPE_DISPLAY = 1
    private const val DEFAULT_WIDTH = 1920
    private const val DEFAULT_HEIGHT = 1080
}
