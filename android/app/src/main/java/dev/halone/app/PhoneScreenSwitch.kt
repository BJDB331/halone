package dev.halone.app

import android.content.Context
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import dev.halone.plugin.android.HelperBridge
import dev.halone.plugin.android.IDisplayService
import java.util.concurrent.Executors

/**
 * Turns the phone's own panel off, and back on, through the Android apps
 * shell helper when it is running: really off, rather than the black,
 * dimmed window [PointerPad] falls back to. Holds a lease the helper expects
 * renewed every few seconds, so a crash here brings the phone screen back.
 */
class PhoneScreenSwitch(private val context: Context, private val log: (String) -> Unit) {
    private val token = Binder()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "phone-screen") }
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var held: IDisplayService? = null

    /** Called on the main thread if the helper brought the screen back by itself (glasses unplugged, helper gone). */
    var onLost: (() -> Unit)? = null

    private val renew = object : Runnable {
        override fun run() {
            val service = held ?: return
            worker.execute {
                val ok = runCatching { service.phoneScreen(RENEW, token, 0) }
                    .onFailure { log("  phone screen: lease ended: ${it.message}") }
                    .isSuccess
                main.post {
                    if (held !== service) return@post
                    if (ok) main.postDelayed(this, RENEW_MS) else {
                        held = null
                        onLost?.invoke()
                    }
                }
            }
        }
    }

    /** Off, if the helper can do it; [done] hears whether it did, on the main thread. */
    fun turnOff(glassesDisplayId: Int, done: (Boolean) -> Unit) {
        worker.execute {
            val service = helper()
            val ok = service != null && runCatching { service.phoneScreen(OFF, token, glassesDisplayId) }
                .onFailure { log("  phone screen: couldn't turn it off: ${it.message}") }
                .isSuccess
            main.post {
                if (ok) {
                    held = service
                    main.postDelayed(renew, RENEW_MS)
                }
                done(ok)
            }
        }
    }

    fun turnOn() {
        val service = held ?: return
        held = null
        main.removeCallbacks(renew)
        worker.execute { runCatching { service.phoneScreen(ON, token, 0) }.onFailure { log("  phone screen: $it") } }
    }

    private fun helper(): IDisplayService? = runCatching {
        val uri = Uri.parse("content://" + HelperBridge.authority(context.packageName))
        context.contentResolver.call(uri, "get", null, null)?.getBinder("binder")?.let { IDisplayService.Stub.asInterface(it) }
    }.getOrNull()

    private companion object {
        const val OFF = 1
        const val RENEW = 2
        const val ON = 3
        const val RENEW_MS = 3_000L
    }
}
