package dev.halone.app.shell

import android.content.Context
import dev.halone.shell.SettingsStore

/** A JSON file in the app's own storage, written on [writer] so the UI thread never waits on the disk. */
internal class FileStore(context: Context, name: String, private val writer: java.util.concurrent.Executor) : SettingsStore {
    private val file = java.io.File(context.filesDir, name)

    override fun load(): String? = runCatching { file.readText() }.getOrNull()

    override fun save(json: String) = writer.execute {
        val temporary = java.io.File(file.path + ".tmp")
        runCatching {
            temporary.writeText(json)
            temporary.renameTo(file)
        }
    }
}

internal class PreferencesStore(context: Context) : SettingsStore {
    private val preferences = context.getSharedPreferences("halone-shell", Context.MODE_PRIVATE)
    override fun load(): String? = preferences.getString(KEY, null)
    override fun save(json: String) = preferences.edit().putString(KEY, json).apply()

    companion object {
        const val KEY = "settings"
    }
}
