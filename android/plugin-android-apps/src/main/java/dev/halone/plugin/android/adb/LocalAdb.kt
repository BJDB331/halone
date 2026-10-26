package dev.halone.plugin.android.adb

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * An ADB client for this phone only: every connection goes to 127.0.0.1,
 * the phone's own Wireless debugging. After The Unbound Launcher's (MIT).
 */
internal class LocalAdb(context: Context) : AbsAdbConnectionManager() {
    private val identity = AdbIdentity.load(context)

    init {
        setApi(android.os.Build.VERSION.SDK_INT)
        setHostAddress(LOOPBACK)
        setTimeout(8, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = identity.key

    override fun getCertificate(): Certificate = identity.certificate

    override fun getDeviceName(): String = "halone@android"

    companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
