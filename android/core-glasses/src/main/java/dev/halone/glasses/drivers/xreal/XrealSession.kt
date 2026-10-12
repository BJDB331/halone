package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.BrightnessControl
import dev.halone.glasses.api.CalibrationSource
import dev.halone.glasses.api.DeviceCalibration
import dev.halone.glasses.api.DeviceEvent
import dev.halone.glasses.api.DeviceEventListener
import dev.halone.glasses.api.DeviceInfo
import dev.halone.glasses.api.DisplayControl
import dev.halone.glasses.api.DisplayMode
import dev.halone.glasses.api.GlassesDevice
import dev.halone.glasses.api.ClockSample
import dev.halone.glasses.api.Optics
import dev.halone.glasses.api.ScanoutEvent
import dev.halone.glasses.api.ScanoutSource
import dev.halone.glasses.api.TrackingSink
import dev.halone.glasses.api.TrackingSource
import dev.halone.glasses.transport.HidTransport
import java.util.concurrent.CopyOnWriteArrayList

internal class XrealSession(
    override val info: DeviceInfo,
    private val model: XrealModel,
    private val imuTransport: HidTransport,
    private val mcuTransport: HidTransport?,
    private val vsyncTransport: HidTransport?,
    private val axes: AxisConvention,
) : GlassesDevice {

    override val optics: Optics = model.optics

    override val displayNameHints = listOf(model.model, "XREAL", "Air")

    private val listeners = CopyOnWriteArrayList<DeviceEventListener>()

    private val xrealTracking = XrealTracking()

    override val tracking: TrackingSource get() = xrealTracking

    /**
     * Started eagerly, because every MCU exchange now goes through it: two
     * callers reading the same endpoint would steal each other's replies, and
     * once vsync is on there is far too much traffic for on-demand reads.
     */
    private val mcu = mcuTransport?.let { transport ->
        XrealMcuChannel(transport, ::onMcuPacket).apply { start() }
    }

    override val display: DisplayControl? = mcu?.let { XrealDisplayControl(it) }

    override val brightness: BrightnessControl? = mcu?.let { XrealBrightness(it) }

    /**
     * Its own endpoint, and its own reader. The vendor's libraries name a
     * "vsync interface" separately from the imu and sdk ones, and at 60 events
     * a second it would otherwise drown the endpoint that carries display and
     * brightness commands.
     */
    private val vsync = vsyncTransport?.let { transport ->
        XrealMcuChannel(transport) { _, packet, arrivedAtNs ->
            onTimingPacket(packet, arrivedAtNs)
        }.apply { start() }
    }

    override val scanout: ScanoutSource? = vsync?.let { XrealScanout(mcu) }

    override val calibration: CalibrationSource = XrealCalibration()

    override fun addEventListener(listener: DeviceEventListener) {
        listeners += listener
    }

    override fun removeEventListener(listener: DeviceEventListener) {
        listeners -= listener
    }

    private fun emit(event: DeviceEvent) = listeners.forEach { it.onEvent(event) }

    override fun close() {
        tracking.stop()
        runCatching { scanout?.stop() }
        mcu?.close()
        vsync?.close()
        runCatching { imuTransport.close() }
        mcuTransport?.let { runCatching { it.close() } }
    }

    private inner class XrealTracking : TrackingSource {
        override val providesOrientation = false
        override val providesPosition = false

        @Volatile
        private var running = false
        private var thread: Thread? = null

        /**
         * Remembered so the stream can be put back exactly as it was after
         * something else borrows the endpoint. A caller that asked for tracking
         * should not have to re-establish it because we read a calibration blob.
         */
        private var lastSink: TrackingSink? = null

        override fun start(sink: TrackingSink) {
            if (running) return
            lastSink = sink
            beginStream(sink)
        }

        override fun stop() {
            endStream()
            lastSink = null
        }

        /**
         * Runs [block] with the sensor stream stopped, then restores it.
         *
         * The calibration transfer and the sensor stream share one endpoint, so
         * they cannot overlap: the read loop would eat the command replies.
         */
        fun <T> withStreamPaused(block: () -> T): T {
            val sink = lastSink
            val wasRunning = running
            if (wasRunning) endStream()
            try {
                return block()
            } finally {
                if (wasRunning && sink != null) beginStream(sink)
            }
        }

        private fun beginStream(sink: TrackingSink) {
            running = true

            // A stop before start clears any stream left running by a previous
            // process, which otherwise floods the endpoint with stale frames.
            imuTransport.write(XrealProtocol.stopImuStream())
            drain()
            imuTransport.write(XrealProtocol.startImuStream())

            thread = Thread({ readLoop(sink) }, "xreal-imu").apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
                start()
            }
        }

        private fun endStream() {
            running = false
            thread?.join(500)
            thread = null
            runCatching { imuTransport.write(XrealProtocol.stopImuStream()) }
        }

        private fun drain() {
            val scratch = ByteArray(model.imuPayloadSize)
            repeat(8) { if (imuTransport.read(scratch, 20) <= 0) return }
        }

        private fun readLoop(sink: TrackingSink) {
            // Reused across iterations: at ~1kHz a per-sample allocation here is
            // the difference between a quiet app and constant GC pressure.
            val buffer = ByteArray(model.imuPayloadSize)
            var consecutiveFailures = 0

            while (running) {
                val n = imuTransport.read(buffer, READ_TIMEOUT_MS)
                if (n <= 0) {
                    if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        emit(DeviceEvent.Disconnected("IMU stream stopped responding"))
                        running = false
                    }
                    continue
                }
                consecutiveFailures = 0

                when (val packet = XrealSensorPacket.parse(buffer, n, axes)) {
                    is XrealPacket.Sensor -> sink.onSample(packet.sample)
                    is XrealPacket.CommandReply -> Unit
                    is XrealPacket.Unrecognised -> Unit
                }
            }
        }
    }

    @Volatile
    private var scanoutListener: ((ScanoutEvent) -> Unit)? = null

    private val timingTrace = java.util.concurrent.ConcurrentLinkedDeque<String>()

    @Volatile
    private var timingPackets = 0L

    private var lastCounter = -1
    private var beacons = 0L

    private fun onMcuPacket(msgId: Int, packet: ByteArray, arrivedAtNs: Long) {
        when (msgId) {
            XrealProtocol.MCU_MSG_E_KEY -> emit(
                DeviceEvent.ButtonPressed(DeviceEvent.Button.UNKNOWN)
            )
            XrealProtocol.MCU_MSG_E_PSENSOR -> Unit
        }
    }

    /**
     * The vsync endpoint speaks its own dialect, so this does not go through
     * the MCU message table at all — see [XrealProtocol.VSYNC_DATA_OFFSET].
     * The counter is one byte, so it is unwrapped here rather than handed on
     * with a discontinuity every 256 beacons.
     */
    private fun onTimingPacket(packet: ByteArray, arrivedAtNs: Long) {
        timingPackets++
        timingTrace += XrealProtocol.hex(packet, TRACE_BYTES)
        while (timingTrace.size > TRACE_PACKETS) timingTrace.pollFirst()

        val beacon = XrealProtocol.vsyncBeacon(packet) ?: return
        val listener = scanoutListener ?: return
        if (beacon.counter != lastCounter) {
            if (lastCounter >= 0) beacons += ((beacon.counter - lastCounter) and 0xFF).toLong()
            lastCounter = beacon.counter
        }
        listener(
            ScanoutEvent(
                sequence = beacons,
                deviceTimeNs = beacon.deviceTimeNs,
                periodNs = beacon.periodNs,
                hostTimeNs = arrivedAtNs,
            )
        )
    }

    private inner class XrealCalibration : CalibrationSource {
        override fun read(onReply: (ByteArray) -> Unit): Result<ByteArray> =
            xrealTracking.withStreamPaused {
                XrealCalibrationReader(imuTransport, model.imuPayloadSize).read(onReply)
            }

        override fun load(): DeviceCalibration {
            val fromDevice = read()
                .mapCatching { bytes ->
                    XrealCalibrationParser
                        .parse(bytes, axes, DeviceCalibration.Source.DEVICE)
                        .getOrThrow()
                }
                .onFailure { emit(DeviceEvent.CalibrationFallback(it.message ?: "unknown error")) }

            return fromDevice.getOrElse { XrealFallbackCalibration.load(axes) }
        }
    }

    private inner class XrealDisplayControl(
        private val channel: XrealMcuChannel,
    ) : DisplayControl {

        override val supportedModes = model.modes

        /**
         * Measured on an Air 2 Pro: switching mode drops and re-adds the display
         * with a new displayId, and changes the advertised mode list.
         */
        override val modeChangeRecreatesDisplay = true

        override fun currentMode(): Result<DisplayMode> {
            val reply = channel.request(
                XrealProtocol.readDisplayMode(), XrealProtocol.MCU_MSG_R_DISP_MODE, MCU_TIMEOUT_MS
            )
                ?: return Result.failure(IllegalStateException("no display-mode reply within ${MCU_TIMEOUT_MS}ms"))

            val status = reply[XrealProtocol.MCU_REPLY_STATUS_OFFSET].toInt() and 0xFF
            if (status != STATUS_OK) {
                return Result.failure(IllegalStateException("device returned status $status"))
            }

            val raw = reply[XrealProtocol.MCU_REPLY_DATA_OFFSET].toInt() and 0xFF
            val key = XrealDriver.MODE_BYTES.entries.firstOrNull { it.value == raw }?.key
                ?: return Result.failure(IllegalStateException("unknown firmware mode byte $raw"))

            return model.modes.firstOrNull { it.key == key }
                ?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("mode $key not in this model's table"))
        }

        override fun setMode(mode: DisplayMode): Result<Unit> {
            val byte = XrealDriver.MODE_BYTES[mode.key]
                ?: return Result.failure(IllegalArgumentException("mode ${mode.key} is not an XREAL mode"))

            channel.request(
                XrealProtocol.writeDisplayMode(byte), XrealProtocol.MCU_MSG_W_DISP_MODE, MCU_TIMEOUT_MS
            )
                ?: return Result.failure(IllegalStateException("no acknowledgement for display mode ${mode.key}"))

            return Result.success(Unit)
        }
    }

    private inner class XrealBrightness(
        private val channel: XrealMcuChannel,
    ) : BrightnessControl {

        override val range = 0..7

        override fun current(): Result<Int> {
            val reply = channel.request(
                XrealProtocol.readBrightness(), XrealProtocol.MCU_MSG_R_BRIGHTNESS, MCU_TIMEOUT_MS
            )
                ?: return Result.failure(IllegalStateException("no brightness reply"))
            return Result.success(reply[XrealProtocol.MCU_REPLY_DATA_OFFSET].toInt() and 0xFF)
        }

        override fun set(level: Int): Result<Unit> {
            if (level !in range) {
                return Result.failure(IllegalArgumentException("brightness $level outside $range"))
            }
            channel.request(
                XrealProtocol.writeBrightness(level), XrealProtocol.MCU_MSG_W_BRIGHTNESS, MCU_TIMEOUT_MS
            )
                ?: return Result.failure(IllegalStateException("no acknowledgement for brightness"))
            return Result.success(Unit)
        }
    }

    /**
     * Display refresh reporting, plus the clock round trip that makes the
     * device's timestamps mean something on this side.
     */
    private inner class XrealScanout(
        private val control: XrealMcuChannel?,
    ) : ScanoutSource {

        override val recentPackets: List<String> get() = timingTrace.toList()
        override val packetsSeen: Long get() = timingPackets

        /**
         * **[measured]** The beacons arrive whether or not anything asks for
         * them, so this only attaches a listener. The command is still sent,
         * because a device that has been switched off by something else should
         * be switched back on, and because a firmware that needs it costs us
         * one exchange to satisfy.
         */
        override fun start(listener: (ScanoutEvent) -> Unit): Result<Unit> {
            scanoutListener = listener
            control?.let { runCatching { enableOn(it, true) } }
            return Result.success(Unit)
        }

        private fun enableOn(channel: XrealMcuChannel, on: Boolean): Boolean {
            val reply = channel.request(
                XrealProtocol.writeVsyncEnabled(on), XrealProtocol.MCU_MSG_W_VSYNC, MCU_TIMEOUT_MS
            ) ?: return false
            return (reply[XrealProtocol.MCU_REPLY_STATUS_OFFSET].toInt() and 0xFF) == STATUS_OK
        }

        override fun stop() {
            scanoutListener = null
        }

        /**
         * The NTP round trip, with the device's own two timestamps standing in
         * for the parts we cannot see:
         *
         * ```
         * offset = ((recv − sent) + (replied − heard)) / 2
         * delay  = (heard − sent) − (replied − recv)
         * ```
         *
         * Subtracting the device's own turnaround is what makes this better
         * than halving a round trip: the time the firmware spent thinking is
         * measured rather than charged to the wire.
         */
        override fun syncClock(): Result<ClockSample> {
            val sentAtNs = System.nanoTime()
            val channel = control
                ?: return Result.failure(IllegalStateException("no control endpoint"))
            val reply = channel.request(
                XrealProtocol.heartbeat(sentAtNs), XrealProtocol.MCU_MSG_W_HEARTBEAT, MCU_TIMEOUT_MS
            ) ?: return Result.failure(IllegalStateException("no heartbeat reply"))
            val heardAtNs = System.nanoTime()

            val at = XrealProtocol.MCU_REPLY_DATA_OFFSET
            val deviceReceived =
                XrealProtocol.u64At(reply, at + XrealProtocol.HEARTBEAT_DEVICE_RECEIVED_OFFSET)
            val deviceSent =
                XrealProtocol.u64At(reply, at + XrealProtocol.HEARTBEAT_DEVICE_SENT_OFFSET)
            if (deviceReceived == 0L && deviceSent == 0L) {
                return Result.failure(IllegalStateException("device returned no timestamps"))
            }

            return Result.success(
                ClockSample(
                    offsetNs = ((deviceReceived - sentAtNs) + (deviceSent - heardAtNs)) / 2,
                    roundTripNs = (heardAtNs - sentAtNs) - (deviceSent - deviceReceived),
                )
            )
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 100
        const val MAX_CONSECUTIVE_FAILURES = 50
        const val MCU_PACKET_SIZE = 64
        const val MCU_READ_TIMEOUT_MS = 150
        const val MCU_TIMEOUT_MS = 1_000L
        const val STATUS_OK = 0
        const val TRACE_BYTES = 32
        const val TRACE_PACKETS = 6
    }
}
