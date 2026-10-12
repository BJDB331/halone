package dev.halone.shell

import org.json.JSONArray
import org.json.JSONObject

data class BrowserTab(val id: Int, val title: String, val url: String, val loading: Boolean, val private: Boolean = false)

/** A permission a page asked for, waiting for the wearer; [kind] is the protocol's `PermissionKind`. */
data class BrowserPrompt(val id: Int, val kind: String, val host: String, val rememberable: Boolean) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("kind", kind).put("host", host).put("rememberable", rememberable)
}

data class BrowserState(
    val tabs: List<BrowserTab>,
    val activeTab: Int,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val loading: Boolean,
    val prompt: BrowserPrompt? = null,
) {
    val active: BrowserTab? get() = tabs.firstOrNull { it.id == activeTab }

    fun toJson(): JSONObject = JSONObject()
        .put("tabs", JSONArray(tabs.map {
            JSONObject().put("id", it.id).put("title", it.title).put("url", it.url).put("loading", it.loading).put("private", it.private)
        }))
        .put("activeTab", activeTab)
        .put("canGoBack", canGoBack)
        .put("canGoForward", canGoForward)
        .put("loading", loading)
        .put("prompt", prompt?.toJson() ?: JSONObject.NULL)
}

/** A web window's tabs and navigation, as its browser chrome drives them. */
interface BrowserControl {
    fun state(): BrowserState
    fun navigate(url: String)
    fun back()
    fun forward()
    fun reload()
    fun stop()
    fun newTab(url: String?, private: Boolean = false)
    fun selectTab(id: Int)

    /** Closes a tab other than the last; closing the last one is closing the window. */
    fun closeTab(id: Int)

    /** Answers a [BrowserPrompt]; [remember] keeps the answer for the site where it can be kept. */
    fun answerPrompt(id: Int, allow: Boolean, remember: Boolean): Unit =
        throw HaloneException("no-such-prompt", "no such prompt: $id")

    /** The active page's own icon, where it declared one. */
    fun iconUrl(): String? = null

    /** The active page's web app manifest, as Gecko processed it, where it links one. */
    fun manifest(): JSONObject? = null

    /** Called on any change to [state]. */
    var onChange: (() -> Unit)?
}

/** The window of an installed web app: one page, no chrome. */
interface WebAppControl {
    val title: String
    val url: String?
    fun navigate(url: String)

    /** Called when [title] or [url] changes. */
    var onChange: (() -> Unit)?
}
