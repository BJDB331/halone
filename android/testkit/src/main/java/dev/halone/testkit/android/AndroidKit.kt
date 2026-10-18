package dev.halone.testkit.android

import android.content.Context
import dev.halone.glasses.api.UsbIdentity
import dev.halone.testkit.capture.Tap
import dev.halone.testkit.capture.TcpTap
import dev.halone.testkit.engine.DisplayState
import dev.halone.testkit.engine.Io
import dev.halone.testkit.engine.Kit
import dev.halone.testkit.engine.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class AndroidKit(
    private val context: Context,
    override val io: Io,
    override val session: Session,
    private val workDir: File,
) : Kit {

    private val network = NetworkProbe(context)

    override fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    override fun usbDevices(): List<UsbIdentity> = UsbProbe.identities(context)

    override fun snapshot(label: String) {
        val at = (session.now() - session.startedAtNs) / 1_000_000
        session.mark("snapshot:$label")
        session.attach("recon/$label/phone.json", PhoneProbe.describe(context))
        session.attach("recon/$label/usb.txt", "t+${at}ms\n" + UsbProbe.describe(context))
        session.attach("recon/$label/network.txt", "t+${at}ms\n" + network.describe())
        session.attach("recon/$label/displays.txt", "t+${at}ms\n" + DisplayInfoProbe.describe(context))
    }

    override fun externalDisplay(): DisplayState? = DisplayInfoProbe.current(context)

    override suspend fun openTcp(
        name: String,
        hosts: List<String>,
        port: Int,
        alwaysRecord: Boolean,
        listener: ((ByteArray, Int) -> Unit)?,
    ): Result<Tap> = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        for (host in hosts) {
            var socket: Socket? = null
            try {
                socket = network.socketFactoryFor(host).createSocket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                }
            } catch (e: IOException) {
                runCatching { socket?.close() }
                failures += "$host: ${e.message ?: e.javaClass.simpleName}"
                continue
            }
            val file = File(workDir, "$name.cap")
            session.attach("captures/$name.cap", file)
            session.note("$name connected to $host:$port")
            return@withContext Result.success(TcpTap(name, socket, session, file, alwaysRecord, listener))
        }
        Result.failure(IOException("port $port unreachable (${failures.joinToString("; ")})"))
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 2_000
    }
}
