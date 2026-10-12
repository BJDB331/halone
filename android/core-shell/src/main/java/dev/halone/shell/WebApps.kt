package dev.halone.shell

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class ManifestIcon(val src: String, val sizes: List<Int>, val type: String?, val purposes: List<String>) {
    /** The larger side of its largest size, or 0 for `any`, a vector, or no size given. */
    val largest: Int get() = sizes.maxOrNull() ?: 0
}

/**
 * What a site's web app manifest says about installing it, as Gecko processed
 * it: URLs already absolute, `display` already defaulted. Only manifests that
 * [parse] accepts can be installed.
 */
data class WebManifest(
    /** The app's identity: the manifest's `id`, or failing that its start URL. */
    val identity: String,
    val name: String,
    val shortName: String?,
    val startUrl: String,
    val scope: String,
    val display: String,
    val icons: List<ManifestIcon>,
    val themeColor: String?,
    val backgroundColor: String?,
) {
    /** The id the app is installed under, the same every time the site is installed. */
    val appId: String get() = WebApp.ID_PREFIX + sha1(identity).take(ID_HEX)

    /**
     * The icon to keep: one meant for any background, at least [minimumPx] and
     * the smallest such, or failing that the largest there is. Masks and
     * monochrome icons are for the system to colour, which this one does not.
     */
    fun bestIcon(minimumPx: Int = ICON_PX): ManifestIcon? {
        val usable = icons.filter { icon ->
            (icon.purposes.isEmpty() || "any" in icon.purposes) && icon.type?.contains("svg") != true && !icon.src.endsWith(".svg")
        }
        return usable.filter { it.largest >= minimumPx }.minByOrNull { it.largest } ?: usable.maxByOrNull { it.largest }
    }

    companion object {
        const val ICON_PX = 192
        private const val ID_HEX = 12
        private val INSTALLABLE_DISPLAYS = setOf("standalone", "fullscreen", "minimal-ui", "window-controls-overlay")

        /** The manifest when the app can be installed: a name, a web start URL inside its scope, and a display other than `browser`. */
        fun parse(json: JSONObject): WebManifest? {
            val name = json.text("name") ?: json.text("short_name") ?: return null
            val startUrl = json.text("start_url")?.takeIf(::isWeb) ?: return null
            val scope = json.text("scope")?.takeIf(::isWeb) ?: defaultScope(startUrl)
            if (!startUrl.startsWith(scope)) return null
            val overrides = json.optJSONArray("display_override")?.strings().orEmpty()
            val display = (overrides + listOfNotNull(json.text("display"))).firstOrNull { it in INSTALLABLE_DISPLAYS } ?: return null
            return WebManifest(
                identity = json.text("id")?.takeIf(::isWeb) ?: startUrl,
                name = name.take(MAX_NAME),
                shortName = json.text("short_name")?.take(MAX_NAME),
                startUrl = startUrl,
                scope = scope,
                display = display,
                icons = json.optJSONArray("icons")?.let(::icons).orEmpty(),
                themeColor = json.text("theme_color"),
                backgroundColor = json.text("background_color"),
            )
        }

        private fun isWeb(url: String) = url.startsWith("https://") || url.startsWith("http://")

        /** The start URL's directory, as the spec defaults it. */
        private fun defaultScope(startUrl: String): String {
            val uri = java.net.URI(startUrl)
            val path = uri.path.orEmpty().substringBeforeLast('/', "") + "/"
            return java.net.URI(uri.scheme, uri.authority, path, null, null).toString()
        }

        /** Gecko hands sizes and purposes over as lists or as the manifest's own space-separated text. */
        private fun icons(array: JSONArray): List<ManifestIcon> = (0 until array.length()).mapNotNull { index ->
            val icon = array.optJSONObject(index) ?: return@mapNotNull null
            val src = icon.text("src")?.takeIf { isWeb(it) || it.startsWith("data:") } ?: return@mapNotNull null
            ManifestIcon(
                src = src,
                sizes = words(icon.opt("sizes")).mapNotNull { size ->
                    size.lowercase().split('x').takeIf { it.size == 2 }?.mapNotNull(String::toIntOrNull)?.maxOrNull()
                },
                type = icon.text("type"),
                purposes = words(icon.opt("purpose")).map { it.lowercase() },
            )
        }

        private fun words(value: Any?): List<String> = when (value) {
            is JSONArray -> value.strings().flatMap { it.split(' ') }
            is String -> value.split(' ')
            else -> emptyList()
        }.filter { it.isNotBlank() }

        private fun JSONArray.strings() = (0 until length()).mapNotNull { opt(it) as? String }

        private fun JSONObject.text(key: String): String? = (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

        private const val MAX_NAME = 80

        private fun sha1(text: String) =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** A site installed as an app: it opens in a window of its own, without the browser's chrome and without the bridge. */
data class WebApp(
    val id: String,
    val name: String,
    val startUrl: String,
    val scope: String,
    val display: String,
    /** The icon as a `data:` URL, kept so the launcher shows it offline; null when the site has none that could be read. */
    val iconUrl: String?,
    val themeColor: String?,
    val backgroundColor: String?,
    val installedAt: Long,
) {
    fun inScope(url: String) = url.startsWith(scope)

    fun toAppInfo() = AppInfo(
        id = id,
        name = name,
        description = runCatching { java.net.URI(startUrl).host }.getOrNull().orEmpty(),
        icon = "",
        entry = startUrl,
        widthPx = WIDTH_PX,
        heightPx = HEIGHT_PX,
        kind = AppKind.WEB,
        iconUrl = iconUrl,
        scope = scope,
        color = themeColor,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("startUrl", startUrl)
        .put("scope", scope)
        .put("display", display)
        .put("iconUrl", iconUrl ?: JSONObject.NULL)
        .put("themeColor", themeColor ?: JSONObject.NULL)
        .put("backgroundColor", backgroundColor ?: JSONObject.NULL)
        .put("installedAt", installedAt)

    companion object {
        const val ID_PREFIX = "web-"
        const val WIDTH_PX = 1280
        const val HEIGHT_PX = 800

        fun fromJson(json: JSONObject): WebApp {
            fun optional(key: String) = json.optString(key).takeIf { it.isNotEmpty() && !json.isNull(key) }
            return WebApp(
                id = json.getString("id"),
                name = json.getString("name"),
                startUrl = json.getString("startUrl"),
                scope = json.getString("scope"),
                display = json.optString("display", "standalone"),
                iconUrl = optional("iconUrl"),
                themeColor = optional("themeColor"),
                backgroundColor = optional("backgroundColor"),
                installedAt = json.optLong("installedAt"),
            )
        }
    }
}

/**
 * The sites installed as apps, in the order they were installed.
 * [loadIcon] fetches an icon and hands back a `data:` URL, or null, from any
 * thread; the store is otherwise used on one thread.
 */
class WebApps(
    private val store: SettingsStore,
    private val loadIcon: (url: String, done: (String?) -> Unit) -> Unit = { _, done -> done(null) },
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where [install] finishes: the thread the store is used on. */
    private val onMain: (Runnable) -> Unit = Runnable::run,
) {
    private val apps = ArrayList<WebApp>()

    var onChange: (() -> Unit)? = null

    init {
        store.load()?.let { text ->
            runCatching {
                val array = JSONArray(text)
                for (i in 0 until array.length()) runCatching { apps += WebApp.fromJson(array.getJSONObject(i)) }
            }
        }
    }

    fun list(): List<WebApp> = apps.toList()

    fun find(id: String?): WebApp? = id?.let { apps.firstOrNull { app -> app.id == it } }

    /** Installs [manifest]'s app, or reinstalls it with what the manifest says now; [done] hears it once its icon is in. */
    fun install(manifest: WebManifest, done: (WebApp) -> Unit) {
        fun finish(iconUrl: String?) = onMain {
            val previous = find(manifest.appId)
            val app = WebApp(
                id = manifest.appId,
                name = manifest.name,
                startUrl = manifest.startUrl,
                scope = manifest.scope,
                display = manifest.display,
                iconUrl = iconUrl ?: previous?.iconUrl,
                themeColor = manifest.themeColor,
                backgroundColor = manifest.backgroundColor,
                installedAt = previous?.installedAt ?: clock(),
            )
            val index = apps.indexOfFirst { it.id == app.id }
            if (index >= 0) apps[index] = app else apps += app
            changed()
            done(app)
        }
        val icon = manifest.bestIcon() ?: return finish(null)
        loadIcon(icon.src) { finish(it) }
    }

    fun remove(id: String) {
        if (apps.removeAll { it.id == id }) changed()
    }

    private fun changed() {
        store.save(JSONArray(apps.map { it.toJson() }).toString())
        onChange?.invoke()
    }
}
