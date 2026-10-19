package dev.halone.glasses.drivers.xrealone

import dev.halone.glasses.api.AxisConvention
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class XrealOneCaptureTest {

    private val messages = XrealOneCaptures.messages(XrealOneCaptures.REPORTS)

    @Test
    fun `the capture frames cleanly into report messages`() {
        assertEquals(600, messages.size)
        assertTrue(messages.all { (magic, body) -> magic == XrealOneProtocol.MSG_REPORT && body.size == XrealOneReport.SIZE })
    }

    @Test
    fun `arbitrary tcp segmentation yields the same messages`() {
        val random = Random(7)
        val framer = XrealOneFramer(4096)
        val out = mutableListOf<ByteArray>()
        var at = 0
        val stream = XrealOneCaptures.REPORTS
        while (at < stream.size) {
            val n = minOf(random.nextInt(1, 700), stream.size - at)
            framer.append(stream.copyOfRange(at, at + n), n)
            framer.drain { _, data, offset, length -> out += data.copyOfRange(offset, offset + length) }
            at += n
        }
        assertEquals(messages.size, out.size)
        messages.zip(out).forEach { (expected, actual) -> assertArrayEquals(expected.second, actual) }
    }

    @Test
    fun `garbage ahead of the stream is skipped`() {
        val garbage = ByteArray(37) { 0x7F }
        val recovered = XrealOneFramer(4096).run {
            val stream = garbage + XrealOneCaptures.REPORTS
            append(stream, stream.size)
            var count = 0
            drain { magic, _, _, length -> if (magic == XrealOneProtocol.MSG_REPORT && length == 128) count++ }
            count
        }
        assertTrue("recovered $recovered", recovered >= messages.size - 1)
    }

    @Test
    fun `imu and magnetometer reports interleave at their own rates`() {
        val types = messages.groupBy { (_, body) -> XrealOneReport.type(body, 0) }
        val imu = types.getValue(XrealOneReport.TYPE_IMU)
        val mag = types.getValue(XrealOneReport.TYPE_MAGNETOMETER)
        assertEquals(messages.size, imu.size + mag.size)

        val imuPeriodNs = medianPeriod(imu.map { XrealOneReport.timestampNs(it.second, 0) })
        val magPeriodNs = medianPeriod(mag.map { XrealOneReport.timestampNs(it.second, 0) })
        assertEquals(1_000_000.0, imuPeriodNs, 50_000.0)
        assertEquals(2_500_000.0, magPeriodNs, 50_000.0)
    }

    @Test
    fun `frame counters run without gaps`() {
        val imu = messages.filter { (_, body) -> XrealOneReport.type(body, 0) == XrealOneReport.TYPE_IMU }
        val counters = imu.map { XrealOneReport.frameCounter(it.second, 0) }
        assertTrue(counters.zipWithNext().all { (a, b) -> b == a + 1 })
    }

    @Test
    fun `magnetometer reports carry no inertial sample`() {
        val (_, body) = messages.first { (_, body) -> XrealOneReport.type(body, 0) == XrealOneReport.TYPE_MAGNETOMETER }
        assertNull(XrealOneReport.inertial(body, 0, XrealOneDriver.AXES))
    }

    @Test
    fun `remapped gravity in the capture reads one g on +Y`() {
        val samples = messages.mapNotNull { (_, body) -> XrealOneReport.inertial(body, 0, XrealOneDriver.AXES) }
        val accel = samples.map { it.accel }
        val meanY = accel.map { it.y }.average()
        val magnitude = accel.map { it.length }.average()

        assertEquals(9.8, magnitude, 0.2)
        assertTrue("gravity should read on +Y once remapped, was $meanY", meanY > 7.0)
        assertTrue(
            "gyro at rest should be near zero",
            samples.all { it.gyro.length < 0.1f },
        )
    }

    @Test
    fun `the axis convention is a proper rotation`() {
        assertTrue(XrealOneDriver.AXES.isProper)
        assertTrue(XrealOneDriver.AXES != AxisConvention.IDENTITY)
    }

    private fun medianPeriod(timestamps: List<Long>): Double =
        timestamps.zipWithNext { a, b -> (b - a).toDouble() }.sorted().let { it[it.size / 2] }
}
