package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.transport.HidTransport
import dev.halone.glasses.transport.HidTransportProvider
import dev.halone.glasses.transport.ReplayHidTransport
import dev.halone.glasses.transport.ReplayHidTransport.Companion.toHexKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calibration transfer has never been run against hardware, so unlike the
 * display-control tests these fixtures are built rather than captured. They pin
 * our *framing assumptions* — that a reply echoes the command header and that
 * `length` counts msgid plus the two length bytes — so that when the real
 * device disagrees, it is these tests that have to change and the disagreement
 * is visible rather than absorbed.
 */
class XrealCalibrationTest {

    private val driver = XrealDriver()
    private val identity = UsbIdentity(vendorId = 0x3318, productId = 0x0432)

    /** Builds a reply with the same framing the device uses for command acks. */
    private fun reply(msgId: Int, payload: ByteArray): ByteArray =
        XrealProtocol.imuCommand(msgId, payload).copyOf(64)

    private fun lengthReply(total: Int) = reply(
        XrealProtocol.IMU_MSG_GET_CAL_DATA_LENGTH,
        byteArrayOf(
            (total and 0xFF).toByte(),
            ((total ushr 8) and 0xFF).toByte(),
            ((total ushr 16) and 0xFF).toByte(),
            ((total ushr 24) and 0xFF).toByte(),
        ),
    )

    private fun segmentReplies(blob: ByteArray, chunk: Int): List<ByteArray> =
        blob.toList().chunked(chunk).map {
            reply(XrealProtocol.IMU_MSG_GET_CAL_DATA, it.toByteArray())
        }

    private fun connect(imuInbound: List<ByteArray>): dev.halone.glasses.api.GlassesDevice {
        val match = driver.probe(identity)!!
        val provider = object : HidTransportProvider {
            override fun open(interfaceId: Int): Result<HidTransport> = Result.success(
                ReplayHidTransport(
                    name = when (interfaceId) {
                        4 -> "mcu"
                        5 -> "vsync"
                        else -> "imu"
                    },
                    inbound = if (interfaceId == 3) imuInbound else emptyList(),
                    repeatLast = false,
                )
            )
        }
        return driver.connect(match, provider).getOrThrow()
    }

    @Test
    fun `reassembles a blob from length plus segments`() {
        val blob = ByteArray(200) { (it * 7).toByte() }
        val inbound = listOf(lengthReply(blob.size)) + segmentReplies(blob, 56)

        connect(inbound).use { device ->
            val read = device.calibration!!.read().getOrThrow()
            assertArrayEquals(blob, read)
        }
    }

    /**
     * The last segment is very unlikely to divide evenly, and a device that
     * pads its final reply to a full payload must not add trailing bytes to
     * the blob.
     */
    @Test
    fun `trims a final segment that overruns the declared size`() {
        val blob = ByteArray(100) { it.toByte() }
        val overrun = blob + ByteArray(56) { 0xFF.toByte() }
        val inbound = listOf(lengthReply(blob.size)) + segmentReplies(overrun, 56)

        connect(inbound).use { device ->
            assertArrayEquals(blob, device.calibration!!.read().getOrThrow())
        }
    }

    /**
     * Sensor frames keep arriving for a while after the stream is stopped. If
     * one were mistaken for a segment the blob would be silently corrupted,
     * which is the worst possible failure for data we cannot otherwise check.
     */
    @Test
    fun `skips sensor frames interleaved with replies`() {
        val blob = ByteArray(56) { it.toByte() }
        val inbound = listOf(
            XrealCaptures.SENSOR_AT_REST,
            lengthReply(blob.size),
            XrealCaptures.SENSOR_AT_REST,
        ) + segmentReplies(blob, 56)

        connect(inbound).use { device ->
            assertArrayEquals(blob, device.calibration!!.read().getOrThrow())
        }
    }

    /**
     * Regression for a failure that only appeared on hardware, and only on the
     * run after a reinstall.
     *
     * A sensor stream left running by a *previous process* keeps arriving at
     * ~1 kHz. The reader skipped those frames correctly but counted each one
     * against a 256-frame budget, so the budget was gone in about a quarter of
     * a second — with the reply still queued behind the backlog. The transfer
     * then "failed" and fell back to a bundled calibration from another unit,
     * silently, which is the worst possible way for this to go wrong.
     *
     * The fix is to budget by wall clock and to stop the stream first. This
     * pins the first half; [`the transfer stops the sensor stream`] pins the
     * second.
     */
    @Test
    fun `a large backlog of stale sensor frames does not exhaust the budget`() {
        val blob = ByteArray(56) { it.toByte() }
        val backlog = List(400) { XrealCaptures.SENSOR_AT_REST }
        val inbound = backlog + listOf(lengthReply(blob.size)) + segmentReplies(blob, 56)

        connect(inbound).use { device ->
            assertArrayEquals(blob, device.calibration!!.read().getOrThrow())
        }
    }

    @Test
    fun `every reply is offered to the capture hook before interpretation`() {
        val blob = ByteArray(112) { it.toByte() }
        val inbound = listOf(lengthReply(blob.size)) + segmentReplies(blob, 56)

        connect(inbound).use { device ->
            val seen = mutableListOf<ByteArray>()
            device.calibration!!.read { seen += it }
            // One length reply plus two segments; sensor frames are not replies.
            assertEquals(3, seen.size)
            assertEquals(
                XrealProtocol.IMU_MSG_GET_CAL_DATA_LENGTH,
                XrealProtocol.imuMsgId(seen.first()),
            )
        }
    }

    @Test
    fun `an implausible declared size fails instead of allocating`() {
        connect(listOf(lengthReply(Int.MAX_VALUE))).use { device ->
            val error = device.calibration!!.read().exceptionOrNull()!!
            assertTrue(error.message!!.contains("not plausible"))
        }
    }

    /** A truncated transfer must hand back what it got, not just an error. */
    @Test
    fun `a short transfer returns the partial blob`() {
        val blob = ByteArray(200) { it.toByte() }
        val inbound = listOf(lengthReply(blob.size)) + segmentReplies(blob, 56).take(2)

        connect(inbound).use { device ->
            val error = device.calibration!!.read().exceptionOrNull()
            assertTrue(error is IncompleteCalibration)
            error as IncompleteCalibration
            assertEquals(200, error.expected)
            assertEquals(112, error.partial.size)
            assertArrayEquals(blob.copyOf(112), error.partial)
        }
    }

    @Test
    fun `no reply at all fails cleanly`() {
        connect(emptyList()).use { device ->
            val error = device.calibration!!.read().exceptionOrNull()!!
            assertTrue(error.message!!.contains("0x14"))
        }
    }

    /**
     * Belt and braces with the backlog test above: stopping the stream is what
     * keeps the backlog finite in the first place.
     */
    @Test
    fun `the transfer stops the sensor stream and asks for length then segments`() {
        val blob = ByteArray(56) { it.toByte() }
        val match = driver.probe(identity)!!
        val imu = ReplayHidTransport(
            name = "imu",
            inbound = listOf(lengthReply(blob.size)) + segmentReplies(blob, 56),
            repeatLast = false,
        )
        val provider = object : HidTransportProvider {
            override fun open(interfaceId: Int): Result<HidTransport> = Result.success(
                when (interfaceId) {
                    4 -> ReplayHidTransport("mcu")
                    5 -> ReplayHidTransport("vsync", repeatLast = false)
                    else -> imu
                }
            )
        }

        driver.connect(match, provider).getOrThrow().use { device ->
            device.calibration!!.read().getOrThrow()

            val sent = imu.written.map { it.toHexKey() }
            assertTrue(sent.contains(XrealProtocol.readCalibrationLength().toHexKey()))
            assertTrue(sent.contains(XrealProtocol.readCalibrationSegment().toHexKey()))
        }
    }
}
