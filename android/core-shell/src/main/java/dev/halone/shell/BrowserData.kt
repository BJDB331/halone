package dev.halone.shell

import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val url: String, val title: String, val visitedAt: Long) {
    fun toJson(): JSONObject = JSONObject().put("url", url).put("title", title).put("visitedAt", visitedAt)
}

/**
 * Pages visited in ordinary tabs, newest first, one entry per address: a visit
 * to a page already there moves it to the top. Private tabs never reach it.
 */
class BrowserHistory(
    private val store: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val limit: Int = LIMIT,
) {
    private val entries = ArrayList<HistoryEntry>()

    var onChange: (() -> Unit)? = null

    init {
        store.load()?.let { text ->
            runCatching {
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    entries += HistoryEntry(json.getString("url"), json.optString("title"), json.getLong("visitedAt"))
                }
            }
        }
    }

    fun visited(url: String, title: String? = null) {
        if (!recordable(url)) return
        val previous = entries.firstOrNull { it.url == url }
        entries.remove(previous)
        entries.add(0, HistoryEntry(url, title?.takeIf { it.isNotBlank() } ?: previous?.title.orEmpty(), clock()))
        while (entries.size > limit) entries.removeAt(entries.lastIndex)
        changed()
    }

    /** A page's title, which arrives after the visit. */
    fun titled(url: String, title: String) {
        val index = entries.indexOfFirst { it.url == url }
        if (index < 0 || title.isBlank() || entries[index].title == title) return
        entries[index] = entries[index].copy(title = title)
        changed()
    }

    fun list(query: String? = null, limit: Int = Int.MAX_VALUE): List<HistoryEntry> {
        val words = query?.trim()?.lowercase()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
        return entries.asSequence()
            .filter { entry -> words.all { entry.url.lowercase().contains(it) || entry.title.lowercase().contains(it) } }
            .take(limit)
            .toList()
    }

    fun remove(url: String) {
        if (entries.removeAll { it.url == url }) changed()
    }

    fun clear() {
        if (entries.isEmpty()) return
        entries.clear()
        changed()
    }

    private fun changed() {
        store.save(JSONArray(entries.map { it.toJson() }).toString())
        onChange?.invoke()
    }

    companion object {
        const val LIMIT = 5_000

        fun recordable(url: String) = url.startsWith("https://") || url.startsWith("http://")
    }
}

data class Bookmark(val id: String, val url: String, val title: String, val iconUrl: String?, val addedAt: Long) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("url", url)
        .put("title", title)
        .put("iconUrl", iconUrl ?: JSONObject.NULL)
        .put("addedAt", addedAt)
}

/** Bookmarked pages in the order they were added, which is the order the launcher shows them in. */
class Bookmarks(
    private val store: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val items = ArrayList<Bookmark>()
    private var nextId = 1

    var onChange: (() -> Unit)? = null

    init {
        store.load()?.let { text ->
            runCatching {
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    items += Bookmark(
                        json.getString("id"),
                        json.getString("url"),
                        json.optString("title"),
                        json.optString("iconUrl").takeIf { it.isNotEmpty() && !json.isNull("iconUrl") },
                        json.optLong("addedAt"),
                    )
                }
            }
            nextId = (items.mapNotNull { it.id.removePrefix("b").toIntOrNull() }.maxOrNull() ?: 0) + 1
        }
    }

    fun list(): List<Bookmark> = items.toList()

    fun find(url: String?): Bookmark? = url?.let { items.firstOrNull { bookmark -> bookmark.url == it } }

    /** Adds a bookmark, or returns the one the address already has. */
    fun add(url: String, title: String, iconUrl: String? = null): Bookmark {
        if (!BrowserHistory.recordable(url)) throw HaloneException("invalid-url", "only http and https pages can be bookmarked: $url")
        find(url)?.let { return it }
        val bookmark = Bookmark("b${nextId++}", url, title.ifBlank { url }.take(MAX_TITLE), iconUrl, clock())
        items += bookmark
        changed()
        return bookmark
    }

    fun update(id: String, title: String?, url: String?): Bookmark {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) throw HaloneException("no-such-bookmark", "no such bookmark: $id")
        if (url != null && !BrowserHistory.recordable(url)) throw HaloneException("invalid-url", "not a web address: $url")
        val updated = items[index].copy(
            title = title?.ifBlank { null }?.take(MAX_TITLE) ?: items[index].title,
            url = url ?: items[index].url,
        )
        items[index] = updated
        changed()
        return updated
    }

    fun remove(id: String) {
        if (items.removeAll { it.id == id }) changed()
    }

    /** A page's icon, learnt on a later visit. */
    fun iconFound(url: String, iconUrl: String) {
        val index = items.indexOfFirst { it.url == url }
        if (index < 0 || items[index].iconUrl == iconUrl) return
        items[index] = items[index].copy(iconUrl = iconUrl)
        changed()
    }

    private fun changed() {
        store.save(JSONArray(items.map { it.toJson() }).toString())
        onChange?.invoke()
    }

    companion object {
        private const val MAX_TITLE = 200
    }
}

enum class DownloadState(val wire: String) {
    DOWNLOADING("downloading"),
    DONE("done"),
    FAILED("failed"),
    CANCELLED("cancelled"),
}

data class Download(
    val id: Int,
    val url: String,
    val fileName: String,
    val path: String?,
    val mimeType: String?,
    val totalBytes: Long?,
    val receivedBytes: Long,
    val state: DownloadState,
    val startedAt: Long,
    val error: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("url", url)
        .put("fileName", fileName)
        .put("path", path ?: JSONObject.NULL)
        .put("mimeType", mimeType ?: JSONObject.NULL)
        .put("totalBytes", totalBytes ?: JSONObject.NULL)
        .put("receivedBytes", receivedBytes)
        .put("state", state.wire)
        .put("startedAt", startedAt)
        .put("error", error ?: JSONObject.NULL)
}

/**
 * The download list. The bytes are the platform's business; this keeps what
 * the list shows, and remembers finished downloads across restarts. A download
 * that was still going when the app stopped comes back as failed.
 */
class Downloads(
    private val store: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val items = ArrayList<Download>()
    private val cancellers = HashMap<Int, () -> Unit>()
    private var nextId = 1

    var onChange: (() -> Unit)? = null

    init {
        store.load()?.let { text ->
            runCatching {
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    val state = DownloadState.entries.firstOrNull { it.wire == json.optString("state") } ?: DownloadState.FAILED
                    items += Download(
                        id = json.getInt("id"),
                        url = json.getString("url"),
                        fileName = json.getString("fileName"),
                        path = json.optString("path").takeIf { !json.isNull("path") && it.isNotEmpty() },
                        mimeType = json.optString("mimeType").takeIf { !json.isNull("mimeType") && it.isNotEmpty() },
                        totalBytes = if (json.isNull("totalBytes")) null else json.optLong("totalBytes"),
                        receivedBytes = json.optLong("receivedBytes"),
                        state = if (state == DownloadState.DOWNLOADING) DownloadState.FAILED else state,
                        startedAt = json.optLong("startedAt"),
                        error = if (state == DownloadState.DOWNLOADING) "interrupted" else json.optString("error").takeIf { !json.isNull("error") && it.isNotEmpty() },
                    )
                }
            }
            nextId = (items.maxOfOrNull { it.id } ?: 0) + 1
        }
    }

    fun list(): List<Download> = items.sortedByDescending { it.startedAt }

    val active: Int get() = items.count { it.state == DownloadState.DOWNLOADING }

    fun find(id: Int): Download = items.firstOrNull { it.id == id } ?: throw HaloneException("no-such-download", "no such download: $id")

    fun start(url: String, fileName: String, mimeType: String?, totalBytes: Long?, cancel: () -> Unit): Int {
        val id = nextId++
        items += Download(id, url, fileName, null, mimeType, totalBytes?.takeIf { it >= 0 }, 0, DownloadState.DOWNLOADING, clock(), null)
        cancellers[id] = cancel
        changed()
        return id
    }

    /** Progress, which the caller should not report more than a few times a second. */
    fun progress(id: Int, receivedBytes: Long, path: String? = null) = update(id) {
        if (it.state != DownloadState.DOWNLOADING) it else it.copy(receivedBytes = receivedBytes, path = path ?: it.path)
    }

    fun finish(id: Int, path: String, fileName: String = find(id).fileName) {
        cancellers.remove(id)
        update(id) { it.copy(state = DownloadState.DONE, path = path, fileName = fileName, totalBytes = it.totalBytes ?: it.receivedBytes) }
    }

    fun fail(id: Int, error: String) {
        cancellers.remove(id)
        update(id) { if (it.state == DownloadState.DOWNLOADING) it.copy(state = DownloadState.FAILED, error = error) else it }
    }

    fun cancel(id: Int) {
        find(id)
        cancellers.remove(id)?.invoke()
        update(id) { if (it.state == DownloadState.DOWNLOADING) it.copy(state = DownloadState.CANCELLED) else it }
    }

    fun remove(id: Int) {
        if (find(id).state == DownloadState.DOWNLOADING) throw HaloneException("busy", "cancel the download first")
        items.removeAll { it.id == id }
        changed()
    }

    fun clear() {
        if (items.removeAll { it.state != DownloadState.DOWNLOADING }) changed()
    }

    private fun update(id: Int, change: (Download) -> Download) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val next = change(items[index])
        if (next == items[index]) return
        items[index] = next
        changed()
    }

    private fun changed() {
        store.save(JSONArray(items.map { it.toJson() }).toString())
        onChange?.invoke()
    }
}

/** What `browser.clearData` asks for. */
data class ClearData(val history: Boolean, val cookies: Boolean, val cache: Boolean)

/** The browser's own records, shared by every web window. */
class BrowserData(val history: BrowserHistory, val bookmarks: Bookmarks, val downloads: Downloads)
