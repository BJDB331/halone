package dev.halone.testkit.analysis

import dev.halone.glasses.api.AxisConvention
import dev.halone.testkit.capture.SegmentRecorder
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Works out how the hardware's axes map onto Halone's canonical frame (see
 * `AxisConvention`) from head motions whose direction is known, and how far
 * the IMU is tilted against the head beyond that.
 *
 * Each pair of opposite motions gives one head axis as the IMU sees it. Two
 * pairs fix the frame. The third pair and gravity are independent checks, which
 * is what makes the answer trustworthy enough to render with.
 */
object AxisSolver {

    /** Rotation axis, in the canonical frame, of each motion's first swing. */
    val EXPECTED = mapOf(
        "turn-left" to D(0.0, 1.0, 0.0),
        "turn-right" to D(0.0, -1.0, 0.0),
        "look-up" to D(1.0, 0.0, 0.0),
        "look-down" to D(-1.0, 0.0, 0.0),
        "tilt-left" to D(0.0, 0.0, 1.0),
        "tilt-right" to D(0.0, 0.0, -1.0),
    )

    enum class Status { CONFIDENT, INFERRED, AMBIGUOUS }

    data class Result(
        val status: Status,
        val convention: AxisConvention?,
        /** Rows are the canonical axes expressed in the hardware frame. */
        val imuToHead: List<D>?,
        /** What is left once [convention] is applied: the IMU's tilt inside the frame. */
        val mountAngleDegrees: Double?,
        val mountPitchDegrees: Double?,
        val gravityFromUpDegrees: Double?,
        val problems: List<String>,
        /** Motions worth recording again before trusting the answer. */
        val redo: Set<String>,
        val swings: Map<String, D>,
    )

    /**
     * How far the head got from where it was at the start cue, at its furthest
     * point, in radians. For an out-and-back movement that is the out end.
     *
     * Measured from the cue because testers carry on from the previous step
     * while the next instruction plays. Not "the first movement after the cue",
     * which was tried: most testers make a small settling motion right after
     * the beep and only start the real one seconds later.
     */
    fun swing(samples: SegmentRecorder.Samples, bias: D): D {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var peak = D(0.0, 0.0, 0.0)
        for (i in maxOf(1, samples.goIndex + 1) until samples.size) {
            val dt = (samples.timestampsNs[i] - samples.timestampsNs[i - 1]) / 1e9
            if (dt <= 0 || dt > MAX_GAP_SECONDS) continue
            x += (samples.gyro[i * 3] - bias.x) * dt
            y += (samples.gyro[i * 3 + 1] - bias.y) * dt
            z += (samples.gyro[i * 3 + 2] - bias.z) * dt
            if (x * x + y * y + z * z > peak.dot(peak)) peak = D(x, y, z)
        }
        return peak
    }

    /** Means over what came after the start cue only. */
    fun meanGyro(samples: SegmentRecorder.Samples) = mean(samples.gyro, samples.goIndex, samples.size)

    fun meanAccel(samples: SegmentRecorder.Samples) = mean(samples.accel, samples.goIndex, samples.size)

    fun solve(swings: Map<String, D>, gravity: D?): Result {
        val problems = mutableListOf<String>()
        val gaps = mutableListOf<String>()
        val redo = linkedSetOf<String>()

        fun axis(positive: String, negative: String, name: String): D? {
            val p = swings[positive]?.takeIf { it.length() >= MIN_SWING_RADIANS }
            val n = swings[negative]?.takeIf { it.length() >= MIN_SWING_RADIANS }
            if (p == null) redo += positive
            if (n == null) redo += negative
            if (p != null && n != null) {
                val apart = degreesBetween(p.normalized(), -n.normalized())
                if (apart > MAX_PAIR_DISAGREEMENT_DEGREES) {
                    gaps += "$name: $positive and $negative disagree by %.0f°, left out".format(apart)
                    redo += positive
                    redo += negative
                    return null
                }
                return (p.normalized() - n.normalized()).normalized()
            }
            if (p == null && n == null) gaps += "$name: no usable motion"
            return (p ?: n?.let { -it })?.normalized()
        }

        val x = axis("look-up", "look-down", "pitch")
        val y = axis("turn-left", "turn-right", "yaw")
        val z = axis("tilt-left", "tilt-right", "roll")

        val rows = when {
            x != null && y != null -> {
                val x1 = (x - y * x.dot(y)).normalized()
                val z1 = x1.cross(y)
                z?.let { measured ->
                    val off = degreesBetween(z1, measured)
                    if (off > MAX_THIRD_AXIS_DEGREES) {
                        problems += "roll axis is %.0f° from where pitch and yaw put it".format(off)
                        redo += listOf("tilt-left", "tilt-right")
                    }
                }
                listOf(x1, y, z1)
            }
            y != null && z != null -> {
                val z1 = (z - y * z.dot(y)).normalized()
                listOf(y.cross(z1), y, z1)
            }
            x != null && z != null -> {
                val z1 = (z - x * z.dot(x)).normalized()
                listOf(x, z1.cross(x), z1)
            }
            else -> null
        }
        if (rows == null) {
            problems += "fewer than two axes measured"
            return Result(Status.AMBIGUOUS, null, null, null, null, null, problems + gaps, redo, swings)
        }

        val picks = rows.map { row ->
            val index = (0..2).maxBy { abs(row[it]) }
            index to row[index]
        }
        picks.forEachIndexed { i, (_, value) ->
            if (abs(value) < MIN_DOMINANCE) problems += "${"XYZ"[i]} sits between two hardware axes (%.2f)".format(abs(value))
        }
        val convention = if (picks.map { it.first }.distinct().size == 3) {
            AxisConvention(axisOf(picks[0]), axisOf(picks[1]), axisOf(picks[2])).takeIf { it.isProper }
        } else {
            null
        }
        if (convention == null) problems += "no proper signed permutation fits"

        var mountAngle: Double? = null
        var mountPitch: Double? = null
        if (convention != null) {
            val permutation = picks.map { (index, value) ->
                D(if (index == 0) sign(value) else 0.0, if (index == 1) sign(value) else 0.0, if (index == 2) sign(value) else 0.0)
            }
            val residual = Array(3) { r -> DoubleArray(3) { c -> rows[r].dot(permutation[c]) } }
            val trace = residual[0][0] + residual[1][1] + residual[2][2]
            mountAngle = Math.toDegrees(acos(((trace - 1) / 2).coerceIn(-1.0, 1.0)))
            mountPitch = Math.toDegrees(atan2(residual[2][1], residual[1][1]))
        }

        val gravityAngle = gravity?.takeIf { it.length() > 1.0 }?.let { g ->
            val up = D(rows[0].dot(g), rows[1].dot(g), rows[2].dot(g))
            degreesBetween(up.normalized(), D(0.0, 1.0, 0.0))
        }
        when {
            gravityAngle == null -> problems += "no gravity reading to check against"
            gravityAngle > GRAVITY_WRONG_DEGREES -> problems += "gravity points %.0f° from up: a sign is wrong".format(gravityAngle)
        }

        val status = when {
            problems.isNotEmpty() -> Status.AMBIGUOUS
            gaps.isNotEmpty() || redo.isNotEmpty() -> Status.INFERRED
            else -> Status.CONFIDENT
        }
        return Result(status, convention, rows, mountAngle, mountPitch, gravityAngle, problems + gaps, redo, swings)
    }

    fun toJson(result: Result): JSONObject = JSONObject().apply {
        put("status", result.status.name.lowercase())
        put("convention", result.convention?.let { "x=${it.xFrom} y=${it.yFrom} z=${it.zFrom}" } ?: JSONObject.NULL)
        put("imu_to_head_rows", result.imuToHead?.let { rows -> JSONArray(rows.map { JSONArray(listOf(it.x, it.y, it.z)) }) } ?: JSONObject.NULL)
        put("mount_angle_deg", result.mountAngleDegrees ?: JSONObject.NULL)
        put("mount_pitch_deg", result.mountPitchDegrees ?: JSONObject.NULL)
        put("gravity_from_up_deg", result.gravityFromUpDegrees ?: JSONObject.NULL)
        put("problems", JSONArray(result.problems))
        put("redo", JSONArray(result.redo.toList()))
        put("swings_rad", JSONObject().apply {
            result.swings.forEach { (label, v) -> put(label, JSONArray(listOf(v.x, v.y, v.z))) }
        })
    }

    private fun axisOf(pick: Pair<Int, Double>): AxisConvention.Axis = when (pick.first) {
        0 -> if (pick.second > 0) AxisConvention.Axis.X_POS else AxisConvention.Axis.X_NEG
        1 -> if (pick.second > 0) AxisConvention.Axis.Y_POS else AxisConvention.Axis.Y_NEG
        else -> if (pick.second > 0) AxisConvention.Axis.Z_POS else AxisConvention.Axis.Z_NEG
    }

    private fun sign(v: Double) = if (v >= 0) 1.0 else -1.0

    private fun mean(values: FloatArray, from: Int, until: Int): D {
        val count = until - from
        if (count <= 0) return D(0.0, 0.0, 0.0)
        var x = 0.0
        var y = 0.0
        var z = 0.0
        for (i in from until until) {
            x += values[i * 3]
            y += values[i * 3 + 1]
            z += values[i * 3 + 2]
        }
        return D(x / count, y / count, z / count)
    }

    private fun degreesBetween(a: D, b: D) = Math.toDegrees(acos(a.dot(b).coerceIn(-1.0, 1.0)))

    const val MIN_SWING_RADIANS = 0.17
    private const val MAX_GAP_SECONDS = 0.1
    /** Generous on purpose: people nod a little while they tilt. A reversed pair is ~180°. */
    private const val MAX_PAIR_DISAGREEMENT_DEGREES = 40.0
    private const val MAX_THIRD_AXIS_DEGREES = 30.0
    private const val MIN_DOMINANCE = 0.72
    private const val GRAVITY_WRONG_DEGREES = 50.0
}

data class D(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: D) = D(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: D) = D(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = D(x * s, y * s, z * s)
    operator fun unaryMinus() = D(-x, -y, -z)
    operator fun get(i: Int) = when (i) { 0 -> x; 1 -> y; else -> z }
    fun dot(o: D) = x * o.x + y * o.y + z * o.z
    fun cross(o: D) = D(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(dot(this))
    fun normalized() = length().let { if (it == 0.0) this else this * (1.0 / it) }
}
