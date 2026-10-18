package dev.halone.glasses.drivers.rayneo

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.BrightnessControl
import dev.halone.glasses.api.DeviceEvent
import dev.halone.glasses.api.DeviceEventListener
import dev.halone.glasses.api.DeviceInfo
import dev.halone.glasses.api.DisplayControl
import dev.halone.glasses.api.GlassesDevice
import dev.halone.glasses.api.OnboardPinning
import dev.halone.glasses.api.Optics
import dev.halone.glasses.api.TrackingSample
import dev.halone.glasses.api.TrackingSink
import dev.halone.glasses.api.TrackingSource
import dev.halone.glasses.drivers.rayneo.RayneoProtocol.AnchorMode
import dev.halone.glasses.transport.HidTransport
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A connected pair of GT-series glasses.
 *
 * Built by [RayneoDriver.connect] once the device-info handshake has answered,
 * so [board] and [firmwareBuild] are what this pair said about itself.
 */
internal class RayneoSession(
    override val info: DeviceInfo,
    override val optics: Optics,
    /** Board id from the handshake: [RayneoProtocol.BOARD_GT] or [RayneoProtocol.BOARD_GT_MAX]. */
    val board: Int,
    /** Firmware build date from the handshake, when it was printable. */
    val firmwareBuild: String?,
    initialBrightness: Int?,
    private val transport: HidTransport,
    private val axes: AxisConvention,
    framing: RayneoProtocol.Framing = RayneoProtocol.Framing.BARE,
) : GlassesDevice {

    override val displayNameHints = listOf("SmartGlasses", "RayNeo")

    /**
     * No 3D command is known: the wearer turns side-by-side on with the
     * glasses' own controls, and the host only sees the display re-advertise
     * itself. A null here is what tells the display layer to wait for that
     * rather than try to command it.
     */
    override val display: DisplayControl? = null

    private val listeners = CopyOnWriteArrayList<DeviceEventListener>()
    private val closed = AtomicBoolean(false)

    private val channel = RayneoChannel(transport, ::onReport, ::onLost, framing)

    private val rayneoTracking = RayneoTracking()
    override val tracking: TrackingSource get() = rayneoTracking

    override val brightness: BrightnessControl = RayneoBrightness(initialBrightness)

    /** What the glasses should be in while tracking: follow, or pinned when the wearer asked. */
    @Volatile
    private var sessionAnchor = AnchorMode.FOLLOW

    override val onboardPinning: OnboardPinning = RayneoPinning()

    init {
        channel.start()
    }

    override fun addEventListener(listener: DeviceEventListener) {
        listeners += listener
    }

    override fun removeEventListener(listener: DeviceEventListener) {
        listeners -= listener
    }

    private fun emit(event: DeviceEvent) = listeners.forEach { it.onEvent(event) }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        rayneoTracking.stop()
        channel.close()
        runCatching { transport.close() }
    }

    // ---- reader thread ----

    @Volatile
    private var sink: TrackingSink? = null

    private var lastTick = -1L
    private var ticks = 0L

    private fun onReport(report: RayneoReport) {
        when (report) {
            is RayneoReport.Imu -> sink?.onSample(sample(report))
            is RayneoReport.Ack -> if (report.opcode == RayneoProtocol.CMD_ANCHOR_MODE) onAnchorAck()
            is RayneoReport.Unrecognised -> Unit
        }
    }

    /**
     * Device ticks rather than arrival time: they carry no USB jitter, and the
     * fusion only ever takes differences of them. The counter is 32 bits of
     * 100 µs, which wraps after about five days, so it is unwrapped here.
     */
    private fun sample(imu: RayneoReport.Imu): TrackingSample.Inertial {
        if (lastTick >= 0) ticks += (imu.tick - lastTick) and 0xFFFF_FFFFL
        lastTick = imu.tick
        return TrackingSample.Inertial(
            timestampNs = ticks * RayneoProtocol.TICK_NS,
            gyro = axes.apply(imu.gyroDegPerSec * DEG_TO_RAD),
            accel = axes.apply(imu.accel),
            // In µT, so it belongs in `magnet`. Deliberately not in
            // `magnetRaw`, which the heading correction consumes and is tuned
            // for XREAL counts; both earlier GT projects found desk
            // interference turned magnetometer fusion into a steady yaw spin.
            magnet = axes.apply(imu.magnetMicrotesla),
            magnetRaw = null,
        )
    }

    /**
     * The glasses report a press of their mode button with an unsolicited
     * anchor-mode acknowledgement — which also means they have just left
     * [AnchorMode.FOLLOW]. Put them straight back, or the image would be
     * stabilised twice. Fire-and-forget: this is the reader thread.
     */
    /**
     * Anchor-mode acknowledgements we caused ourselves without waiting for
     * them. The button's ack and ours are byte-for-byte the same, so without
     * this count the ack to a reassertion read as another press — and was
     * reasserted again, forever, flooding the endpoint.
     */
    private val unawaitedAnchorAcks = java.util.concurrent.atomic.AtomicInteger(0)

    private fun onAnchorAck() {
        if (unawaitedAnchorAcks.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) return
        onModeButton()
    }

    private fun onModeButton() {
        emit(DeviceEvent.ButtonPressed(DeviceEvent.Button.UNKNOWN))
        if (sink != null) {
            unawaitedAnchorAcks.incrementAndGet()
            if (!channel.send(RayneoProtocol.CMD_ANCHOR_MODE, sessionAnchor.value)) unawaitedAnchorAcks.decrementAndGet()
        }
    }

    private fun onLost(reason: String) {
        emit(DeviceEvent.Disconnected("IMU stream stopped: $reason"))
    }

    private inner class RayneoTracking : TrackingSource {
        override val providesOrientation = false
        override val providesPosition = false

        @Synchronized
        override fun start(sink: TrackingSink) {
            if (this@RayneoSession.sink != null || closed.get()) return
            lastTick = -1L
            ticks = 0L
            this@RayneoSession.sink = sink
            // Halone world-locks the scene itself, so the glasses' own
            // stabilisation has to be off while it does.
            channel.request(RayneoProtocol.CMD_ANCHOR_MODE, sessionAnchor.value)
            channel.request(RayneoProtocol.CMD_STREAM_ON)
            channel.expectTraffic = true
        }

        @Synchronized
        override fun stop() {
            if (sink == null) return
            channel.expectTraffic = false
            sink = null
            runCatching { channel.request(RayneoProtocol.CMD_STREAM_OFF) }
            // The mode cannot be read back, so there is nothing exact to
            // restore. Pinned is what the earlier GT desktop app hands back.
            runCatching { channel.request(RayneoProtocol.CMD_ANCHOR_MODE, RESTORED_ANCHOR.value) }
        }
    }

    /**
     * The glasses' own "pinned" mode, offered to the host. Tracking keeps
     * running either way — the host still aims its gaze pointer with it.
     */
    private inner class RayneoPinning : OnboardPinning {
        override val enabled: Boolean get() = sessionAnchor == AnchorMode.PINNED

        override fun setEnabled(enabled: Boolean): Result<Unit> {
            val mode = if (enabled) AnchorMode.PINNED else AnchorMode.FOLLOW
            channel.request(RayneoProtocol.CMD_ANCHOR_MODE, mode.value)
                ?: return Result.failure(IllegalStateException("no acknowledgement for anchor mode $mode"))
            sessionAnchor = mode
            return Result.success(Unit)
        }

        /**
         * Pinned-to-pinned does nothing on this firmware; leaving and coming
         * back is what re-anchors it, and the glasses want a moment between.
         */
        override fun recentre(): Result<Unit> {
            if (!enabled) return Result.success(Unit)
            channel.request(RayneoProtocol.CMD_ANCHOR_MODE, AnchorMode.FOLLOW.value)
                ?: return Result.failure(IllegalStateException("no acknowledgement for follow"))
            Thread.sleep(RECENTRE_GAP_MS)
            channel.request(RayneoProtocol.CMD_ANCHOR_MODE, AnchorMode.PINNED.value)
                ?: return Result.failure(IllegalStateException("no acknowledgement for pinned"))
            return Result.success(Unit)
        }
    }

    private inner class RayneoBrightness(initial: Int?) : BrightnessControl {

        /**
         * 0 is nearly dark; 8 is what the glasses ship at. Whether they go
         * brighter is not known, so the range stops at the one level known to
         * be safe rather than invent a ceiling.
         */
        override val range = 0..RayneoProtocol.BRIGHTNESS_FACTORY

        @Volatile
        private var lastKnown: Int? = initial

        override fun current(): Result<Int> {
            val level = channel.request(RayneoProtocol.CMD_DEVICE_INFO)
                ?.let { RayneoProtocol.brightness(it) }
                ?: lastKnown
                ?: return Result.failure(IllegalStateException("no device-info reply"))
            lastKnown = level
            return Result.success(level)
        }

        override fun set(level: Int): Result<Unit> {
            if (level !in range) {
                return Result.failure(IllegalArgumentException("brightness $level outside $range"))
            }
            channel.request(RayneoProtocol.CMD_BRIGHTNESS, level)
                ?: return Result.failure(IllegalStateException("no acknowledgement for brightness"))
            lastKnown = level
            emit(DeviceEvent.BrightnessChanged(level))
            return Result.success(Unit)
        }
    }

    private companion object {
        val DEG_TO_RAD = (Math.PI / 180.0).toFloat()
        val RESTORED_ANCHOR = AnchorMode.PINNED

        /** Between leaving pinned and re-entering it, as measured on the GT. */
        const val RECENTRE_GAP_MS = 300L
    }
}
