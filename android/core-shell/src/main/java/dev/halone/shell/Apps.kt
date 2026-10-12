package dev.halone.shell

import org.json.JSONArray
import org.json.JSONObject

/** Where an app comes from: built into the shell, a site the wearer installed, a Halone package, or a plugin APK. */
enum class AppKind(val wire: String) { SYSTEM("system"), WEB("web"), PACKAGE("package"), NATIVE("native") }

/** One entry of `apps.json`, the registry the system apps build ships. */
data class AppInfo(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val entry: String,
    val widthPx: Int,
    val heightPx: Int,
    val hidden: Boolean = false,
    /** MIME type patterns the app opens. */
    val opens: List<String> = emptyList(),
    val kind: AppKind = AppKind.SYSTEM,
    /** An image for the app, where [icon] is not enough: an installed site's own. */
    val iconUrl: String? = null,
    /** A web app's pages: addresses under it stay in its window. */
    val scope: String? = null,
    /** A package's granted permissions; null for apps that need none granted. */
    val permissions: List<String>? = null,
    /** A package loaded from a developer's server. */
    val dev: Boolean = false,
    /** The plugin's Android package, for a native app. */
    val provider: String? = null,
    /** A CSS colour for the app's tile, chosen by the app; null leaves it to the launcher. */
    val color: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("description", description)
        .put("icon", icon)
        .put("entry", entry)
        .put("widthPx", widthPx)
        .put("heightPx", heightPx)
        .put("hidden", hidden)
        .put("opens", JSONArray(opens))
        .put("kind", kind.wire)
        .put("iconUrl", iconUrl ?: JSONObject.NULL)
        .put("scope", scope ?: JSONObject.NULL)
        .put("permissions", permissions?.let { JSONArray(it) } ?: JSONObject.NULL)
        .put("dev", dev)
        .put("provider", provider ?: JSONObject.NULL)
        .put("color", color ?: JSONObject.NULL)

    companion object {
        fun parseRegistry(json: String): List<AppInfo> {
            val array = JSONArray(json)
            return (0 until array.length()).map { index ->
                val app = array.getJSONObject(index)
                AppInfo(
                    id = app.getString("id"),
                    name = app.getString("name"),
                    description = app.optString("description"),
                    icon = app.optString("icon"),
                    entry = app.getString("entry"),
                    widthPx = app.getInt("widthPx"),
                    heightPx = app.getInt("heightPx"),
                    hidden = app.optBoolean("hidden", false),
                    opens = app.optJSONArray("opens")?.let { list -> (0 until list.length()).map { list.getString(it) } }.orEmpty(),
                    iconUrl = app.optString("iconUrl").takeIf { it.isNotEmpty() },
                    color = app.optString("color").takeIf { it.isNotEmpty() },
                )
            }
        }
    }
}
