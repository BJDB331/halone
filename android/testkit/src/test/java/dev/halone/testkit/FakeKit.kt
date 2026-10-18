package dev.halone.testkit

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.UsbIdentity
import dev.halone.testkit.analysis.D
import dev.halone.testkit.capture.Tap
import dev.halone.testkit.engine.Choice
import dev.halone.testkit.engine.DisplayState
import dev.halone.testkit.engine.Io
import dev.halone.testkit.engine.Kit
import dev.halone.testkit.engine.Session
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Text is `s<resource id>`, so a test can tell which line a script spoke. */
fun said(id: Int) = "s$id"

class FakeTap(val listener: ((ByteArray, Int) -> Unit)?) : Tap {
    val sent = mutableListOf<ByteArray>()
    var onSend: (ByteArray) -> Unit = {}
    override var bytesIn = 0L
    override var alive = true

    fun push(bytes: ByteArray) {
        bytesIn += bytes.size
        listener?.invoke(bytes, bytes.size)
    }

    override suspend fun send(bytes: ByteArray): Boolean {
        sent += bytes
        onSend(bytes)
        return true
    }

    override fun close() {
        alive = false
    }
}

class FakeKit(clock: () -> Long) : Kit, Io {
    override val io: Io get() = this
    override val session = Session(clock = clock)

    var devices = listOf(UsbIdentity(vendorId = 0x3318, productId = 0x0436))
    var display: DisplayState? = DisplayState("XREAL One Pro", "2d")
    val spoken = mutableListOf<String>()
    val snapshots = mutableListOf<String>()
    val taps = mutableMapOf<String, FakeTap>()

    /** Picks an answer per question; the default takes the first choice. */
    var answer: (question: String, choices: List<Choice>) -> Choice = { _, choices -> choices.first() }
    var onSay: (String) -> Unit = {}
    var tcp: (name: String, port: Int, listener: ((ByteArray, Int) -> Unit)?) -> Result<Tap> = { name, _, listener ->
        Result.success(FakeTap(listener).also { taps[name] = it })
    }

    override fun text(id: Int, vararg args: Any) = said(id)
    override fun usbDevices() = devices
    override fun snapshot(label: String) {
        snapshots += label
        session.attach("recon/$label/marker.txt", label)
    }
    override fun externalDisplay() = display
    override suspend fun openTcp(
        name: String,
        hosts: List<String>,
        port: Int,
        alwaysRecord: Boolean,
        listener: ((ByteArray, Int) -> Unit)?,
    ) = tcp(name, port, listener)

    override suspend fun say(text: String) {
        spoken += text
        onSay(text)
    }

    override suspend fun ask(question: String, choices: List<Choice>): Choice {
        spoken += question
        return answer(question, choices)
    }

    override suspend fun askText(question: String, skip: Choice): String? {
        spoken += question
        return "felt fine"
    }

    override suspend fun waitUntil(prompt: String, timeoutMs: Long, skip: Choice?, condition: suspend () -> Boolean): Boolean {
        spoken += prompt
        var waited = 0L
        while (waited <= timeoutMs) {
            if (condition()) return true
            delay(100)
            waited += 100
        }
        return false
    }

    override fun progress(done: Int, total: Int) = Unit
}

/** A 0x0B report frame; accel defaults to gravity on hardware -Y, as a 1S reads worn level. */
fun imuReport(
    timestampNs: Long,
    gx: Float,
    gy: Float = 0f,
    gz: Float = 0f,
    accel: Triple<Float, Float, Float> = Triple(0f, -9.8f, 0f),
): ByteArray {
    val payload = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
    payload.putLong(0x08, timestampNs)
    payload.putInt(0x18, 0x0B)
    payload.putFloat(0x1C, gx).putFloat(0x20, gy).putFloat(0x24, gz)
    payload.putFloat(0x28, accel.first).putFloat(0x2C, accel.second).putFloat(0x30, accel.third)
    return ByteArrayOutputStream().apply {
        write(byteArrayOf(0x28, 0x36, 0, 0, 0, 0x80.toByte()))
        write(payload.array())
    }.toByteArray()
}

/** [seconds] of 1 kHz reports turning at [radPerSec] about hardware X. */
fun turning(startNs: Long, seconds: Double, radPerSec: Float): ByteArray =
    turning(startNs, seconds, D(radPerSec.toDouble(), 0.0, 0.0))

/** [seconds] of 1 kHz reports turning at [gyro] rad/s, in the hardware frame. */
fun turning(startNs: Long, seconds: Double, gyro: D, accel: D = D(0.0, -9.8, 0.0)): ByteArray {
    val out = ByteArrayOutputStream()
    val count = (seconds * 1000).toInt()
    val a = Triple(accel.x.toFloat(), accel.y.toFloat(), accel.z.toFloat())
    for (i in 0 until count) {
        out.write(imuReport(startNs + i * 1_000_000L, gyro.x.toFloat(), gyro.y.toFloat(), gyro.z.toFloat(), a))
    }
    return out.toByteArray()
}

/** Inverts [convention]: the hardware-frame vector that reads as [canonical]. */
fun toHardware(convention: AxisConvention, canonical: D): D {
    val raw = DoubleArray(3)
    listOf(convention.xFrom, convention.yFrom, convention.zFrom).forEachIndexed { k, axis ->
        raw[axis.index] = axis.sign * canonical[k]
    }
    return D(raw[0], raw[1], raw[2])
}

/** The 1S's measured convention, which the fake glasses use. */
val FAKE_AXES = AxisConvention(AxisConvention.Axis.X_POS, AxisConvention.Axis.Y_NEG, AxisConvention.Axis.Z_NEG)
