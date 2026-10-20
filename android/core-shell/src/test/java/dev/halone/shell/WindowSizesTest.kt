package dev.halone.shell

import dev.halone.compositor.Scene
import dev.halone.compositor.TitleBar
import dev.halone.engine.plugin.PluginPackage
import dev.halone.engine.plugin.ProvidedApp
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSizesTest {
    private class MemorySizes : WindowSizeStore {
        val saved = mutableMapOf<String, Pair<Int, Int>>()
        override fun load(key: String) = saved[key]
        override fun save(key: String, widthPx: Int, heightPx: Int) {
            saved[key] = widthPx to heightPx
        }
    }

    private val sizes = MemorySizes()
    private val nativeApps = NativeApps(MemoryStore())
    private val scene = Scene()
    private val shell = Shell(scene, APPS, ShellSettings(MemoryStore(), SCHEMA), FakePlatform(scene), nativeApps = nativeApps)
        .also { it.windowSizes = sizes }

    private fun size(window: ShellWindow) = window.panel.widthPx to window.panel.heightPx

    @Test
    fun `the size button steps through the presets and remembers the choice per app`() {
        val window = shell.launch("settings")
        val first = shell.cycleSize(window.id)

        assertEquals(TitleBar.SIZE_PRESETS[0], first)
        assertEquals(first, size(window))
        assertEquals(first, sizes.saved["settings"])

        val second = shell.cycleSize(window.id)
        assertEquals(TitleBar.SIZE_PRESETS[1], second)
        assertEquals(second, sizes.saved["settings"])
    }

    @Test
    fun `an app reopens at the size it was given`() {
        sizes.saved["files"] = 720 to 1280
        assertEquals(720 to 1280, size(shell.launch("files")))
    }

    @Test
    fun `an app with nothing remembered opens at its own size`() {
        val window = shell.launch("settings")
        assertEquals(1024, window.panel.widthPx)
    }

    @Test
    fun `plugin windows are remembered by title, applied once the title arrives`() {
        val plugin = PluginPackage("dev.example.android", "dev.example.android.Provider", "Android apps", "1.0", 1, "aa")
        nativeApps.update(listOf(plugin))
        nativeApps.setEnabled(plugin.packageName, true)
        nativeApps.setApps(plugin.packageName, listOf(ProvidedApp("apps", "Apps", "", 720, 1280, false, "1")))
        val appId = "dev.example.android/apps"

        sizes.saved["$appId|YouTube"] = 1280 to 720
        val youtube = shell.openNativePanel(appId, null)
        assertEquals("not yet: the plugin has not said which app this is", 720 to 1280, size(youtube))
        shell.setTitle(youtube.id, "YouTube")
        assertEquals(1280 to 720, size(youtube))

        val other = shell.openNativePanel(appId, null)
        shell.setTitle(other.id, "Maps")
        assertEquals("another app shown by the same plugin keeps its own size", 720 to 1280, size(other))
    }
}
