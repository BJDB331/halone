package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.DeviceCalibration
import dev.halone.glasses.api.Eye
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the real blob captured from our Air 2 Pro (FSN E434X00248).
 *
 * The expected values come from an independent analysis of the same file in
 * Python (`tools/walk-calibration.py`), so this is a genuine cross-check of the
 * Kotlin reader rather than a restatement of what it happens to produce.
 */
class XrealCalibrationParserTest {

    private val axes = XrealDriver.AXES

    private fun blob(): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/calibration-air2pro-E434X00248.json")) {
            "the captured calibration blob is missing from test resources"
        }.use { it.readBytes() }

    private fun parse(): DeviceCalibration =
        XrealCalibrationParser.parse(blob(), axes, DeviceCalibration.Source.DEVICE).getOrThrow()

    @Test
    fun `identifies the unit it came from`() {
        val c = parse()
        assertEquals("E434X00248", c.serial)
        assertEquals("2024-08-31 15:41:24", c.calibratedAt)
        assertEquals(DeviceCalibration.Source.DEVICE, c.source)
    }

    @Test
    fun `reads the per-eye intrinsics`() {
        val left = parse().left!!
        assertEquals(2691.43f, left.fx, 0.01f)
        assertEquals(2705.31f, left.fy, 0.01f)
        assertEquals(964.043f, left.cx, 0.01f)
        assertEquals(560.216f, left.cy, 0.01f)
        assertEquals(1920, left.widthPx)
        assertEquals(1080, left.heightPx)
    }

    @Test
    fun `derives the field of view from the intrinsics`() {
        val f = parse().left!!.frustum
        assertEquals(39.261f, f.horizontalDegrees, 0.01f)
        assertEquals(22.575f, f.verticalDegrees, 0.01f)
        assertEquals(44.464f, f.diagonalDegrees, 0.01f)
    }

    /**
     * The whole reason `Frustum` exists rather than a field-of-view float. If
     * this ever reads zero, something has quietly re-centred the projection and
     * the asymmetry is being thrown away.
     */
    @Test
    fun `the frustum is asymmetric`() {
        val f = parse().left!!.frustum
        assertEquals(0.823f, f.verticalAsymmetryDegrees, 0.01f)
        assertTrue("top and bottom must differ", kotlin.math.abs(f.top) != kotlin.math.abs(f.bottom))
        val symmetric = f.symmetrised()
        assertEquals(0f, symmetric.verticalAsymmetryDegrees, 1e-5f)
    }

    @Test
    fun `IPD comes from the eye positions`() {
        assertEquals(0.06366f, parse().ipdMetres!!, 0.00005f)
    }

    /** A distance is frame-independent, so the axis conversion must not change it. */
    @Test
    fun `the axis conversion preserves eye separation`() {
        val c = parse()
        val separation = (c.right!!.positionFromImu - c.left!!.positionFromImu).length
        assertEquals(0.06366f, separation, 0.00005f)
    }

    /**
     * The stored vectors are `accel_bias = [0.028185, -0.275832, 0.012632]` and
     * `gyro_bias = [-0.0048628, -0.0074489, 0.00053336]`. They come out of the
     * parser in the *sensor packet* frame, which the factory's is not: Y and Z
     * are exchanged and all three negated. See `FACTORY_IMU_FRAME`.
     */
    @Test
    fun `reads the factory IMU bias into the sensor packet frame`() {
        val imu = parse().imu!!
        assertEquals(-0.0281849f, imu.hardwareAccelBias.x, 1e-6f)
        assertEquals(-0.0126323f, imu.hardwareAccelBias.y, 1e-6f)
        assertEquals(0.2758319f, imu.hardwareAccelBias.z, 1e-6f)

        assertEquals(0.0048627f, imu.hardwareGyroBias.x, 1e-6f)
        assertEquals(-0.0005333f, imu.hardwareGyroBias.y, 1e-6f)
        assertEquals(0.0074488f, imu.hardwareGyroBias.z, 1e-6f)

        // A frame change cannot alter a magnitude, in either direction.
        assertEquals(0.2776f, imu.hardwareAccelBias.length, 1e-3f)
        assertEquals(imu.hardwareAccelBias.length, imu.accelBias.length, 1e-6f)
        assertEquals(imu.hardwareGyroBias.length, imu.gyroBias.length, 1e-9f)
    }

    /**
     * Regression for the measurement that established the frame, taken with the
     * glasses stationary on a table. Both residuals come out of a mapping that
     * was fitted to the gyro alone, so the accelerometer is independent
     * evidence — and it is what takes the long-standing 3.5% rest-magnitude
     * error down to 0.7%.
     *
     * If this ever fails, the frame mapping has moved and the bias is being
     * applied in a direction nobody checked.
     */
    @Test
    fun `the frame mapping is the one the stationary measurement picked`() {
        val imu = parse().imu!!

        // Measured at rest, canonical frame, averaged over 750 still samples.
        val measuredGyro = dev.halone.glasses.math.Vec3(-0.0064f, 0.0078f, -0.0008f)
        val measuredAccel = dev.halone.glasses.math.Vec3(0.1639f, 10.1129f, 0.9128f)
        val degPerSec = 180f / Math.PI.toFloat()

        assertEquals(0.580f, measuredGyro.length * degPerSec, 0.01f)
        assertEquals(0.092f, (measuredGyro - imu.gyroBias).length * degPerSec, 0.01f)

        assertEquals(10.155f, measuredAccel.length, 0.01f)
        assertEquals(9.881f, (measuredAccel - imu.accelBias).length, 0.01f)
    }

    @Test
    fun `notices that the vendor stored no magnetometer calibration`() {
        assertTrue(parse().imu!!.magnetometerUncalibrated)
    }

    @Test
    fun `reads the noise figures`() {
        assertEquals(listOf(0.00035f, 1e-05f, 0.00667f, 0.00068f), parse().imu!!.noises)
    }

    @Test
    fun `reads the distortion grid and its displacements`() {
        val grid = parse().left!!.distortion!!
        assertEquals(18, grid.rows)
        assertEquals(32, grid.cols)
        assertEquals(18 * 32 * 4, grid.points.size)
        // Corner of the frame: ideal (0,0) lands at (-13.60, -9.96).
        assertEquals(0f, grid.idealX(0, 0), 1e-4f)
        assertEquals(-13.6012f, grid.actualX(0, 0), 1e-3f)
        assertEquals(-9.95843f, grid.actualY(0, 0), 1e-3f)
        assertEquals(20.78f, grid.maxDisplacementPx, 0.01f)
        assertEquals(3.91f, grid.meanDisplacementPx, 0.01f)
    }

    @Test
    fun `this unit reports no cameras`() {
        // Air-series hardware is 3DoF; the blob says so itself, and a parser
        // that invented camera calibration here would be a red flag.
        assertEquals(2, parse().eyes.size)
    }

    @Test
    fun `rejects bytes that are not a calibration blob`() {
        val result = XrealCalibrationParser.parse(
            "not json".toByteArray(), axes, DeviceCalibration.Source.DEVICE
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects valid json that carries no intrinsics`() {
        val result = XrealCalibrationParser.parse(
            """{"FSN":"X"}""".toByteArray(), axes, DeviceCalibration.Source.DEVICE
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `the bundled fallback parses and is stamped as a fallback`() {
        val c = XrealFallbackCalibration.load(axes)
        assertEquals(DeviceCalibration.Source.FALLBACK, c.source)
        assertNotNull(c.left)
        assertEquals(22.575f, c.left!!.frustum.verticalDegrees, 0.01f)
        assertEquals(0.06366f, c.ipdMetres!!, 0.00005f)
        assertNotNull(c.imu)
    }

    @Test
    fun `describe mentions the source so a reader cannot miss it`() {
        assertTrue(XrealFallbackCalibration.load(axes).describe().contains("fallback"))
        assertTrue(parse().describe().contains("device"))
    }
}
