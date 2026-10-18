package dev.halone.testkit.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.transport.SocketTransport
import dev.halone.glasses.transport.usbnet.UsbEcmLink
import dev.halone.glasses.transport.usbnet.UsbNetTransportProvider
import dev.halone.testkit.capture.PcapWriter
import dev.halone.testkit.capture.Tap
import dev.halone.testkit.capture.TcpTap
import dev.halone.testkit.engine.DisplayState
import dev.halone.testkit.engine.Io
import dev.halone.testkit.engine.Kit
import dev.halone.testkit.engine.Session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class AndroidKit(
    private val context: Context,
    override val io: Io,
    override val session: Session,
    private val workDir: File,
) : Kit, AutoCloseable {

    private val network = NetworkProbe(context)
    private val usb = context.getSystemService(UsbManager::class.java)
    private var usbNet: UsbNetTransportProvider? = null
    private var pcap: PcapWriter? = null

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

    override fun hasSystemLink(hosts: List<String>) = hosts.any { network.linkFor(it) != null }

    override suspend fun requestUsbAccess(): Boolean {
        val device = ecmDevice() ?: return false
        if (usb.hasPermission(device)) return true
        val answer = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                answer.complete(Unit)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val intent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), PendingIntent.FLAG_MUTABLE,
            )
            usb.requestPermission(device, intent)
            withTimeoutOrNull(PERMISSION_TIMEOUT_MS) { answer.await() }
        } finally {
            context.unregisterReceiver(receiver)
        }
        return usb.hasPermission(device)
    }

    override suspend fun openTcp(
        name: String,
        hosts: List<String>,
        port: Int,
        alwaysRecord: Boolean,
        listener: ((ByteArray, Int) -> Unit)?,
    ): Result<Tap> = withContext(Dispatchers.IO) {
        val transport = if (hasSystemLink(hosts)) {
            session.outcome("link", "system-network")
            systemSocket(hosts, port)
        } else {
            session.outcome("link", "usb-ecm")
            inApp(hosts, port)
        }.getOrElse { return@withContext Result.failure(it) }

        val file = File(workDir, "$name.cap")
        session.attach("captures/$name.cap", file)
        session.note("$name connected via ${transport.name}")
        Result.success(TcpTap(name, transport, session, file, alwaysRecord, listener))
    }

    private fun systemSocket(hosts: List<String>, port: Int): Result<SocketTransport> {
        val failures = mutableListOf<String>()
        for (host in hosts) {
            val link = network.linkFor(host) ?: continue
            var socket: Socket? = null
            try {
                socket = link.socketFactory.createSocket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                }
                return Result.success(SocketTransport("tcp:$host:$port", socket))
            } catch (e: IOException) {
                runCatching { socket?.close() }
                failures += "$host: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return Result.failure(IOException("port $port unreachable (${failures.joinToString("; ")})"))
    }

    private fun inApp(hosts: List<String>, port: Int) = runCatching {
        val provider = usbNet ?: run {
            val device = ecmDevice() ?: throw IOException("no glasses with a USB Ethernet function attached")
            if (!usb.hasPermission(device)) throw IOException("no USB permission for the glasses")
            val connection = usb.openDevice(device) ?: throw IOException("could not open the glasses over USB")
            val capture = PcapWriter(File(workDir, "usbnet.pcap"), skipPort = REPORTS_PORT).also { pcap = it }
            session.attach("captures/usbnet.pcap", File(workDir, "usbnet.pcap"))
            UsbNetTransportProvider(
                device, connection, hosts,
                log = { session.note("usbnet: $it") },
                onFrame = { _, frame, length -> capture.write(frame, length) },
            ).also { usbNet = it }
        }
        provider.open(port).getOrThrow()
    }

    private fun ecmDevice(): UsbDevice? = usb.deviceList.values.firstOrNull { UsbEcmLink.hasEcm(it) }

    override fun close() {
        usbNet?.close()
        usbNet = null
        pcap?.close()
        pcap = null
    }

    private companion object {
        const val ACTION_USB_PERMISSION = "dev.halone.testkit.USB_PERMISSION"
        const val CONNECT_TIMEOUT_MS = 2_000
        const val PERMISSION_TIMEOUT_MS = 60_000L
        const val REPORTS_PORT = 52998
    }
}
