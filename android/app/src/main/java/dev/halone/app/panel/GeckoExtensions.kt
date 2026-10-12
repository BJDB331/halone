package dev.halone.app.panel

import android.os.Handler
import android.os.Looper
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * The extensions every web window runs: the page helper, built in, which tells
 * the browser what is under the pointer and edits text for it, and uBlock
 * Origin, installed from addons.mozilla.org while the ad blocker setting is
 * on. One per runtime, which is one per process.
 */
class GeckoExtensions private constructor(private val runtime: GeckoRuntime, private val log: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var helper: WebExtension? = null
    private val waiting = mutableListOf<Pair<GeckoSession, WebExtension.MessageDelegate>>()
    private var adBlockerWanted: Boolean? = null
    private var adBlockerBusy = false

    init {
        runtime.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(
                    if (extension.id == UBLOCK_ID) WebExtension.PermissionPromptResponse(true, true, false)
                    else WebExtension.PermissionPromptResponse(false, false, false),
                )
        }
        runtime.webExtensionController.ensureBuiltIn(HELPER_URI, HELPER_ID).accept(
            { extension ->
                main.post {
                    helper = extension
                    log("  gecko: page helper ready")
                    waiting.forEach { (session, delegate) -> bind(session, delegate) }
                    waiting.clear()
                }
            },
            { error -> log("  gecko: page helper failed to install: $error") },
        )
    }

    /** Hears the page helper of [session]; until the helper is installed, as soon as it is. UI thread. */
    fun attach(session: GeckoSession, delegate: WebExtension.MessageDelegate) {
        if (helper == null) waiting += session to delegate else bind(session, delegate)
    }

    fun detach(session: GeckoSession) {
        waiting.removeAll { it.first === session }
    }

    private fun bind(session: GeckoSession, delegate: WebExtension.MessageDelegate) {
        val extension = helper ?: return
        session.webExtensionController.setMessageDelegate(extension, delegate, NATIVE_APP)
    }

    /** Installs or enables uBlock Origin, or disables it. UI thread. */
    fun setAdBlocker(enabled: Boolean) {
        if (adBlockerWanted == enabled) return
        adBlockerWanted = enabled
        applyAdBlocker()
    }

    private fun applyAdBlocker() {
        if (adBlockerBusy) return
        val wanted = adBlockerWanted ?: return
        adBlockerBusy = true
        val controller = runtime.webExtensionController
        fun done(message: String) = main.post {
            log("  gecko: $message")
            adBlockerBusy = false
            if (adBlockerWanted != wanted) applyAdBlocker()
        }
        controller.list().accept({ extensions ->
            val installed = extensions?.firstOrNull { it.id == UBLOCK_ID }
            val step: GeckoResult<WebExtension>? = when {
                installed == null && wanted -> controller.install(UBLOCK_URL)
                installed == null -> null
                wanted && !installed.metaData.enabled -> controller.enable(installed, WebExtensionController.EnableSource.USER)
                !wanted && installed.metaData.enabled -> controller.disable(installed, WebExtensionController.EnableSource.USER)
                else -> null
            }
            if (step == null) {
                done("ad blocker ${if (wanted) "on" else "off"}")
                return@accept
            }
            step.accept(
                { extension -> done("ad blocker ${if (wanted) "on" else "off"}: ${extension?.metaData?.name} ${extension?.metaData?.version}") },
                { error -> done("ad blocker could not be turned ${if (wanted) "on" else "off"}: $error") },
            )
        }, { error -> done("could not list extensions: $error") })
    }

    companion object {
        private const val HELPER_URI = "resource://android/assets/extensions/page/"
        private const val HELPER_ID = "page@halone.dev"

        /** What the page helper's content script names in `connectNative`. */
        const val NATIVE_APP = "halone"

        private const val UBLOCK_ID = "uBlock0@raymondhill.net"
        private const val UBLOCK_URL = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"

        @Volatile
        private var instance: GeckoExtensions? = null

        fun of(runtime: GeckoRuntime, log: (String) -> Unit): GeckoExtensions =
            instance ?: synchronized(this) { instance ?: GeckoExtensions(runtime, log).also { instance = it } }
    }
}
