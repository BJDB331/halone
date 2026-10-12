package dev.halone.glasses.fusion

import dev.halone.glasses.math.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Estimates the constant magnetic offset the glasses carry around with them.
 *
 * A magnetometer bolted next to an OLED panel and a DP bridge reads the Earth's
 * field plus whatever the hardware itself is doing, and on this unit the second
 * part is not small: a probe while worn found the heading error swinging 21.5°
 * across 45° of head rotation. That is not noise to be gated out — it is a
 * repeatable function of where the wearer is facing, and no rejection threshold
 * can fix it, because the reading really is that wrong.
 *
 * The fix is geometric. If the true field has constant magnitude, then every
 * sample of it lies on a sphere centred at the origin; measured with a constant
 * offset `c` added, they lie on a sphere centred at `c`. Finding `c` is
 * therefore a sphere fit, and it linearises exactly:
 *
 * ```
 * |m − c|² = r²   →   2·m·c + (r² − |c|²) = |m|²
 * ```
 *
 * which is linear in the four unknowns `(2c, r² − |c|²)`. Four normal equations,
 * accumulated incrementally so nothing has to be stored.
 *
 * ### Why this replaced a min/max midpoint
 *
 * Min/max is only the sphere fit's answer when the extremes along each axis
 * have actually been visited, and a wearer at a desk sweeps maybe 90° of yaw.
 * The midpoint of a partial arc is not the centre of the circle it lies on —
 * it is somewhere inside it — so the estimate was both wrong and confidently
 * available. This is the same quantity, solved rather than approximated.
 *
 * ### The conditioning problem, which is real
 *
 * Head motion is mostly yaw, so samples cluster near a horizontal *circle*
 * rather than covering a sphere. A circle does not determine a sphere's centre
 * along its axis: that direction is unobservable, and a least-squares solve
 * asked for it anyway will return a confident, arbitrary number. So the fit is
 * only published once the sample cloud has real extent in all three directions
 * — see [conditioning] — and otherwise reports nothing at all, which is the
 * honest answer and lets the caller carry on uncorrected.
 */
class HardIronEstimator {

    private var n = 0L
    // Accumulated normal equations for A'A x = A'b, with A rows [mx, my, mz, 1]
    // and b = |m|².
    private val ata = Array(4) { DoubleArray(4) }
    private val atb = DoubleArray(4)

    // Running mean and variance per axis, for the conditioning test.
    private var mean = Vec3.ZERO
    private var m2 = Vec3.ZERO

    @Volatile
    var offset: Vec3? = null
        private set

    @Volatile
    var radius: Float = Float.NaN
        private set

    val samples: Long get() = n

    /**
     * Spread of the sample cloud along its weakest axis, in counts.
     *
     * Cheap stand-in for the smallest singular value: if every axis has moved,
     * the fit is posed; if one has not, it is not. Using per-axis standard
     * deviation rather than a proper decomposition is a simplification, and it
     * is conservative in the direction that matters — it can refuse a fit that
     * would have been fine, never accept one that is degenerate along an axis.
     */
    val conditioning: Float
        get() {
            if (n < 2) return 0f
            val v = Vec3(
                sqrt(m2.x / (n - 1)), sqrt(m2.y / (n - 1)), sqrt(m2.z / (n - 1))
            )
            return minOf(v.x, v.y, v.z)
        }

    val converged: Boolean
        get() = n >= MIN_SAMPLES && conditioning >= MIN_SPREAD_COUNTS

    fun reset() {
        n = 0
        for (row in ata) row.fill(0.0)
        atb.fill(0.0)
        mean = Vec3.ZERO
        m2 = Vec3.ZERO
        offset = null
        radius = Float.NaN
    }

    fun add(m: Vec3) {
        n++
        val delta = m - mean
        mean = Vec3(
            mean.x + delta.x / n, mean.y + delta.y / n, mean.z + delta.z / n
        )
        val delta2 = m - mean
        m2 = Vec3(
            m2.x + delta.x * delta2.x,
            m2.y + delta.y * delta2.y,
            m2.z + delta.z * delta2.z,
        )

        val row = doubleArrayOf(m.x.toDouble(), m.y.toDouble(), m.z.toDouble(), 1.0)
        val b = (m.x.toDouble() * m.x + m.y.toDouble() * m.y + m.z.toDouble() * m.z)
        for (i in 0 until 4) {
            for (j in 0 until 4) ata[i][j] += row[i] * row[j]
            atb[i] += row[i] * b
        }
    }

    /** Solves for the offset, or returns null while the fit is not yet posed. */
    fun solve(): Vec3? {
        if (!converged) return null
        val x = solve4(ata, atb) ?: return null
        val c = Vec3((x[0] / 2).toFloat(), (x[1] / 2).toFloat(), (x[2] / 2).toFloat())
        val rSquared = x[3] + (c.x.toDouble() * c.x + c.y.toDouble() * c.y + c.z.toDouble() * c.z)
        if (rSquared <= 0 || !c.x.isFinite() || !c.y.isFinite() || !c.z.isFinite()) return null
        offset = c
        radius = sqrt(rSquared).toFloat()
        return c
    }

    /** Gaussian elimination with partial pivoting; null if singular. */
    private fun solve4(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val m = Array(4) { i -> DoubleArray(5) { j -> if (j < 4) a[i][j] else b[i] } }
        for (col in 0 until 4) {
            var pivot = col
            for (r in col + 1 until 4) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            if (abs(m[pivot][col]) < EPSILON) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (r in 0 until 4) {
                if (r == col) continue
                val f = m[r][col] / m[col][col]
                for (c2 in col until 5) m[r][c2] -= f * m[col][c2]
            }
        }
        return DoubleArray(4) { m[it][4] / m[it][it] }
    }

    private companion object {
        const val MIN_SAMPLES = 500L

        /**
         * Counts of standard deviation required on the weakest axis. The field
         * is a few hundred counts, so this asks for real motion rather than the
         * jitter of a head held still.
         */
        const val MIN_SPREAD_COUNTS = 25f
        const val EPSILON = 1e-9
    }
}
