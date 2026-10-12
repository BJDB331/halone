package dev.halone.glasses.fusion

import dev.halone.glasses.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class HardIronEstimatorTest {

    /** Points on a sphere of [radius] about [centre], spread over both angles. */
    private fun sphere(centre: Vec3, radius: Float, steps: Int = 40): List<Vec3> =
        buildList {
            for (i in 0 until steps) {
                val yaw = i * 2.0 * Math.PI / steps
                for (j in 0 until steps / 2) {
                    val pitch = -Math.PI / 3 + j * (2 * Math.PI / 3) / (steps / 2 - 1)
                    add(
                        Vec3(
                            centre.x + (radius * cos(pitch) * cos(yaw)).toFloat(),
                            centre.y + (radius * sin(pitch)).toFloat(),
                            centre.z + (radius * cos(pitch) * sin(yaw)).toFloat(),
                        )
                    )
                }
            }
        }

    @Test
    fun `recovers a known offset from a well spread cloud`() {
        val truth = Vec3(212f, -95f, 271f)
        val estimator = HardIronEstimator()
        sphere(truth, 390f).forEach { estimator.add(it) }

        val solved = estimator.solve()
        assertNotNull(solved)
        assertEquals(truth.x, solved!!.x, 0.5f)
        assertEquals(truth.y, solved.y, 0.5f)
        assertEquals(truth.z, solved.z, 0.5f)
        assertEquals(390f, estimator.radius, 0.5f)
    }

    /**
     * The failure mode that matters, and the one min/max had no way to notice.
     *
     * A wearer at a desk turns their head in yaw and very little else, so the
     * samples lie near a horizontal circle. A circle does not determine a
     * sphere's centre along its axis — that direction is unobservable — and a
     * solver asked for it anyway returns a confident, arbitrary number. Refusing
     * is the only honest answer.
     */
    @Test
    fun `refuses a fit when the samples lie in a plane`() {
        val estimator = HardIronEstimator()
        repeat(2000) { i ->
            val yaw = i * 2.0 * Math.PI / 60
            estimator.add(
                Vec3(212f + (390 * cos(yaw)).toFloat(), -95f, 271f + (390 * sin(yaw)).toFloat())
            )
        }
        assertFalse(estimator.converged)
        assertNull(estimator.solve())
    }

    @Test
    fun `refuses a fit before enough samples`() {
        val estimator = HardIronEstimator()
        sphere(Vec3(10f, 10f, 10f), 300f, steps = 6).forEach { estimator.add(it) }
        assertTrue(estimator.samples < 500)
        assertNull(estimator.solve())
    }

    @Test
    fun `a stationary sensor never becomes confident`() {
        val estimator = HardIronEstimator()
        repeat(5000) { estimator.add(Vec3(212.1f, -95.2f, 271.3f)) }
        assertFalse("no motion means no information", estimator.converged)
        assertNull(estimator.solve())
    }

    @Test
    fun `tolerates noise on the samples`() {
        val truth = Vec3(-40f, 120f, 15f)
        val estimator = HardIronEstimator()
        val random = java.util.Random(7)
        sphere(truth, 420f).forEach {
            estimator.add(
                Vec3(
                    it.x + random.nextGaussian().toFloat() * 4f,
                    it.y + random.nextGaussian().toFloat() * 4f,
                    it.z + random.nextGaussian().toFloat() * 4f,
                )
            )
        }
        val solved = estimator.solve()!!
        assertEquals(truth.x, solved.x, 6f)
        assertEquals(truth.y, solved.y, 6f)
        assertEquals(truth.z, solved.z, 6f)
    }

    @Test
    fun `reset clears everything`() {
        val estimator = HardIronEstimator()
        sphere(Vec3(1f, 2f, 3f), 300f).forEach { estimator.add(it) }
        assertNotNull(estimator.solve())
        estimator.reset()
        assertEquals(0L, estimator.samples)
        assertNull(estimator.offset)
        assertFalse(estimator.converged)
    }
}
