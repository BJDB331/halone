package dev.halone.glasses.input

import dev.halone.glasses.api.TrackingSample
import dev.halone.glasses.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

/**
 * Against a worn session (`tools/tap-fixture.py`): every cued tap, and the
 * movements a detector must ignore, as the Air 2 Pro reported them.
 */
class TapDetectorTest {

    private class Row(val sample: TrackingSample.Inertial, val segment: String)

    private fun load(name: String): List<Row> {
        val stream = javaClass.classLoader!!.getResourceAsStream("taps/$name") ?: error("missing fixture $name")
        return GZIPInputStream(stream).bufferedReader().readLines().drop(1).map { line ->
            val f = line.split(',')
            val accel = Vec3(f[1].toFloat(), f[2].toFloat(), f[3].toFloat())
            Row(TrackingSample.Inertial(f[0].toLong(), Vec3.ZERO, accel), f[4])
        }
    }

    private fun detectBySegment(rows: List<Row>): Map<String, List<Tap>> {
        val found = LinkedHashMap<String, MutableList<Tap>>()
        var detector = TapDetector()
        var segment = ""
        for (row in rows) {
            if (row.segment != segment) {
                segment = row.segment
                detector = TapDetector()
                found[segment] = mutableListOf()
            }
            detector.onSample(row.sample)?.let { found.getValue(segment) += it }
        }
        return found
    }

    @Test
    fun `every worn tap is found once, on the side it was made`() {
        val found = detectBySegment(load("taps.csv.gz"))

        assertEquals(21, found.size)
        for ((segment, taps) in found) {
            val expectedCount = segment.substringAfter('-')[0].digitToInt()
            val expectedSide = if (segment.last() == 'L') TapSide.LEFT else TapSide.RIGHT
            assertEquals("taps in $segment", expectedCount, taps.size)
            assertTrue("side in $segment: $taps", taps.all { it.side == expectedSide })
        }
    }

    @Test
    fun `adjusting the glasses, talking and looking around are not taps`() {
        val found = detectBySegment(load("movements.csv.gz"))

        assertEquals(setOf("adjust", "talk", "turn"), found.keys)
        found.forEach { (segment, taps) -> assertEquals("false taps during $segment: $taps", 0, taps.size) }
    }

    @Test
    fun `nothing is reported before the average has filled`() {
        val detector = TapDetector()
        repeat(10) { i ->
            assertNull(detector.onSample(TrackingSample.Inertial(i * 1_000_000L, Vec3.ZERO, Vec3(0f, if (i == 5) 80f else 9.8f, 0f))))
        }
    }

    @Test
    fun `a tap is reported once its peak has passed, with the side of the impulse`() {
        val detector = TapDetector()
        val reported = mutableListOf<Tap>()
        for (i in 0 until 200) {
            val x = when (i) {
                100 -> -12f
                101 -> -30f
                102 -> -8f
                else -> 0f
            }
            detector.onSample(TrackingSample.Inertial(i * 1_000_000L, Vec3.ZERO, Vec3(x, 9.8f, 0f)))?.let { reported += it }
        }

        assertEquals(1, reported.size)
        assertEquals(TapSide.RIGHT, reported[0].side)
        assertEquals(100_000_000L, reported[0].timestampNs)
        assertEquals(30f, reported[0].peakMps2, 1f)
    }
}
