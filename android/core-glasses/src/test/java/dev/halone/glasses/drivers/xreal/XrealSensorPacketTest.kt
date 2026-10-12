package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.TrackingSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class XrealSensorPacketTest {

    private val axes = AxisConvention.IDENTITY
    private val xrealAxes = XrealDriver.AXES

    private fun parse(buf: ByteArray) =
        XrealSensorPacket.parse(buf, buf.size, axes)

    private fun sample(buf: ByteArray): TrackingSample.Inertial {
        val r = parse(buf)
        assertTrue("expected a sensor frame, got $r", r is XrealPacket.Sensor)
        return (r as XrealPacket.Sensor).sample
    }

    /**
     * Values cross-checked by hand against the raw bytes: gyro scaling is
     * mult=4000 div=16777216 in degrees, accel mult=32 div=16777216 in g.
     */
    @Test
    fun `parses a real frame captured at rest`() {
        val s = sample(XrealCaptures.SENSOR_AT_REST)

        assertEquals(211_332_311_000L, s.timestampNs)
        assertEquals(0.006658f, s.gyro.x, 1e-5f)
        assertEquals(0.000799f, s.gyro.y, 1e-5f)
        assertEquals(0.008256f, s.gyro.z, 1e-5f)
        assertEquals(-0.1689f, s.accel.x, 1e-3f)
        assertEquals(2.7423f, s.accel.y, 1e-3f)
        assertEquals(9.7489f, s.accel.z, 1e-3f)
    }

    /** At rest the total should be one gravity; the sensor reads ~3% high. */
    @Test
    fun `accelerometer magnitude at rest is close to one g`() {
        val s = sample(XrealCaptures.SENSOR_AT_REST)
        assertEquals(9.81f, s.accel.length, 0.45f)
    }

    @Test
    fun `magnetometer is not reported in physical units because its scale is unknown`() {
        val parsed = sample(XrealCaptures.SENSOR_AT_REST)
        assertNull(parsed.magnet)
        // The counts are still published, so a spike can ask whether the sensor
        // is alive without the driver having to claim it knows the units.
        assertNotNull(parsed.magnetRaw)
    }

    /** Unsigned and biased at 0x8000, unlike the two's-complement gyro/accel. */
    @Test
    fun `raw magnetometer counts are unsigned biased at 0x8000`() {
        val m = XrealSensorPacket.rawMagnetCounts(XrealCaptures.SENSOR_AT_REST)
        assertEquals(-85f, m.x, 0f)
        assertEquals(24f, m.y, 0f)
        assertEquals(370f, m.z, 0f)
    }

    @Test
    fun `command replies on the sensor endpoint are not mistaken for sensor data`() {
        val r = parse(XrealCaptures.IMU_COMMAND_ACK)
        assertTrue("expected a command reply, got $r", r is XrealPacket.CommandReply)
        assertEquals(XrealProtocol.IMU_MSG_START_IMU_DATA, (r as XrealPacket.CommandReply).msgId)
    }

    @Test
    fun `unknown signature is rejected`() {
        val junk = ByteArray(64).also { it[0] = 0x7F; it[1] = 0x7F }
        assertTrue(parse(junk) is XrealPacket.Unrecognised)
    }

    @Test
    fun `short frame is rejected`() {
        assertTrue(XrealSensorPacket.parse(ByteArray(64), 12, axes) is XrealPacket.Unrecognised)
    }

    /**
     * The driver's real convention must put gravity on canonical +Y, because
     * that is the axis the shared gravity correction compares against. Getting
     * this wrong does not fail loudly — it just quietly stops anchoring the
     * horizon, and the world rolls away over a few seconds.
     */
    @Test
    fun `xreal remap puts gravity on canonical up`() {
        val remapped = (XrealSensorPacket.parse(
            XrealCaptures.SENSOR_AT_REST, 64, xrealAxes
        ) as XrealPacket.Sensor).sample

        val accel = remapped.accel
        assertEquals("gravity should dominate canonical +Y", 9.75f, accel.y, 0.4f)
        assertTrue("no large canonical X component, was ${accel.x}", abs(accel.x) < 1f)
        assertTrue("Y should dominate X and Z", accel.y > abs(accel.x) && accel.y > abs(accel.z))
    }

    /** A remap that flips handedness would mirror every rotation. */
    @Test
    fun `xreal remap is a proper rotation`() {
        val x = xrealAxes.apply(dev.halone.glasses.math.Vec3(1f, 0f, 0f))
        val y = xrealAxes.apply(dev.halone.glasses.math.Vec3(0f, 1f, 0f))
        val z = xrealAxes.apply(dev.halone.glasses.math.Vec3(0f, 0f, 1f))

        // det of the matrix whose columns are the images of the basis vectors.
        val det = x.x * (y.y * z.z - y.z * z.y) -
            y.x * (x.y * z.z - x.z * z.y) +
            z.x * (x.y * y.z - x.z * y.y)
        assertEquals("determinant must be +1, was $det", 1f, det, 1e-6f)
    }

    @Test
    fun `axis convention is applied to both gyro and accel`() {
        val flipped = AxisConvention(
            AxisConvention.Axis.X_POS,
            AxisConvention.Axis.Z_POS,
            AxisConvention.Axis.Y_NEG,
        )
        val plain = sample(XrealCaptures.SENSOR_AT_REST)
        val remapped = (XrealSensorPacket.parse(
            XrealCaptures.SENSOR_AT_REST, 64, flipped
        ) as XrealPacket.Sensor).sample

        assertEquals(plain.accel.z, remapped.accel.y, 1e-6f)
        assertEquals(-plain.accel.y, remapped.accel.z, 1e-6f)
        assertEquals(plain.gyro.z, remapped.gyro.y, 1e-6f)
    }

    /** A zero divisor must not produce NaN and poison the filter downstream. */
    @Test
    fun `zero divisor yields zero rather than NaN`() {
        val buf = XrealCaptures.SENSOR_AT_REST.copyOf()
        for (i in 14 until 18) buf[i] = 0
        val s = sample(buf)
        assertTrue("gyro.x was ${s.gyro.x}", !s.gyro.x.isNaN())
        assertEquals(0f, s.gyro.x, 0f)
    }
}
