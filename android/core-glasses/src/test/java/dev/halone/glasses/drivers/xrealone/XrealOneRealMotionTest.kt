package dev.halone.glasses.drivers.xrealone

import dev.halone.glasses.math.Vec3
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The driver's axis convention against a real One Pro wearer's movements (see
 * `fixtures/xrealone/README.md`). The IMU sits ~30° pitched against the head,
 * so components off the main axis are expected; the dominant one and its sign
 * are what the convention has to get right.
 */
class XrealOneRealMotionTest {

    private class Sample(val t: Long, val g: Vec3, val a: Vec3)

    private val segments: Map<String, List<Sample>> by lazy {
        GZIPInputStream(File("../../fixtures/xrealone/one-pro-motions.csv.gz").inputStream()).bufferedReader().useLines { lines ->
            lines.drop(1).map { it.split(',') }.groupBy({ it[0] }) { c ->
                Sample(
                    c[1].toLong(),
                    XrealOneDriver.AXES.apply(Vec3(c[2].toFloat(), c[3].toFloat(), c[4].toFloat())),
                    XrealOneDriver.AXES.apply(Vec3(c[5].toFloat(), c[6].toFloat(), c[7].toFloat())),
                )
            }
        }
    }

    /** Integrated rotation, in degrees, at the furthest point from where the segment started. */
    private fun swing(label: String): Vec3 {
        val samples = segments.getValue(label)
        val bias = mean(segments.getValue("rest").map { it.g })
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var best = Vec3.ZERO
        for (i in 1 until samples.size) {
            val dt = (samples[i].t - samples[i - 1].t) / 1e9
            x += (samples[i].g.x - bias.x) * dt
            y += (samples[i].g.y - bias.y) * dt
            z += (samples[i].g.z - bias.z) * dt
            val v = Vec3(Math.toDegrees(x).toFloat(), Math.toDegrees(y).toFloat(), Math.toDegrees(z).toFloat())
            if (v.length > best.length) best = v
        }
        return best
    }

    private fun mean(vs: List<Vec3>) = Vec3(vs.map { it.x }.average().toFloat(), vs.map { it.y }.average().toFloat(), vs.map { it.z }.average().toFloat())

    @Test
    fun `worn level, gravity reads up`() {
        val g = mean(segments.getValue("level").map { it.a })
        assertTrue("gravity $g", g.y > 0.8f * g.length)
    }

    @Test
    fun `turning left and right is mostly a positive and negative yaw`() {
        val left = swing("turn-left")
        val right = swing("turn-right")
        assertTrue("left $left", left.y > 20f && left.y > kotlin.math.abs(left.x))
        assertTrue("right $right", right.y < -20f && -right.y > kotlin.math.abs(right.x))
    }

    @Test
    fun `looking up and down is a positive and negative pitch`() {
        val up = swing("look-up")
        val down = swing("look-down")
        assertTrue("up $up", up.x > 10f && up.x > kotlin.math.abs(up.y))
        assertTrue("down $down", down.x < -10f && -down.x > kotlin.math.abs(down.y))
    }
}
