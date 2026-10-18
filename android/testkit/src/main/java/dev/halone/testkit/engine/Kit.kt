package dev.halone.testkit.engine

import dev.halone.glasses.api.UsbIdentity
import dev.halone.testkit.capture.Tap

data class DisplayState(val name: String, val signature: String)

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
