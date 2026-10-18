package dev.halone.testkit

import dev.halone.testkit.capture.MotionMeter
import dev.halone.testkit.engine.ReportBundle
import dev.halone.testkit.engine.Session
import dev.halone.testkit.profiles.XrealOneProfile
import dev.halone.testkit.profiles.XrealOneStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CaptureTest {

    private val capture = javaClass.getResourceAsStream("/xrealone/one-pro-reports.bin")!!.use { it.readBytes() }

    @Test
    fun `the real capture parses the same however tcp splits it`() {
        fun gyroCount(chunks: List<ByteArray>): Pair<Long, Int> {
            var gyro = 0
            val stream = XrealOneStream { _, _, _, _, _, _, _ -> gyro++ }
            chunks.forEach { stream.feed(it, it.size) }
            return stream.reports to gyro
        }
        val whole = gyroCount(listOf(capture))
        val random = Random(3)
        val pieces = mutableListOf<ByteArray>()
        var at = 0
        while (at < capture.size) {
            val n = minOf(random.nextInt(1, 500), capture.size - at)
            pieces += capture.copyOfRange(at, at + n)
            at += n
        }

        assertEquals(600L, whole.first)
        assertTrue("imu reports ${whole.second}", whole.second in 400..450)
        assertEquals(whole, gyroCount(pieces))
    }

    @Test
    fun `a real unit at rest passes the rest check`() {
        val meter = MotionMeter()
        XrealOneStream { t, gx, gy, gz, _, _, _ -> meter.onGyro(t, gx, gy, gz) }.feed(capture, capture.size)
        val reading = meter.read()
        val rest = XrealOneProfile.MOTIONS.first { it.label == "rest" }.check as XrealOneProfile.Check.Still
        assertTrue("peak ${reading.peakDegreesPerSec}", reading.peakDegreesPerSec <= rest.maxDegreesPerSec)
    }

    @Test
    fun `the meter integrates a steady turn`() {
        val meter = MotionMeter()
        XrealOneStream { t, gx, gy, gz, _, _, _ -> meter.onGyro(t, gx, gy, gz) }.apply { turning(0, 1.0, Math.toRadians(45.0).toFloat()).let { feed(it, it.size) } }
        assertEquals(45.0, meter.read().turnedDegrees, 0.5)
    }

    @Test
    fun `getters match the published request bytes`() {
        assertArrayEquals(
            byteArrayOf(0x27, 0x1F, 0, 0, 0, 6, 0x80.toByte(), 0, 0, 1, 0x18, 0),
            XrealOneProfile.getter(0x271F, 1),
        )
    }

    @Test
    fun `redaction keeps lengths so binary offsets survive`() {
        val bytes = "ab SECRET1 cd SECRET1".toByteArray()
        val out = ReportBundle.redact(bytes, listOf("SECRET1".toByteArray()))
        assertEquals(bytes.size, out.size)
        assertEquals("ab XXXXXXX cd XXXXXXX", String(out))
    }

    @Test
    fun `segments mark both ends and record only inside`() = runTest {
        val session = Session(clock = { testScheduler.currentTime })
        assertFalse(session.recording)
        session.segment("x") { assertTrue(session.recording) }
        assertFalse(session.recording)
        assertEquals(listOf("x:start", "x:end"), session.markers.map { it.label })
    }

    @Test
    fun `short secrets are ignored rather than redacting everywhere`() {
        val session = Session()
        session.secret("ab")
        assertTrue(session.secrets.isEmpty())
    }
}
