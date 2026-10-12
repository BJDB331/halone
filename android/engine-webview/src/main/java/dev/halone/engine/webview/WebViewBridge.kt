package dev.halone.engine.webview

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object WebViewBridge {
    private val FEATURES = listOf(
        WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL,
        WebViewFeature.POST_WEB_MESSAGE,
        WebViewFeature.WEB_MESSAGE_PORT_POST_MESSAGE,
        WebViewFeature.WEB_MESSAGE_PORT_SET_MESSAGE_CALLBACK,
        WebViewFeature.WEB_MESSAGE_PORT_CLOSE,
    )

    /**
     * Posts a page that finished loading at [url] its end of a new channel.
     * Only pages on the panel's own origin get one: a system app that navigates
     * elsewhere must not take the bridge with it, and the target origin makes
     * the platform enforce that as well. Call from `onPageFinished`.
     */
    fun connect(view: WebView, url: String, origin: String, connector: BridgeConnector, log: (String) -> Unit): Boolean {
        if (!url.startsWith("$origin/")) return false
        if (!FEATURES.all { WebViewFeature.isFeatureSupported(it) }) {
            log("  webview: bridge unsupported by this WebView")
            return false
        }
        val (host, page) = WebViewCompat.createWebMessageChannel(view)
        val message = connector.connect(host) ?: run {
            host.close()
            page.close()
            return false
        }
        WebViewCompat.postWebMessage(view, WebMessageCompat(message, arrayOf(page)), Uri.parse(origin))
        return true
    }
}
