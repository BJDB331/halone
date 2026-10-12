package dev.halone.engine.webview

import androidx.webkit.WebMessagePortCompat

/**
 * Hands the shell its end of a fresh message channel each time a page on
 * `https://halone.local/` finishes loading, and returns the text to post to the
 * page with the other end, or null to close the channel unused.
 *
 * Kept free of the protocol on purpose: this module knows how to deliver a
 * port to a page, not what is said over it.
 */
fun interface BridgeConnector {
    fun connect(port: WebMessagePortCompat): String?
}
