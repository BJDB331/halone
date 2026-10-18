package dev.halone.glasses.drivers.rayneo

import dev.halone.glasses.math.Vec3
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RayneoProtocolTest {

    @Test
    fun `commands are a bare 64-byte report starting at the tag`() {
        val frame = RayneoProtocol.command(RayneoProtocol.CMD_BRIGHTNESS, 5)
        assertEquals(64, frame.size)
        assertArrayEquals(byteArrayOf(0x66, 0x09, 0x05), frame.copyOfRange(0, 3))
        assertTrue("padding must be zero", frame.drop(3).all { it == 0.toByte() })
    }

    @Test
    fun `imu report fields come from the published offsets`() {
        val imu = RayneoProtocol.parse(RayneoCaptures.IMU_AT_REST, 64) as RayneoReport.Imu
        assertVec(Vec3(0.05f, -0.12f, 9.84f), imu.accel)
        assertVec(Vec3(0.4f, -0.3f, 0.2f), imu.gyroDegPerSec)
        // Z is at 52, not next to X and Y.
        assertVec(Vec3(21f, -14f, 38f), imu.magnetMicrotesla)
        assertEquals(39.4f, imu.temperatureC, 1e-4f)
        assertEquals(1_000_000L, imu.tick)
    }

    @Test
    fun `the tick is read unsigned`() {
        val imu = RayneoProtocol.parse(RayneoCaptures.imu(tick = 0xFFFF_FFF0L), 64) as RayneoReport.Imu
        assertEquals(0xFFFF_FFF0L, imu.tick)
    }

    @Test
    fun `a leading report id is tolerated`() {
        val withId = byteArrayOf(0) + RayneoCaptures.IMU_AT_REST
        val imu = RayneoProtocol.parse(withId, withId.size) as RayneoReport.Imu
        assertEquals(1_000_000L, imu.tick)
    }

    @Test
    fun `an acknowledgement carries board and echoed opcode`() {
        val ack = RayneoProtocol.parse(RayneoCaptures.ack(0x09, board = 0x40), 64) as RayneoReport.Ack
        assertEquals(RayneoProtocol.BOARD_GT, ack.board)
        assertEquals(0x09, ack.opcode)
    }

    @Test
    fun `device info yields build date and brightness`() {
        val ack = RayneoProtocol.parse(RayneoCaptures.deviceInfo(brightness = 6), 64) as RayneoReport.Ack
        assertEquals("Sep 22 2026", RayneoProtocol.buildDate(ack))
        assertEquals(6, RayneoProtocol.brightness(ack))
        assertEquals("GT Max", RayneoProtocol.boardName(ack.board))
    }

    @Test
    fun `garbage in the build field is not reported as a date`() {
        val raw = RayneoCaptures.ack(0x00).also { it[23] = 0x01 }
        val ack = RayneoProtocol.parse(raw, 64) as RayneoReport.Ack
        assertNull(RayneoProtocol.buildDate(ack))
    }

    @Test
    fun `foreign, short and non-finite reports are not samples`() {
        val foreign = ByteArray(64).also { it[0] = 0x01; it[1] = 0x02 }
        assertTrue(RayneoProtocol.parse(foreign, 64) is RayneoReport.Unrecognised)
        assertTrue(RayneoProtocol.parse(RayneoCaptures.IMU_AT_REST, 32) is RayneoReport.Unrecognised)
        val nan = RayneoCaptures.imu(tick = 1, gyroDeg = floatArrayOf(Float.NaN, 0f, 0f))
        assertTrue(RayneoProtocol.parse(nan, 64) is RayneoReport.Unrecognised)
    }

    @Test
    fun `anchor modes are numbered per board`() {
        val p = RayneoProtocol
        assertEquals(0, p.anchorValue(p.AnchorMode.FOLLOW, p.BOARD_GT))
        assertEquals(1, p.anchorValue(p.AnchorMode.PINNED, p.BOARD_GT))
        assertEquals(0, p.anchorValue(p.AnchorMode.FOLLOW, p.BOARD_GT_MAX))
        assertEquals(2, p.anchorValue(p.AnchorMode.PINNED, p.BOARD_GT_MAX))
    }

    @Test
    fun `axis convention is a proper rotation`() {
        assertTrue(RayneoDriver.AXES.isProper)
    }

    /**
     * Worn level, gravity reads on sensor +Z; the canonical frame wants it on
     * +Y. Forward is sensor +X, which is canonical −Z; left is sensor +Y,
     * which is canonical −X.
     */
    @Test
    fun `axis convention puts gravity up, forward ahead and left left`() {
        assertVec(Vec3(0f, 9.81f, 0f), RayneoDriver.AXES.apply(Vec3(0f, 0f, 9.81f)))
        assertVec(Vec3(0f, 0f, -1f), RayneoDriver.AXES.apply(Vec3(1f, 0f, 0f)))
        assertVec(Vec3(-1f, 0f, 0f), RayneoDriver.AXES.apply(Vec3(0f, 1f, 0f)))
    }

    /**
     * The GT Max symptom of the first mapping: a nod — rotation about the
     * left-right axis, sensor Y — must come out as pitch (canonical X), not
     * as roll (canonical Z).
     */
    @Test
    fun `a nod is pitch, not roll`() {
        val nod = RayneoDriver.AXES.apply(Vec3(0f, 1f, 0f))
        assertEquals(0f, nod.z, 1e-6f)
        assertEquals(1f, kotlin.math.abs(nod.x), 1e-6f)
    }

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals("x", expected.x, actual.x, 1e-4f)
        assertEquals("y", expected.y, actual.y, 1e-4f)
        assertEquals("z", expected.z, actual.z, 1e-4f)
    }
}
