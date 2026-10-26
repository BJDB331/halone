package dev.halone.plugin.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import dev.halone.plugin.android.privileged.ShellMain

/**
 * Where the shell helper hands Halone its Binder: only the shell user may
 * publish, and the Binder stays in this process, which is the one that uses
 * it. After The Unbound Launcher's local bridge provider (MIT).
 */
class HelperBridge : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "publish") throw IllegalArgumentException("unknown bridge operation")
        if (Binder.getCallingUid() != ShellMain.SHELL_UID) throw SecurityException("only the ADB shell helper may publish")
        val published = extras?.getBinder("binder") ?: throw IllegalArgumentException("missing binder")
        published.linkToDeath({ main.post { if (binder === published) set(null) } }, 0)
        main.post { set(published) }
        return Bundle().apply { putBoolean("published", true) }
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0

    companion object {
        private val main = Handler(Looper.getMainLooper())
        private val listeners = LinkedHashSet<() -> Unit>()

        /** The live helper, or null. Main thread. */
        var binder: IBinder? = null
            private set

        fun authority(packageName: String) = "$packageName.androidapps.helper"

        private fun set(value: IBinder?) {
            binder = value
            listeners.toList().forEach { it() }
        }

        fun addListener(listener: () -> Unit) {
            listeners += listener
        }

        fun removeListener(listener: () -> Unit) {
            listeners -= listener
        }

        fun alive(): Boolean = binder?.let { it.isBinderAlive && it.pingBinder() } == true
    }
}
