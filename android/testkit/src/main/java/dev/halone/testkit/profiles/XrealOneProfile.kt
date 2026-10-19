package dev.halone.testkit.profiles

import dev.halone.glasses.api.UsbIdentity
import dev.halone.testkit.R
import dev.halone.glasses.math.Quaternion
import dev.halone.testkit.ab.AbRounds
import dev.halone.testkit.ab.HeadView
import dev.halone.testkit.analysis.AxisSolver
import dev.halone.testkit.analysis.D
import dev.halone.testkit.capture.ImuListener
import dev.halone.testkit.capture.MotionMeter
import dev.halone.testkit.capture.SegmentRecorder
import dev.halone.testkit.capture.Tap
import dev.halone.testkit.engine.CommonSteps
import dev.halone.testkit.engine.Kit
import dev.halone.testkit.engine.Profile
import dev.halone.testkit.engine.retrying
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan
import kotlin.math.abs
import kotlin.math.acos

/**
 * XREAL One, One Pro and 1S: sensors and controls over a USB network link.
 *
 * Only reads. The one thing sent is three published getters on the control
 * port; nothing that changes a setting is ever written.
 */
object XrealOneProfile : Profile {
    override val id = "xreal-one"
    override val steps = 7

    private const val VENDOR = 0x3318
    private val PRODUCTS = setOf(0x0436, 0x0438, 0x043E)
    private val HOSTS = listOf("169.254.2.1", "169.254.1.1")
    private const val PORT_REPORTS = 52998
    private const val PORT_CONTROL = 52999

    private const val MSG_GET_SW_VERSION = 0x271D
    private const val MSG_GET_CONFIG = 0x271F
    private const val MSG_GET_DSP_VERSION = 0x272D

    override fun matches(device: UsbIdentity) = device.vendorId == VENDOR && device.productId in PRODUCTS

    sealed interface Check {
        /** [maxTurnedDegrees] is the looser limit for a worn head, which is never truly still. */
        data class Still(val maxDegreesPerSec: Double, val maxTurnedDegrees: Double = Double.MAX_VALUE) : Check
        data class Turn(val minDegrees: Double) : Check
        data object None : Check
    }

    /** [prepare], when set, is something the tester does with the glasses first and confirms with Done. */
    data class Motion(val label: String, val instruction: Int, val seconds: Int, val check: Check, val prepare: Int? = null)

    val MOTIONS = listOf(
        Motion("rest", R.string.motion_rest, 8, Check.Still(6.0), prepare = R.string.motion_rest_prepare),
        Motion("level", R.string.motion_level, 5, Check.Still(Double.MAX_VALUE, maxTurnedDegrees = 45.0), prepare = R.string.motion_level_prepare),
        Motion("turn-left", R.string.motion_turn_left, 7, Check.Turn(30.0)),
        Motion("turn-right", R.string.motion_turn_right, 7, Check.Turn(30.0)),
        Motion("look-up", R.string.motion_look_up, 8, Check.Turn(20.0)),
        Motion("look-down", R.string.motion_look_down, 8, Check.Turn(20.0)),
        Motion("tilt-left", R.string.motion_tilt_left, 8, Check.Turn(20.0)),
        Motion("tilt-right", R.string.motion_tilt_right, 8, Check.Turn(20.0)),
        Motion("free", R.string.motion_free, 12, Check.None),
    )

    override suspend fun run(kit: Kit, glasses: UsbIdentity) {
        kit.io.progress(1, steps)
        CommonSteps.checkDisplay(kit)

        kit.io.progress(2, steps)
        val menu = kit.text(R.string.one_menu)
        val menuAnswer = kit.io.ask(menu, listOf(kit.choice("done", R.string.done), kit.choice("missing", R.string.one_menu_cant_find)))
        kit.session.answer("follow_stabilizer", menu, menuAnswer)

        kit.io.progress(3, steps)
        val meter = MotionMeter()
        val recorder = SegmentRecorder()
        val headView = AtomicReference<HeadView?>()
        val stream = XrealOneStream(ImuListener { t, gx, gy, gz, ax, ay, az ->
            meter.onGyro(t, gx, gy, gz)
            recorder.onImu(t, gx, gy, gz, ax, ay, az)
            headView.get()?.onImu(t, gx, gy, gz, ax, ay, az)
        })
        val reports = connectSensors(kit, stream)

        kit.io.progress(4, steps)
        val control = readControl(kit)

        kit.io.progress(5, steps)
        if (reports != null && recordMotions(kit, reports, meter, recorder)) {
            val axes = solveAxes(kit, reports, meter, recorder, control?.config)
            compare(kit, reports, axes, control?.config, headView)
        }

        kit.io.progress(6, steps)
        CommonSteps.tourDisplayModes(kit)

        val question = kit.text(R.string.one_model_question)
        val model = kit.io.ask(
            question,
            listOf(
                kit.choice("one-pro", R.string.model_one_pro),
                kit.choice("one", R.string.model_one),
                kit.choice("1s", R.string.model_1s),
                kit.choice("other", R.string.model_other),
                kit.choice("unknown", R.string.model_unknown),
            ),
        )
        kit.session.answer("model", question, model)

        kit.io.progress(7, steps)
        if (kit.session.outcomes["tcpip"] == "turned-on") kit.io.say(kit.text(R.string.one_tcpip_reminder))
        reports?.close()
        control?.tap?.close()
        offerHidRestore(kit)
        CommonSteps.closing(kit)
    }

    private suspend fun connectSensors(kit: Kit, stream: XrealOneStream): Tap? {
        if (!kit.hasSystemLink(HOSTS) && !kit.hasUsbNetwork() && !enableTcpIp(kit)) {
            kit.session.outcome("sensors", "tcpip-off")
            kit.io.say(kit.text(R.string.one_tcpip_skipped))
            return null
        }
        if (!kit.hasSystemLink(HOSTS)) {
            kit.io.say(kit.text(R.string.usb_access_intro))
            val granted = kit.requestUsbAccess()
            kit.session.outcome("usb_access", if (granted) "granted" else "denied")
            if (!granted) {
                kit.io.say(kit.text(R.string.usb_access_denied))
                kit.session.outcome("sensors", "no-usb-access")
                return null
            }
        }
        var tap: Tap? = null
        val connected = retrying(3) { attempt ->
            when (attempt) {
                2 -> kit.io.say(kit.text(R.string.one_sensors_retry))
                3 -> {
                    val question = kit.text(R.string.one_stabilizer_question)
                    val answer = kit.io.ask(
                        question,
                        listOf(
                            kit.choice("off", R.string.one_stabilizer_off),
                            kit.choice("was-on", R.string.one_stabilizer_on),
                            kit.choice("missing", R.string.one_stabilizer_unknown),
                        ),
                    )
                    kit.session.answer("stabilizer_off", question, answer, attempt)
                }
            }
            val opened = kit.openTcp("reports-$attempt", HOSTS, PORT_REPORTS) { bytes, n -> stream.feed(bytes, n) }
                .onFailure { kit.session.note("reports attempt $attempt: ${it.message}") }
                .getOrNull()
                ?: run {
                    delay(RETRY_PAUSE_MS)
                    return@retrying false
                }
            val flowing = kit.io.waitUntil(kit.text(R.string.one_sensors_waiting), FIRST_REPORT_TIMEOUT_MS, skip = null) {
                stream.reports > 0
            }
            if (!flowing) {
                kit.session.note("reports attempt $attempt: connected but silent")
                opened.close()
            } else {
                tap = opened
            }
            flowing
        }
        kit.session.outcome("sensors", if (connected) "streaming" else "unreachable")
        if (connected) {
            kit.io.say(kit.text(R.string.one_sensors_ok))
        } else {
            kit.snapshot("sensors-unreachable")
            kit.io.say(kit.text(R.string.one_sensors_failed))
        }
        return tap
    }

    class ControlRead(val tap: Tap, val config: String)

    /**
     * Without their TCP/IP setting the glasses expose no network function over
     * USB at all, and the sensors are unreachable. XREAL documents where it is,
     * and tells people to turn it off when a phone mistakes the glasses for a
     * network device. One tester's glasses still offered none with it on, so
     * that case gets a replug and, failing that, the firmware version.
     */
    private suspend fun enableTcpIp(kit: Kit): Boolean {
        val question = kit.text(R.string.one_tcpip_question)
        val answer = kit.io.ask(
            question,
            listOf(
                kit.choice("off", R.string.one_tcpip_off),
                kit.choice("on", R.string.one_tcpip_already_on),
                kit.choice("missing", R.string.one_tcpip_cant),
            ),
        )
        kit.session.answer("tcpip_setting", question, answer)
        if (answer.id == "missing") return overHidOrGiveUp(kit, "not-found")

        kit.io.say(kit.text(if (answer.id == "on") R.string.one_tcpip_replug else R.string.one_tcpip_intro))
        val waiting = if (answer.id == "on") R.string.one_tcpip_waiting_replug else R.string.one_tcpip_waiting
        val on = kit.io.waitUntil(kit.text(waiting), TCPIP_TIMEOUT_MS, kit.choice("skip", R.string.one_tcpip_give_up)) {
            networkAppeared(kit)
        }
        if (!on) return overHidOrGiveUp(kit, if (answer.id == "on") "on-but-missing" else "left-off")

        kit.session.outcome("tcpip", if (answer.id == "on") "appeared-after-replug" else "turned-on")
        kit.io.say(kit.text(R.string.one_tcpip_on))
        kit.snapshot("tcpip-on")
        return true
    }

    private fun networkAppeared(kit: Kit) = kit.hasUsbNetwork() || kit.hasSystemLink(HOSTS)

    /**
     * The menu toggle did not bring the network up, so look at the setting
     * itself over HID: the USB function word, plus firmware versions while
     * there. It is only ever changed with the tester's say-so, and only the
     * NCM and ECM fields (see `XrealOneHid`).
     */
    private suspend fun overHidOrGiveUp(kit: Kit, outcome: String): Boolean {
        kit.session.outcome("tcpip", outcome)
        kit.io.say(kit.text(R.string.one_hid_intro))
        if (!kit.requestUsbAccess()) {
            kit.session.outcome("hid", "no-usb-access")
            return giveUp(kit, askFirmware = true)
        }
        val state = kit.readGlassesState().getOrElse {
            kit.session.note("hid read: ${it.message}")
            kit.session.outcome("hid", "read-failed")
            return giveUp(kit, askFirmware = true)
        }
        kit.session.outcome("usb_functions", state.functions)
        state.mcuVersion?.let { kit.session.outcome("firmware_mcu", it) }
        state.dspVersion?.let { kit.session.outcome("firmware_dsp", it) }
        if (state.networkOn) {
            kit.session.outcome("hid", "network-on-but-missing")
            return giveUp(kit, askFirmware = state.mcuVersion == null)
        }

        val consent = kit.text(R.string.one_hid_consent)
        val choice = kit.io.ask(consent, listOf(kit.choice("yes", R.string.one_hid_yes), kit.choice("no", R.string.one_hid_no)))
        kit.session.answer("hid_enable_network", consent, choice)
        if (choice.id != "yes") {
            kit.session.outcome("hid", "declined")
            return giveUp(kit, askFirmware = state.mcuVersion == null)
        }
        kit.setGlassesNetwork(on = true).onFailure {
            kit.session.note("hid write: ${it.message}")
            kit.session.outcome("hid", "write-failed")
            return giveUp(kit, askFirmware = state.mcuVersion == null)
        }
        val appeared = kit.io.waitUntil(kit.text(R.string.one_hid_waiting), HID_REENUMERATE_TIMEOUT_MS, kit.choice("skip", R.string.one_tcpip_give_up)) {
            networkAppeared(kit)
        }
        kit.snapshot("after-hid-enable")
        kit.session.outcome("hid", if (appeared) "enabled" else "enabled-no-network")
        if (!appeared) return giveUp(kit, askFirmware = false)
        kit.session.outcome("tcpip", "enabled-over-hid")
        kit.io.say(kit.text(R.string.one_tcpip_on))
        return true
    }

    private suspend fun giveUp(kit: Kit, askFirmware: Boolean): Boolean {
        kit.snapshot("tcpip-unavailable")
        if (askFirmware) {
            kit.io.askText(kit.text(R.string.one_firmware_question), kit.choice("skip", R.string.skip))
                ?.takeIf { it.isNotBlank() }
                ?.let { kit.session.note("firmware (read by tester): $it") }
        }
        return false
    }

    /** Offers to undo a network switch-on made over HID. */
    private suspend fun offerHidRestore(kit: Kit) {
        if (kit.session.outcomes["hid"] != "enabled") return
        val question = kit.text(R.string.one_hid_restore)
        val choice = kit.io.ask(question, listOf(kit.choice("off", R.string.one_hid_restore_off), kit.choice("keep", R.string.one_hid_restore_keep)))
        kit.session.answer("hid_restore", question, choice)
        if (choice.id != "off") return
        kit.setGlassesNetwork(on = false)
            .onSuccess {
                kit.session.outcome("hid_restore", "restored")
                kit.io.say(kit.text(R.string.one_hid_restored))
            }
            .onFailure {
                kit.session.note("hid restore: ${it.message}")
                kit.session.outcome("hid_restore", "failed")
            }
    }

    /** Read-only getters; the config reply is the factory calibration, about 220 KB of JSON. */
    private suspend fun readControl(kit: Kit): ControlRead? {
        val received = ByteArrayOutputStream()
        val tap = kit.openTcp("control", HOSTS, PORT_CONTROL, alwaysRecord = true) { bytes, n ->
            synchronized(received) { received.write(bytes, 0, n) }
        }.onFailure {
            kit.session.note("control: ${it.message}")
            kit.session.outcome("control", "unreachable")
        }.getOrNull() ?: return null

        listOf(MSG_GET_CONFIG, MSG_GET_SW_VERSION, MSG_GET_DSP_VERSION).forEachIndexed { i, magic ->
            tap.send(getter(magic, transaction = i + 1))
        }

        var lastSize = -1
        var stableSince = 0L
        kit.io.waitUntil(kit.text(R.string.one_config_waiting), CONFIG_TIMEOUT_MS, skip = null) {
            val size = synchronized(received) { received.size() }
            val now = kit.session.now()
            if (size != lastSize) {
                lastSize = size
                stableSince = now
            }
            size > 0 && now - stableSince > QUIET_NS
        }

        val text = synchronized(received) { received.toString(Charsets.ISO_8859_1.name()) }
        SERIAL.find(text)?.groupValues?.get(1)?.let { kit.session.secret(it) }
        kit.session.outcome("control", if (text.contains("\"display\"")) "config-read" else "bytes-${text.length}")
        return ControlRead(tap, text)
    }

    /** False when the stream died part-way, which leaves too little to solve axes from. */
    private suspend fun recordMotions(kit: Kit, reports: Tap, meter: MotionMeter, recorder: SegmentRecorder): Boolean {
        kit.io.say(kit.text(R.string.motion_intro))
        for (motion in MOTIONS) {
            if (!reports.alive) {
                kit.io.say(kit.text(R.string.motion_no_data))
                kit.session.outcome("motions", "stream-ended-before-${motion.label}")
                return false
            }
            recordMotion(kit, motion, meter, recorder, tag = "")
        }
        return true
    }

    private suspend fun recordMotion(kit: Kit, motion: Motion, meter: MotionMeter, recorder: SegmentRecorder, tag: String) {
        val passed = retrying(MOTION_ATTEMPTS) { attempt ->
            if (attempt > 1) {
                kit.io.say(kit.text(if (motion.check is Check.Still) R.string.motion_retry_still else R.string.motion_retry_turn))
            }
            motion.prepare?.let { prepare ->
                val prompt = kit.text(prepare)
                kit.session.answer("prepare_${motion.label}", prompt, kit.io.ask(prompt, listOf(kit.choice("done", R.string.done))), attempt)
            }
            val segment = "motion:${motion.label}:$tag$attempt"
            kit.session.segment(segment) {
                recorder.begin(motion.label)
                try {
                    kit.io.say(kit.text(motion.instruction))
                    recorder.go()
                    meter.reset()
                    kit.session.mark("$segment:go")
                    kit.io.cue()
                    delay(motion.seconds * 1_000L)
                } finally {
                    recorder.end()
                }
            }
            val reading = meter.read()
            kit.session.note(
                "motion ${motion.label} $tag#$attempt: turned %.1f°, peak %.1f°/s, %d samples"
                    .format(reading.turnedDegrees, reading.peakDegreesPerSec, reading.samples),
            )
            when {
                reading.samples == 0L -> true
                motion.check is Check.Still -> reading.peakDegreesPerSec <= motion.check.maxDegreesPerSec &&
                    reading.turnedDegrees <= motion.check.maxTurnedDegrees
                motion.check is Check.Turn -> reading.turnedDegrees >= motion.check.minDegrees
                else -> true
            }
        }
        kit.session.outcome("motion_${motion.label}$tag", if (passed) "ok" else "unclear")
        if (passed) kit.io.say(kit.text(R.string.motion_good))
    }

    /**
     * Works the axis convention out on the spot, so the session can go on to
     * render with it. When the motions contradict each other the tester repeats
     * just those, once.
     */
    private suspend fun solveAxes(kit: Kit, reports: Tap, meter: MotionMeter, recorder: SegmentRecorder, config: String?): AxisSolver.Result {
        var result = solve(recorder)
        if (result.status != AxisSolver.Status.CONFIDENT && result.redo.isNotEmpty() && reports.alive) {
            kit.session.note("axes first pass: ${result.problems.joinToString("; ")}")
            kit.io.say(kit.text(R.string.axes_repeat))
            for (motion in MOTIONS.filter { it.label in result.redo }) {
                recordMotion(kit, motion, meter, recorder, tag = "redo")
            }
            result = solve(recorder)
        }

        val json = AxisSolver.toJson(result)
        val factory = config?.let { factoryDisplayRotation(it) }
        factory?.let { json.put("factory_display_rotation_deg", it) }
        kit.session.attach("analysis/axes.json", json.toString(2))
        kit.session.outcome("axes", result.status.name.lowercase())
        result.convention?.let { kit.session.outcome("axes_convention", "x=${it.xFrom} y=${it.yFrom} z=${it.zFrom}") }
        result.mountAngleDegrees?.let { mount ->
            kit.session.outcome("mount_angle_deg", "%.1f".format(mount))
            factory?.let { kit.session.outcome("mount_vs_factory_deg", "%.1f".format(abs(mount - it))) }
        }
        return result
    }

    /** Bias from the glasses lying still on the table; gravity from them worn and still. */
    /** Blind A/B rounds, when the session has what they need to render a trustworthy scene. */
    private suspend fun compare(kit: Kit, reports: Tap, axes: AxisSolver.Result, config: String?, holder: AtomicReference<HeadView?>) {
        val convention = axes.convention
        val reason = when {
            convention == null || axes.status == AxisSolver.Status.AMBIGUOUS -> "axes-${axes.status.name.lowercase()}"
            !reports.alive -> "stream-ended"
            else -> null
        }
        val factory = config?.let { factoryDisplay(it) }
        if (reason != null || factory == null || convention == null) {
            kit.session.outcome("ab", "skipped-${reason ?: "no-calibration"}")
            return
        }
        val view = HeadView(convention, convention.apply(factory.rotation))
        holder.set(view)
        try {
            val report = AbRounds(kit, view, factory.verticalFovDegrees).run()
            kit.session.attach("analysis/ab.json", report.toString(2))
            kit.session.outcome("ab", "done")
        } finally {
            kit.io.showScene(null, null)
            holder.set(null)
        }
    }

    class FactoryDisplay(val rotation: Quaternion, val verticalFovDegrees: Double)

    /** The left display's rotation against the IMU (hardware axes) and its vertical field of view, from the config. */
    fun factoryDisplay(config: String): FactoryDisplay? = runCatching {
        val json = JSONObject(config.substring(config.indexOf('{'), config.lastIndexOf('}') + 1))
        val display = json.getJSONObject("display")
        val q = display.getJSONArray("target_q_left_display")
        val k = display.getJSONArray("k_left_display")
        val height = display.getJSONArray("resolution").getDouble(1)
        FactoryDisplay(
            rotation = Quaternion(q.getDouble(3).toFloat(), q.getDouble(0).toFloat(), q.getDouble(1).toFloat(), q.getDouble(2).toFloat()).normalized(),
            verticalFovDegrees = Math.toDegrees(2 * atan((height / 2) / k.getDouble(4))),
        )
    }.getOrNull()

    private fun solve(recorder: SegmentRecorder): AxisSolver.Result {
        val still = recorder.samples("rest") ?: recorder.samples("level")
        val bias = still?.let { AxisSolver.meanGyro(it) } ?: D(0.0, 0.0, 0.0)
        val swings = AxisSolver.EXPECTED.keys.mapNotNull { label ->
            recorder.samples(label)?.let { label to AxisSolver.swing(it, bias) }
        }.toMap()
        return AxisSolver.solve(swings, recorder.samples("level")?.let { AxisSolver.meanAccel(it) })
    }

    /** How far the factory says the displays are rotated against the IMU, averaged over both eyes. */
    fun factoryDisplayRotation(config: String): Double? = runCatching {
        val json = JSONObject(config.substring(config.indexOf('{'), config.lastIndexOf('}') + 1))
        val display = json.getJSONObject("display")
        listOf("left", "right").map { side ->
            val q = display.getJSONArray("target_q_${side}_display")
            Math.toDegrees(2 * acos(abs(q.getDouble(3)).coerceAtMost(1.0)))
        }.average()
    }.getOrNull()

    fun getter(magic: Int, transaction: Int): ByteArray = byteArrayOf(
        (magic ushr 8).toByte(), magic.toByte(),
        0, 0, 0, 6,
        0x80.toByte(), (transaction ushr 16).toByte(), (transaction ushr 8).toByte(), transaction.toByte(),
        0x18, 0x00,
    )

    private val SERIAL = Regex("\"FSN\"\\s*:\\s*\"([^\"]+)\"")
    private const val MOTION_ATTEMPTS = 3
    private const val RETRY_PAUSE_MS = 5_000L
    private const val FIRST_REPORT_TIMEOUT_MS = 6_000L
    private const val CONFIG_TIMEOUT_MS = 12_000L
    private const val TCPIP_TIMEOUT_MS = 240_000L
    private const val HID_REENUMERATE_TIMEOUT_MS = 30_000L
    private const val QUIET_NS = 1_000_000_000L
}
