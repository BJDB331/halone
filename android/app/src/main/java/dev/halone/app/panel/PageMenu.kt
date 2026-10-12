package dev.halone.app.panel

import dev.halone.shell.MenuItem
import org.json.JSONObject

/**
 * The context menu over a web page. Asks the page's helper what is there and
 * offers what fits: a link's, an image's or a video's actions, editing in a
 * field, the selection, and otherwise the page's own. A page that shows its
 * own menu there gets the event instead, as with a right click on a desktop.
 * In an installed app's window ([standalone]) there are no tabs, so what
 * would open one opens in the browser.
 */
class PageMenu(
    private val content: GeckoPanel,
    private val host: BrowserHost,
    private val standalone: Boolean,
) {
    /** Shows the menu at ([x], [y]) on the window, over what is at [pageY] on the page. */
    fun show(x: Float, y: Float, pageY: Float) {
        val tab = content.active
        content.inspect(x, pageY) { info ->
            if (content.active !== tab || content.tabs.none { it === tab }) return@inspect
            if (info?.optBoolean("handled") == true) return@inspect
            show(tab, x, y, info ?: JSONObject())
        }
    }

    private fun JSONObject.text(key: String): String? = optString(key).takeIf { has(key) && !isNull(key) && it.isNotEmpty() }

    private fun open(tab: GeckoPanel.Tab, url: String, private: Boolean = tab.private) =
        if (standalone) host.openInBrowser(url, private) else content.newTab(url, private)

    private fun show(tab: GeckoPanel.Tab, x: Float, y: Float, info: JSONObject) {
        val link = info.text("link")
        val image = info.text("image")
        val media = info.text("media")
        val mediaLabel = if (info.optString("mediaKind") == "audio") "audio" else "video"
        val editable = info.optBoolean("editable")
        val selection = info.text("selection")
        val items = mutableListOf<MenuItem>()
        fun section(vararg entries: MenuItem?) {
            if (items.isNotEmpty()) items += MenuItem.SEPARATOR
            items += entries.filterNotNull()
        }
        if (link != null) section(
            MenuItem("open-tab", if (standalone) "Open link in browser" else "Open link in new tab"),
            MenuItem("open-private", "Open link in private tab"),
            if (standalone) null else MenuItem("open-window", "Open link in new window"),
            MenuItem("copy-link", "Copy link address"),
            MenuItem("download-link", "Download link"),
        )
        if (image != null) section(
            MenuItem("open-image", if (standalone) "Open image in browser" else "Open image in new tab"),
            MenuItem("save-image", "Save image"),
            MenuItem("copy-image", "Copy image address"),
        )
        if (media != null && !media.startsWith("blob:")) section(
            MenuItem("save-media", "Save $mediaLabel"),
            MenuItem("copy-media", "Copy $mediaLabel address"),
        )
        if (editable) section(
            MenuItem("cut", "Cut", disabled = selection == null),
            MenuItem("copy", "Copy", disabled = selection == null),
            MenuItem("paste", "Paste", disabled = host.clipboardText().isNullOrEmpty()),
            MenuItem("select-all", "Select all"),
        ) else if (selection != null) section(
            MenuItem("copy", "Copy"),
            MenuItem("search", "Search for “${selection.trim().replace(Regex("\\s+"), " ").let { if (it.length > 24) it.take(24) + "…" else it }}”"),
        )
        val pageUrl = tab.url
        if (items.isEmpty()) {
            section(
                MenuItem("back", "Back", disabled = !tab.canGoBack),
                MenuItem("forward", "Forward", disabled = !tab.canGoForward),
                MenuItem("reload", "Reload"),
            )
            if (standalone) section(
                MenuItem("open-page", "Open in browser", disabled = pageUrl == null),
                MenuItem("copy-page", "Copy page address", disabled = pageUrl == null),
            ) else section(
                MenuItem("bookmark", if (pageUrl != null && host.isBookmarked(pageUrl)) "Remove bookmark" else "Bookmark page", disabled = pageUrl == null),
                MenuItem("copy-page", "Copy page address", disabled = pageUrl == null),
            )
        }
        host.showMenu(items, x, y) { choice ->
            if (choice == null || content.tabs.none { it === tab }) return@showMenu
            when (choice) {
                "open-tab" -> link?.let { open(tab, it) }
                "open-private" -> link?.let { open(tab, it, private = true) }
                "open-window" -> link?.let { host.openWindow(it, tab.private) }
                "copy-link" -> link?.let(host::setClipboardText)
                "download-link" -> link?.let { host.download(it, pageUrl, tab.private) }
                "open-image" -> image?.let { open(tab, it) }
                "save-image" -> image?.let { host.download(it, pageUrl, tab.private) }
                "copy-image" -> image?.let(host::setClipboardText)
                "save-media" -> media?.let { host.download(it, pageUrl, tab.private) }
                "copy-media" -> media?.let(host::setClipboardText)
                "cut" -> selection?.let {
                    host.setClipboardText(it)
                    content.edit("cut")
                }
                "copy" -> selection?.let(host::setClipboardText)
                "paste" -> host.clipboardText()?.let { content.edit("paste", it) }
                "select-all" -> content.edit("selectAll")
                "search" -> selection?.let { open(tab, host.searchUrl(it.trim())) }
                "back" -> content.back()
                "forward" -> content.forward()
                "reload" -> content.reload()
                "bookmark" -> host.toggleBookmark()
                "open-page" -> pageUrl?.let { host.openInBrowser(it, false) }
                "copy-page" -> pageUrl?.let(host::setClipboardText)
            }
        }
    }
}
