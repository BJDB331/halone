package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.StereoLayout
import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.api.preferredStereoMode
import dev.halone.glasses.transport.HidTransport
import dev.halone.glasses.transport.HidTransportProvider
import dev.halone.glasses.transport.ReplayHidTransport
import dev.halone.glasses.transport.ReplayHidTransport.Companion.toHexKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrealDisplayControlTest {

    private val driver = XrealDriver()
    private val identity = UsbIdentity(vendorId = 0x3318, productId = 0x0432)

    /**
     * The MCU answers commands rather than volunteering replies, and since the
     * driver reads that endpoint continuously a reply queued up front would be
     * read and discarded before anything asked for it — which is exactly what
     * hardware does with an unsolicited packet. So replies are keyed to the
     * command that earns them, and [unsolicited] is for what genuinely arrives
     * unbidden.
     */
    private fun connect(
        replies: Map<String, ByteArray> = emptyMap(),
        unsolicited: List<ByteArray> = emptyList(),
    ): dev.halone.glasses.api.GlassesDevice {
        val match = driver.probe(identity)!!
        val provider = object : HidTransportProvider {
            override fun open(interfaceId: Int): Result<HidTransport> = Result.success(
                when (interfaceId) {
                    4 -> ReplayHidTransport(
                        "mcu", inbound = unsolicited, replies = replies, repeatLast = false
                    )
                    5 -> ReplayHidTransport("vsync", repeatLast = false)
                    else -> ReplayHidTransport("imu", inbound = listOf(XrealCaptures.SENSOR_AT_REST))
                }
            )
        }
        return driver.connect(match, provider).getOrThrow()
    }

    private fun answering(command: ByteArray, reply: ByteArray) =
        mapOf(command.toHexKey() to reply)

    private val readMode = XrealProtocol.readDisplayMode()

    @Test
    fun `reads mirror mode from a real reply`() {
        connect(answering(readMode, XrealCaptures.REPLY_MODE_MIRROR)).use { device ->
            val mode = device.display!!.currentMode().getOrThrow()
            assertEquals("mirror-60", mode.key)
            assertEquals(StereoLayout.MONO, mode.layout)
        }
    }

    @Test
    fun `reads sbs mode from a real reply`() {
        connect(answering(readMode, XrealCaptures.REPLY_MODE_SBS)).use { device ->
            val mode = device.display!!.currentMode().getOrThrow()
            assertEquals("sbs-60", mode.key)
            assertEquals(StereoLayout.FULL_SBS, mode.layout)
            assertEquals(1920, mode.perEyeWidth)
            assertEquals(3840, mode.frameWidth)
        }
    }

    /**
     * The regression test for the bug real hardware exposed: an unsolicited
     * packet arriving before our reply must be skipped, not parsed as the answer.
     */
    @Test
    fun `skips unsolicited packets while waiting for a reply`() {
        connect(
            replies = answering(readMode, XrealCaptures.REPLY_MODE_SBS),
            unsolicited = listOf(XrealCaptures.ASYNC_UNSOLICITED, XrealCaptures.ASYNC_UNSOLICITED),
        ).use { device ->
            assertEquals("sbs-60", device.display!!.currentMode().getOrThrow().key)
        }
    }

    @Test
    fun `read fails cleanly when no matching reply arrives`() {
        connect(unsolicited = listOf(XrealCaptures.ASYNC_UNSOLICITED)).use { device ->
            assertTrue(device.display!!.currentMode().isFailure)
        }
    }

    @Test
    fun `setting sbs writes firmware mode byte 3`() {
        val write = XrealProtocol.writeDisplayMode(3)
        connect(answering(write, XrealCaptures.REPLY_MODE_WRITE_ACK)).use { device ->
            val display = device.display!!
            val sbs = display.preferredStereoMode()
            assertNotNull(sbs)
            assertEquals("sbs-60", sbs!!.key)

            assertTrue(display.setMode(sbs).isSuccess)
        }
    }

    @Test
    fun `rejects a mode this driver does not own`() {
        connect().use { device ->
            val alien = dev.halone.glasses.api.DisplayMode(
                "someone-elses-mode", StereoLayout.FULL_SBS, 3840, 1200, 90f
            )
            assertTrue(device.display!!.setMode(alien).isFailure)
        }
    }

    @Test
    fun `mode change is declared as recreating the display`() {
        connect().use { device ->
            assertTrue(device.display!!.modeChangeRecreatesDisplay)
        }
    }

    @Test
    fun `device still works for tracking when the mcu cannot be claimed`() {
        val match = driver.probe(identity)!!
        val provider = object : HidTransportProvider {
            override fun open(interfaceId: Int): Result<HidTransport> =
                if (interfaceId == 4) {
                    Result.failure(IllegalStateException("claim refused"))
                } else {
                    Result.success(ReplayHidTransport("imu", inbound = listOf(XrealCaptures.SENSOR_AT_REST)))
                }
        }
        driver.connect(match, provider).getOrThrow().use { device ->
            assertEquals(null, device.display)
            assertEquals(null, device.brightness)
            assertNotNull("tracking must survive losing the MCU", device.tracking)
        }
    }

    @Test
    fun `every advertised mode maps to a firmware byte`() {
        connect().use { device ->
            for (mode in device.display!!.supportedModes) {
                assertNotNull(
                    "mode ${mode.key} is advertised but has no firmware byte",
                    XrealDriver.MODE_BYTES[mode.key],
                )
            }
        }
    }
}
