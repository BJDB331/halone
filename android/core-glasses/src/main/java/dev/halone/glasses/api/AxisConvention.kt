package dev.halone.glasses.api

import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3

/**
 * The canonical device frame every driver must report in, so that fusion,
 * prediction and the compositor are vendor-independent:
 *
 * - **+X** right, from the wearer's point of view
 * - **+Y** up
 * - **+Z** out of the display, toward the wearer's eyes
 *
 * Right-handed, so a positive rotation about +Y turns the wearer's head to the
 * left. Gyroscope samples are rad/s and accelerometer samples are m/s² in this
 * frame; at rest with the glasses worn level, gravity therefore reads as
 * roughly `(0, +9.81, 0)` because an accelerometer measures the reaction force.
 *
 * Vendors each pick their own axis order and signs, so a driver declares the
 * permutation that maps its hardware frame onto this one. Getting a sign wrong
 * is the single easiest way to make head tracking feel subtly, maddeningly
 * wrong, so [AxisConvention.IDENTITY] should only be claimed once verified on
 * real hardware — see `docs/adding-a-device.md`.
 */
data class AxisConvention(
    val xFrom: Axis,
    val yFrom: Axis,
    val zFrom: Axis,
) {
    enum class Axis(val index: Int, val sign: Float) {
        X_POS(0, 1f), X_NEG(0, -1f),
        Y_POS(1, 1f), Y_NEG(1, -1f),
        Z_POS(2, 1f), Z_NEG(2, -1f),
    }

    fun apply(v: Vec3): Vec3 {
        val c = floatArrayOf(v.x, v.y, v.z)
        return Vec3(
            c[xFrom.index] * xFrom.sign,
            c[yFrom.index] * yFrom.sign,
            c[zFrom.index] * zFrom.sign,
        )
    }

    /**
     * Re-expresses a rotation given in the hardware frame as the same rotation
     * in the canonical one.
     *
     * A change of basis on a rotation is `R Q R⁻¹`, which for a quaternion is
     * just the axis part carried through the permutation with the angle left
     * alone — but only because the permutation is a *proper* rotation. A
     * convention with determinant −1 is a reflection, and a reflected rotation
     * is not a rotation at all, so that case is rejected rather than quietly
     * producing a mirrored world.
     */
    fun apply(q: Quaternion): Quaternion {
        require(isProper) {
            "axis convention $this has determinant -1; a reflection cannot carry a rotation"
        }
        val axis = apply(Vec3(q.x, q.y, q.z))
        return Quaternion(q.w, axis.x, axis.y, axis.z)
    }

    /** True when the permutation is a rotation rather than a reflection. */
    val isProper: Boolean
        get() {
            val used = setOf(xFrom.index, yFrom.index, zFrom.index)
            if (used.size != 3) return false
            // Determinant of the signed permutation matrix: the permutation's
            // own parity times the product of the signs.
            val parity = permutationParity(xFrom.index, yFrom.index, zFrom.index)
            return parity * xFrom.sign * yFrom.sign * zFrom.sign > 0f
        }

    companion object {
        val IDENTITY = AxisConvention(Axis.X_POS, Axis.Y_POS, Axis.Z_POS)

        private fun permutationParity(a: Int, b: Int, c: Int): Float {
            var swaps = 0
            val p = intArrayOf(a, b, c)
            for (i in 0 until 3) {
                for (j in i + 1 until 3) if (p[i] > p[j]) swaps++
            }
            return if (swaps % 2 == 0) 1f else -1f
        }
    }
}
