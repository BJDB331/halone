package dev.halone.glasses.api

import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import kotlin.math.atan
import kotlin.math.hypot

/**
 * Per-unit factory calibration, in the canonical frame.
 *
 * Every value here is measured on the individual pair of glasses at the
 * factory, so treating any of it as a per-model constant is a mistake waiting
 * to happen — two Air 2 Pros do not share an IPD, a bias or a principal point.
 * That is why [source] exists and is worth checking before trusting a number.
 *
 * Frames: the blob stores everything in the hardware's own axis convention, and
 * the parser converts on the way in. Nothing downstream of here should have to
 * think about hardware axes.
 */
data class DeviceCalibration(
    val serial: String?,
    val source: Source,
    val eyes: Map<Eye, EyeCalibration>,
    val imu: ImuCalibration?,
    val calibratedAt: String? = null,
    val configVersion: String? = null,
) {
    enum class Source {
        /** Read from the glasses that are actually attached. Trust it. */
        DEVICE,

        /**
         * A capture from a *different* unit, bundled as a last resort.
         *
         * Better than the hardcoded guesses it replaces, and wrong in exactly
         * the per-unit ways that matter most: IPD, bias, principal point. Fine
         * for getting a picture up; not something to tune against.
         */
        FALLBACK,
    }

    val left: EyeCalibration? get() = eyes[Eye.LEFT]
    val right: EyeCalibration? get() = eyes[Eye.RIGHT]

    /** Interpupillary distance from the eye positions, in metres. */
    val ipdMetres: Float?
        get() {
            val l = left?.positionFromImu ?: return null
            val r = right?.positionFromImu ?: return null
            return (r - l).length
        }

    /**
     * The orientation of the displays as a pair, relative to the IMU.
     *
     * The sensor board is not square to the optics — about a degree of roll on
     * this hardware — so "where the head is pointing" and "where the IMU is
     * pointing" are different questions. Everything above the driver means the
     * first: gaze rays, panel headings, the levelled horizon. So the shared
     * part of the two eye rotations belongs in the head pose, and only what
     * differs between the eyes belongs in the per-eye view matrix.
     *
     * Averaged by summing and renormalising, which is valid only for rotations
     * this close together — and these differ by under a degree.
     */
    val headFromImu: Quaternion
        get() {
            val l = left?.rotationFromImu ?: return Quaternion.IDENTITY
            val r = right?.rotationFromImu ?: return l
            // Opposite-sign quaternions are the same rotation; summing them
            // without checking would cancel rather than average.
            val aligned = if (l.dot(r) < 0f) r.negated() else r
            return Quaternion(l.w + aligned.w, l.x + aligned.x, l.y + aligned.y, l.z + aligned.z)
                .normalized()
        }

    fun describe(): String = buildString {
        append("calibration from ${source.name.lowercase()}")
        serial?.let { append(" (unit $it") }
        calibratedAt?.let { append(", calibrated $it") }
        if (serial != null) append(")")
        ipdMetres?.let { append("\n  IPD %.2f mm".format(it * 1000)) }
        eyes.values.sortedBy { it.eye }.forEach { e ->
            append(
                "\n  %-5s H %.2f° V %.2f° diag %.2f°%s".format(
                    e.eye.name.lowercase(),
                    e.frustum.horizontalDegrees,
                    e.frustum.verticalDegrees,
                    e.frustum.diagonalDegrees,
                    if (e.distortion != null) ", ${e.distortion.rows}x${e.distortion.cols} distortion grid" else "",
                )
            )
        }
        imu?.let {
            append("\n  accel bias ${it.accelBias}  gyro bias ${it.gyroBias} rad/s")
        }
    }
}

enum class Eye { LEFT, RIGHT }

/**
 * A view frustum as the tangents of its four half-angles at unit distance,
 * which is the only form that can express an off-centre projection.
 *
 * Not a field-of-view number. The XREAL Air 2 Pro's principal point sits ~20 px
 * above centre, so its upper and lower half-angles differ by nearly a degree; a
 * symmetric `perspective(fovY, aspect)` cannot represent that no matter what
 * value it is given, and the error it leaves behind moves with head pitch.
 *
 * Settled by a blind A/B while worn. Three projections, ranked by how much a
 * world-locked panel appeared to rotate as the gaze swept across it:
 *
 * 1. **calibrated, 22.58° asymmetric** — least rotation, chosen both rounds
 * 2. legacy, 19.0° symmetric — the hand-measured value, some rotation
 * 3. symmetrised, 23.40° centred — *clearly* the worst
 *
 * The ordering is the interesting part. The wide field of view is only right
 * with the off-centre principal point: apply one without the other and it is
 * worse than both. That is why nulling by hand landed on 19° — a narrower
 * symmetric frustum is the best available compensation for an asymmetry you
 * cannot express, which is also why 19° remains the right *fallback* when there
 * is no calibration to read.
 */
data class Frustum(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float,
) {
    val horizontalDegrees: Float
        get() = Math.toDegrees((atan(right) - atan(left)).toDouble()).toFloat()

    val verticalDegrees: Float
        get() = Math.toDegrees((atan(top) - atan(bottom)).toDouble()).toFloat()

    val diagonalDegrees: Float
        get() {
            val halfW = (right - left) / 2f
            val halfH = (top - bottom) / 2f
            return (2.0 * Math.toDegrees(atan(hypot(halfW, halfH).toDouble()))).toFloat()
        }

    /** How far off-centre the projection is, in degrees; zero for a symmetric one. */
    val verticalAsymmetryDegrees: Float
        get() = Math.toDegrees((atan(top) + atan(bottom)).toDouble()).toFloat()

    val horizontalAsymmetryDegrees: Float
        get() = Math.toDegrees((atan(right) + atan(left)).toDouble()).toFloat()

    /** The nearest symmetric frustum, for A/B against the calibrated one. */
    fun symmetrised(): Frustum {
        val h = maxOf(-left, right)
        val v = maxOf(-bottom, top)
        return Frustum(-h, h, -v, v)
    }

    fun scaledBy(factor: Float) = Frustum(left * factor, right * factor, bottom * factor, top * factor)

    /**
     * Grows the frustum by [fraction] of its own extent on every side.
     *
     * Distinct from [scaledBy], which multiplies about the optical axis: an
     * off-centre frustum scaled that way moves its centre, where this keeps the
     * image and adds a margin around it. That margin is what a distortion warp
     * samples at the corners.
     */
    fun expandedBy(fraction: Float): Frustum {
        val width = right - left
        val height = top - bottom
        return Frustum(
            left - width * fraction,
            right + width * fraction,
            bottom - height * fraction,
            top + height * fraction,
        )
    }
}

data class EyeCalibration(
    val eye: Eye,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val widthPx: Int,
    val heightPx: Int,
    /** Eye position relative to the IMU, canonical frame, metres. */
    val positionFromImu: Vec3,
    /** Eye orientation relative to the IMU, canonical frame. */
    val rotationFromImu: Quaternion,
    val distortion: DistortionGrid? = null,
) {
    /**
     * The projection this eye needs, from the pinhole intrinsics.
     *
     * Sign convention: the frustum is expressed on the near plane at unit
     * distance looking down -Z, so `top` is positive and `bottom` negative.
     * Image +y runs *down* the display, which is why the two vertical bounds
     * come from `cy` the way round they do.
     */
    /** This eye's rotation relative to the head frame, not the IMU's. */
    fun rotationFromHead(headFromImu: Quaternion): Quaternion =
        (headFromImu.conjugate() * rotationFromImu).normalized()

    val frustum: Frustum
        get() = Frustum(
            left = -cx / fx,
            right = (widthPx - cx) / fx,
            bottom = -(heightPx - cy) / fy,
            top = cy / fy,
        )
}

/**
 * A measured map from where a pixel should be to where the optics actually put
 * it, sampled on a grid over the frame.
 *
 * Stored flat: `points[(row * cols + col) * 4 + i]` with `i` running
 * `idealX, idealY, actualX, actualY`, all in pixels. The ideal grid is regular
 * but not uniform — it is denser toward the edges, where the displacement
 * changes fastest.
 */
class DistortionGrid(
    val rows: Int,
    val cols: Int,
    val points: FloatArray,
) {
    init {
        require(points.size == rows * cols * VALUES_PER_POINT) {
            "expected ${rows * cols * VALUES_PER_POINT} values, got ${points.size}"
        }
    }

    fun idealX(row: Int, col: Int) = points[index(row, col)]
    fun idealY(row: Int, col: Int) = points[index(row, col) + 1]
    fun actualX(row: Int, col: Int) = points[index(row, col) + 2]
    fun actualY(row: Int, col: Int) = points[index(row, col) + 3]

    fun displacement(row: Int, col: Int): Float {
        val i = index(row, col)
        return hypot(points[i + 2] - points[i], points[i + 3] - points[i + 1])
    }

    val maxDisplacementPx: Float
        get() = (0 until rows).flatMap { r -> (0 until cols).map { c -> displacement(r, c) } }.max()

    val meanDisplacementPx: Float
        get() = (0 until rows).flatMap { r -> (0 until cols).map { c -> displacement(r, c) } }.average().toFloat()

    private fun index(row: Int, col: Int) = (row * cols + col) * VALUES_PER_POINT

    companion object {
        const val VALUES_PER_POINT = 4
    }
}

/**
 * Factory IMU calibration, canonical frame.
 *
 * **Sign convention is by measurement, not by assumption.** [accelBias] and
 * [gyroBias] are defined here as the value to *subtract* from a raw sample,
 * which is the usual meaning of "bias", and the `calibcheck` spike exists to
 * confirm that against the hardware rather than leave it inferred. The
 * hardware-frame values are kept alongside so that check has something to
 * compare against without re-reading the blob.
 */
data class ImuCalibration(
    val accelBias: Vec3,
    val gyroBias: Vec3,
    val magBias: Vec3,
    val accelScale: Vec3,
    val gyroScale: Vec3,
    val magScale: Vec3,
    /** Rotation from the gyroscope frame to the magnetometer's, canonical. */
    val gyroToMag: Quaternion,
    /** `[gyro noise, gyro random walk, accel noise, accel random walk]`, or null. */
    val noises: List<Float>? = null,
    val hardwareAccelBias: Vec3 = Vec3.ZERO,
    val hardwareGyroBias: Vec3 = Vec3.ZERO,
) {
    /**
     * True when the vendor stored no usable magnetometer calibration — zero
     * bias and unit scale, which is what an uncalibrated field looks like.
     *
     * Compared with a tolerance rather than by equality. Beyond the usual
     * reason not to compare floats, an axis permutation that negates a
     * component turns `0.0f` into `-0.0f`, and those are *not* equal under
     * `Float.equals` even though they are under `==` on primitives — so a data
     * class holding one silently stops matching the other.
     */
    val magnetometerUncalibrated: Boolean
        get() = magBias.length < 1e-6f &&
            (magScale - Vec3(1f, 1f, 1f)).length < 1e-6f
}
