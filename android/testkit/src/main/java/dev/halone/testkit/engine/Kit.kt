package dev.halone.testkit.engine

import dev.halone.glasses.api.UsbIdentity
import dev.halone.testkit.capture.Tap

data class DisplayState(val name: String, val signature: String)

/** What the glasses report over their HID channel about their USB functions and firmware. */
data class GlassesHidState(val functions: String, val networkOn: Boolean, val mcuVersion: String?, val dspVersion: String?)

/** What a script can reach. The Android side lives in `AndroidKit`; tests use a fake. */
interface Kit {
    val io: Io
    val session: Session

    fun text(id: Int, vararg args: Any): String

    fun choice(id: String, label: Int) = Choice(id, text(label))

    fun usbDevices(): List<UsbIdentity>

    /** Records phone, USB, network and display state under `recon/<label>/`. */
    fun snapshot(label: String)

    fun externalDisplay(): DisplayState?

    /** True when the phone brought up a network to the glasses itself, so no USB access is needed. */
    fun hasSystemLink(hosts: List<String>): Boolean

    /** True when the attached glasses offer a USB Ethernet function the in-app stack can use. */
    fun hasUsbNetwork(): Boolean

    /** Asks for USB access to the glasses, for the in-app network stack. False if refused. */
    suspend fun requestUsbAccess(): Boolean

    /** Reads the glasses' USB function word and firmware versions. Needs USB access. */
    suspend fun readGlassesState(): Result<GlassesHidState>

    /** Turns the glasses' USB network functions on or back off; they re-enumerate afterwards. */
    suspend fun setGlassesNetwork(on: Boolean): Result<Unit>

    suspend fun openTcp(
        name: String,
        hosts: List<String>,
        port: Int,
        alwaysRecord: Boolean = false,
        listener: ((ByteArray, Int) -> Unit)? = null,
    ): Result<Tap>
}

interface Profile {
    val id: String

    /** Steps [run] will report through [Io.progress], counting the shared ones. */
    val steps: Int

    fun matches(device: UsbIdentity): Boolean

    suspend fun run(kit: Kit, glasses: UsbIdentity)
}
