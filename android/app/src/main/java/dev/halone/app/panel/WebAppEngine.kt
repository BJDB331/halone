package dev.halone.app.panel

import android.content.Context
import dev.halone.compositor.PanelEngine
import dev.halone.compositor.TouchPhase
import dev.halone.shell.WebAppControl
import org.json.JSONArray
import org.json.JSONObject

/**
 * An installed web app's window: one Gecko page and nothing over it. With no
 * prompt bar, what the page asks permission for is asked in a dialog, and
 * links to new windows open in the browser.
 */
class WebAppEngine(
    context: Context,
    private val content: GeckoPanel,
    private val host: BrowserHost,
) : PanelEngine by content, WebAppControl {

    override val title: String get() = content.active.title
    override val url: String? get() = content.active.url
    override var onChange: (() -> Unit)? = null

    /** For the shell to give the window the screen's size while the page is full screen. */
    var onFullScreenChange: ((Boolean) -> Unit)? = null

    private val pageMenu = PageMenu(content, host, standalone = true)
    private var lastPressX = content.widthPx / 2f
    private var lastPressY = content.heightPx / 2f

    /** The permission prompt on show, and the dialog showing it. */
    private var asking: Pair<Int, Int>? = null

    init {
        content.promptDelegate = GeckoPrompts(context, content, host) { lastPressX to lastPressY }
        content.onNewWindow = { uri ->
            host.openInBrowser(uri, false)
            true
        }
        content.onFullScreen = { onFullScreenChange?.invoke(it) }
        content.onChange = {
            askNext()
            onChange?.invoke()
        }
    }

    override fun navigate(url: String) = content.load(url)

    fun setDesktopUserAgent(desktop: Boolean) = content.setDesktopUserAgent(desktop)

    private fun askNext() {
        val prompt = content.active.prompts.firstOrNull()
        val current = asking
        if (current != null) {
            if (current.first == prompt?.id) return
            asking = null
            host.cancelPopup(current.second)
        }
        prompt ?: return
        val dialog = JSONObject()
            .put("kind", "message")
            .put("title", "")
            .put("message", "${prompt.host} ${ASKS[prompt.kind] ?: "wants ${prompt.kind}"}")
            .put("buttons", JSONArray(listOf(button(BLOCK, "Block"), button(ALLOW, "Allow", primary = true))))
        val dialogId = host.showDialog(dialog) { choice, _ ->
            if (asking?.first != prompt.id) return@showDialog
            asking = null
            content.answerPrompt(prompt.id, allow = choice == ALLOW, remember = choice != null && prompt.rememberable)
        } ?: return
        asking = prompt.id to dialogId
    }

    private fun button(id: String, label: String, primary: Boolean = false) =
        JSONObject().put("id", id).put("label", label).put("primary", primary)

    override fun dispatchContextMenu(x: Float, y: Float) = pageMenu.show(x, y, y)

    /** A link middle-clicked opens in the browser, the app having no tabs. */
    override fun dispatchMiddleClick(x: Float, y: Float) = content.middleClick(x, y) { link ->
        link?.let { host.openInBrowser(it, content.active.private) }
    }

    override fun dispatchTap(x: Float, y: Float) {
        lastPressX = x
        lastPressY = y
        content.dispatchTap(x, y)
    }

    override fun dispatchTouch(phase: TouchPhase, x: Float, y: Float) {
        if (phase == TouchPhase.DOWN) {
            lastPressX = x
            lastPressY = y
        }
        content.dispatchTouch(phase, x, y)
    }

    override fun close() {
        asking?.let { host.cancelPopup(it.second) }
        asking = null
        content.close()
    }

    companion object {
        private const val ALLOW = "allow"
        private const val BLOCK = "block"

        /** As the browser's prompt bar words them, in `browser/App.vue`. */
        private val ASKS = mapOf(
            "geolocation" to "wants to know your location",
            "popup" to "wants to open a pop-up window",
            "microphone" to "wants to use your microphone",
            "clipboard" to "wants to read your clipboard",
            "persistent-storage" to "wants to keep its data on this device",
            "drm" to "wants to play protected content",
            "storage-access" to "wants to use its cookies on this site",
            "local-network" to "wants to reach devices on your network",
            "local-device" to "wants to reach devices on this phone",
        )
    }
}
