package dev.halone.engine.plugin

import java.io.File
import java.net.URLEncoder

/**
 * Plugin apps' icons as PNGs, kept under [root] so the launcher shows them
 * before the plugin connects and without asking it again. An icon is fetched
 * once per version the plugin gives it. Any thread; fetching blocks on the
 * plugin, so never call it on the main thread.
 */
class PluginIcons(
    private val root: File,
    private val connection: (packageName: String) -> PluginConnection?,
    private val log: (String) -> Unit,
) {
    fun read(packageName: String, appId: String, version: String): ByteArray? {
        val plugin = connection(packageName) ?: return null
        val folder = File(root, plugin.plugin.packageName)
        val prefix = encode(appId) + "@"
        val file = File(folder, prefix + encode(version) + ".png")
        if (file.isFile) return runCatching { file.readBytes() }.getOrNull()
        val provider = plugin.awaitProvider(CONNECT_TIMEOUT_MS) ?: return null
        val png = runCatching { provider.appIcon(appId, SIZE_PX) }
            .onFailure { log("  plugins: icon of $packageName/$appId failed: $it") }
            .getOrNull()
            ?.takeIf { it.size in 1..MAX_BYTES }
            ?: return null
        runCatching {
            folder.mkdirs()
            folder.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
            val partial = File(folder, file.name + ".part")
            partial.writeBytes(png)
            partial.renameTo(file)
        }
        return png
    }

    /** Deletes what is kept for a plugin that is gone or disabled. */
    fun forget(packageName: String) {
        File(root, packageName).takeIf { it.parentFile == root }?.deleteRecursively()
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")

    companion object {
        const val SIZE_PX = 192
        private const val MAX_BYTES = 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 3_000L
    }
}
