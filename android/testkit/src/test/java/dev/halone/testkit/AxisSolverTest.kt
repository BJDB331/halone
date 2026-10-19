package dev.halone.testkit

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.AxisConvention.Axis
import dev.halone.testkit.analysis.AxisSolver
import dev.halone.testkit.analysis.AxisSolver.Status
import dev.halone.testkit.analysis.D
import dev.halone.testkit.capture.SegmentRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic glasses: a known convention, an IMU tilted [mountDegrees] about the
 * head's X axis, gyro noise and bias. The solver has to recover the first two.
 */
class AxisSolverTest {

    private class Glasses(val convention: AxisConvention, val mountDegrees: Double, seed: Int = 1) {
        private val random = Random(seed)
        private val bias = D(0.004, -0.007, 0.002)

        /** A head-frame vector as the hardware reports it. */
        fun hardware(head: D): D {
            val a = Math.toRadians(mountDegrees)
            val unmounted = D(head.x, cos(a) * head.y + sin(a) * head.z, -sin(a) * head.y + cos(a) * head.z)
            return toHardware(convention, unmounted)
        }

        /** Swing out about [axis] for a second, hold, swing back: the shape the instructions ask for. */
        fun motion(axis: D, degrees: Double = 40.0): SegmentRecorder.Samples {
            val recorder = SegmentRecorder()
            recorder.begin("m")
            val rate = Math.toRadians(degrees)
            val gravity = hardware(D(0.0, 9.8, 0.0))
            for (i in 0 until 3000) {
                val speed = when {
                    i < 1000 -> rate
                    i < 2000 -> 0.0
                    else -> -rate
                }
                val w = hardware(axis * speed)
                recorder.onImu(
                    i * 1_000_000L,
                    (w.x + bias.x + random.nextDouble(-0.01, 0.01)).toFloat(),
                    (w.y + bias.y + random.nextDouble(-0.01, 0.01)).toFloat(),
                    (w.z + bias.z + random.nextDouble(-0.01, 0.01)).toFloat(),
                    gravity.x.toFloat(), gravity.y.toFloat(), gravity.z.toFloat(),
                )
            }
            recorder.end()
            return recorder.samples("m")!!
        }

        fun swings(flip: Set<String> = emptySet(), skip: Set<String> = emptySet()): Map<String, D> =
            AxisSolver.EXPECTED.filterKeys { it !in skip }.mapValues { (label, axis) ->
                AxisSolver.swing(motion(if (label in flip) -axis else axis), bias)
            }

        fun gravity() = hardware(D(0.0, 9.8, 0.0))
    }

    private val oneS = AxisConvention(Axis.X_POS, Axis.Y_NEG, Axis.Z_NEG)
    private val air = AxisConvention(Axis.X_NEG, Axis.Z_POS, Axis.Y_POS)

    @Test
    fun `recovers the 1S convention through a 35 degree mount`() {
        val glasses = Glasses(oneS, mountDegrees = 35.0)
        val result = AxisSolver.solve(glasses.swings(), glasses.gravity())

        assertEquals(result.problems.toString(), Status.CONFIDENT, result.status)
        assertEquals(oneS, result.convention)
        assertEquals(35.0, result.mountAngleDegrees!!, 2.0)
        assertEquals(35.0, abs(result.mountPitchDegrees!!), 2.0)
        assertTrue(result.gravityFromUpDegrees!! < 3.0)
    }

    @Test
    fun `recovers the Air convention with no mount`() {
        val glasses = Glasses(air, mountDegrees = 0.0, seed = 2)
        val result = AxisSolver.solve(glasses.swings(), glasses.gravity())

        assertEquals(Status.CONFIDENT, result.status)
        assertEquals(air, result.convention)
        assertTrue(result.mountAngleDegrees!! < 2.0)
    }

    @Test
    fun `every proper convention is recoverable`() {
        val axes = Axis.entries
        var tried = 0
        for (x in axes) for (y in axes) for (z in axes) {
            val convention = AxisConvention(x, y, z)
            if (!convention.isProper) continue
            tried++
            val glasses = Glasses(convention, mountDegrees = -20.0, seed = tried)
            val result = AxisSolver.solve(glasses.swings(), glasses.gravity())
            assertEquals("$convention", convention, result.convention)
            assertEquals("$convention", Status.CONFIDENT, result.status)
        }
        assertEquals(24, tried)
    }

    @Test
    fun `turning right when asked left, both times, is caught by gravity`() {
        val glasses = Glasses(oneS, mountDegrees = 10.0)
        val result = AxisSolver.solve(glasses.swings(flip = setOf("turn-left", "turn-right")), glasses.gravity())

        assertEquals(Status.AMBIGUOUS, result.status)
        assertTrue(result.problems.any { it.startsWith("gravity") })
    }

    @Test
    fun `mixed-up tilts are caught by the other two axes and asked for again`() {
        val glasses = Glasses(oneS, mountDegrees = 10.0)
        val result = AxisSolver.solve(glasses.swings(flip = setOf("tilt-left", "tilt-right")), glasses.gravity())

        assertEquals(Status.AMBIGUOUS, result.status)
        assertEquals(setOf("tilt-left", "tilt-right"), result.redo)
    }

    @Test
    fun `one motion done backwards drops just its pair and still finds the frame`() {
        val glasses = Glasses(oneS, mountDegrees = 10.0)
        val result = AxisSolver.solve(glasses.swings(flip = setOf("look-up")), glasses.gravity())

        assertEquals(Status.INFERRED, result.status)
        assertEquals(setOf("look-up", "look-down"), result.redo)
        assertEquals(oneS, result.convention)
    }

    /** What the second real tester did: a small settling motion right after the beep, the real one seconds later. */
    @Test
    fun `the swing is the furthest excursion after the beep, not the first motion`() {
        val recorder = SegmentRecorder()
        recorder.begin("m")
        var t = 0L
        fun hold(rate: D, ms: Int) = repeat(ms) {
            recorder.onImu(t, rate.x.toFloat(), rate.y.toFloat(), rate.z.toFloat(), 0f, 9.8f, 0f)
            t += 1_000_000L
        }
        hold(D(0.0, -0.6, 0.0), 800)
        recorder.go()
        hold(D(0.4, 0.0, 0.0), 500)
        hold(D(-0.4, 0.0, 0.0), 500)
        hold(D(0.0, 0.0, 0.0), 1500)
        hold(D(0.0, 0.0, 0.5), 1000)
        hold(D(0.0, 0.0, -0.5), 1000)
        recorder.end()

        val swing = AxisSolver.swing(recorder.samples("m")!!, D(0.0, 0.0, 0.0))
        assertTrue("picked $swing", swing.z > 0.45 && abs(swing.x) < 0.05 && abs(swing.y) < 0.05)
    }

    @Test
    fun `missing tilts still give an inferred answer`() {
        val glasses = Glasses(oneS, mountDegrees = 35.0)
        val result = AxisSolver.solve(glasses.swings(skip = setOf("tilt-left", "tilt-right")), glasses.gravity())

        assertEquals(Status.INFERRED, result.status)
        assertEquals(oneS, result.convention)
        assertNotNull(result.mountAngleDegrees)
    }

    @Test
    fun `no gravity means the yaw sign cannot be trusted`() {
        val glasses = Glasses(oneS, mountDegrees = 0.0)
        val result = AxisSolver.solve(glasses.swings(), gravity = null)

        assertEquals(Status.AMBIGUOUS, result.status)
    }

    @Test
    fun `the result serialises`() {
        val glasses = Glasses(oneS, mountDegrees = 35.0)
        val json = AxisSolver.toJson(AxisSolver.solve(glasses.swings(), glasses.gravity()))
        assertEquals("confident", json.getString("status"))
        assertEquals("x=X_POS y=Y_NEG z=Z_NEG", json.getString("convention"))
    }
}
