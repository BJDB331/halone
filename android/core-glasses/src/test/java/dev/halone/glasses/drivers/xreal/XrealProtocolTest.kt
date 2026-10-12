package dev.halone.glasses.drivers.xreal

import org.junit.Assert.assertEquals
import org.junit.Test

class XrealProtocolTest {

    private fun u16(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    @Test
    fun `imu command framing`() {
        val p = XrealProtocol.startImuStream()

        assertEquals(8 + 1, p.size)
        assertEquals(0xAA.toByte(), p[0])
        assertEquals(3 + 1, u16(p, 5))
        assertEquals(XrealProtocol.IMU_MSG_START_IMU_DATA, p[7].toInt())
        assertEquals(0x01.toByte(), p[8])
    }

    /** Expected value computed independently with Python's zlib, not with this code. */
    @Test
    fun `imu checksum matches independently computed crc32`() {
        assertEquals(0x4221d1c5L, u32(XrealProtocol.startImuStream(), 1))
    }

    @Test
    fun `mcu command framing`() {
        val p = XrealProtocol.writeDisplayMode(3)

        assertEquals(22 + 1, p.size)
        assertEquals(0xFD.toByte(), p[0])
        assertEquals(17 + 1, u16(p, 5))
        assertEquals(0L, u32(p, 7))
        assertEquals(XrealProtocol.MCU_MSG_W_DISP_MODE, XrealProtocol.mcuMsgId(p))
        assertEquals(3.toByte(), p[XrealProtocol.MCU_DATA_OFFSET])
    }

    @Test
    fun `mcu checksum matches independently computed crc32`() {
        assertEquals(0x0680b2d9L, u32(XrealProtocol.writeDisplayMode(3), 1))
    }

    @Test
    fun `imu and mcu use different packet heads`() {
        assertEquals(0xAA.toByte(), XrealProtocol.stopImuStream()[0])
        assertEquals(0xFD.toByte(), XrealProtocol.readDisplayMode()[0])
    }

    @Test
    fun `msgid is read from a real captured reply`() {
        assertEquals(0x07, XrealProtocol.mcuMsgId(XrealCaptures.REPLY_MODE_SBS))
        assertEquals(0x08, XrealProtocol.mcuMsgId(XrealCaptures.REPLY_MODE_WRITE_ACK))
        assertEquals(0x6C18, XrealProtocol.mcuMsgId(XrealCaptures.ASYNC_UNSOLICITED))
    }

    /**
     * Guards the trap that cost us real debugging time: a reply's payload sits
     * one byte later than a command's, behind a status byte.
     */
    @Test
    fun `reply payload sits one byte after the status byte`() {
        assertEquals(XrealProtocol.MCU_DATA_OFFSET + 1, XrealProtocol.MCU_REPLY_DATA_OFFSET)

        val sbs = XrealCaptures.REPLY_MODE_SBS
        assertEquals(0, sbs[XrealProtocol.MCU_REPLY_STATUS_OFFSET].toInt())
        assertEquals(3, sbs[XrealProtocol.MCU_REPLY_DATA_OFFSET].toInt())

        val mirror = XrealCaptures.REPLY_MODE_MIRROR
        assertEquals(0, mirror[XrealProtocol.MCU_REPLY_STATUS_OFFSET].toInt())
        assertEquals(1, mirror[XrealProtocol.MCU_REPLY_DATA_OFFSET].toInt())
    }
}
