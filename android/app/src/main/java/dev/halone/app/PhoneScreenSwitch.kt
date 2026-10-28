package dev.halone.app

import android.content.Context
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Turns the phone's own panel off, and back on, through the Android apps
 * plugin when it is installed and set up: really off, rather than the
 * black, dimmed window [PointerPad] falls back to.
 *
 * The plugin does the switching from its privileged (Shizuku) side, and
 * holds the panel off only on a lease this renews every few seconds, tied
 * to [token]: if Halone stops renewing or dies, the phone screen comes back
 * by itself, as it does if the glasses' display goes.
 *
 * The contract, served by the plugin at [AUTHORITY]: `call(method, null,
 * extras)` with method `off` (extras `token`, `display`: the glasses'
 * display id), `renew` or `on` (extras `token`), answering a bundle whose
 * `ok` says whether it was done.
 */
class PhoneScreenSwitch(private val context: Context, private val log: (String) -> Unit) {
    private val token = Binder()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "phone-screen") }
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var held = false

    /** Called on the main thread if the plugin brought the screen back by itself (glasses unplugged, plugin gone). */
    var onLost: (() -> Unit)? = null

    private val renew = object : Runnable {
        override fun run() {
            if (!held) return
            worker.execute {
                val ok = call("renew")
                main.post {
                    if (!held) return@post
                    if (ok) main.postDelayed(this, RENEW_MS) else {
                        held = false
                        log("  phone screen: the plugin ended the lease")
                        onLost?.invoke()
                    }
                }
            }
        }
    }

    /** Off, if the plugin can do it; [done] hears whether it did, on the main thread. */
    fun turnOff(glassesDisplayId: Int, done: (Boolean) -> Unit) {
        worker.execute {
            val ok = call("off") { putInt("display", glassesDisplayId) }
            main.post {
                if (ok) {
                    held = true
                    main.postDelayed(renew, RENEW_MS)
                }
                done(ok)
            }
        }
    }

    fun turnOn() {
        if (!held) return
        held = false
        main.removeCallbacks(renew)
        worker.execute { call("on") }
    }

    /** One call to the plugin; false where it is missing, not set up, or said no. */
    private fun call(method: String, more: Bundle.() -> Unit = {}): Boolean = runCatching {
        val extras = Bundle().apply {
            putBinder("token", token)
            more()
        }
        context.contentResolver.call(Uri.parse("content://$AUTHORITY"), method, null, extras)?.getBoolean("ok") == true
    }.onFailure {
        // No plugin, or an older one without this: the dimmed window stands in, quietly.
        if (it !is IllegalArgumentException) log("  phone screen: $method failed: ${it.message}")
    }.getOrDefault(false)

    companion object {
        /** Where the Android apps plugin answers; also in the manifest's queries. */
        const val AUTHORITY = "dev.halone.plugin.android.phonescreen"
        private const val RENEW_MS = 3_000L
    }
}
