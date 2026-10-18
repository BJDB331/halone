package dev.halone.glasses.transport.usbnet

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import dev.halone.glasses.transport.HidTransport
import dev.halone.glasses.transport.HidTransportProvider
import java.io.IOException

/**
 * A CDC ECM function driven from user space: one Ethernet frame per bulk
 * transfer. Claiming detaches whatever kernel driver had the interface, which
 * on the phones this exists for was doing nothing useful anyway.
 */
class UsbEcmLink private constructor(
    private val connection: UsbDeviceConnection,
    private val control: UsbInterface,
    private val data: UsbInterface,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint,
) : EthernetLink {

    @Volatile
    private var closed = false
    private var quickFailures = 0

    override fun send(frame: ByteArray): Boolean {
        if (closed) return false
        val sent = connection.bulkTransfer(outEndpoint, frame, frame.size, WRITE_TIMEOUT_MS) == frame.size
        if (sent && frame.size % outEndpoint.maxPacketSize == 0) {
            connection.bulkTransfer(outEndpoint, ByteArray(0), 0, WRITE_TIMEOUT_MS)
        }
        return sent
    }

    /**
     * `bulkTransfer` answers -1 for both a timeout and a vanished device; only
     * the time it took tells them apart.
     */
    override fun receive(into: ByteArray, timeoutMs: Int): Int {
        if (closed) return -1
        val started = System.nanoTime()
        val n = connection.bulkTransfer(inEndpoint, into, into.size, timeoutMs)
        if (n >= 0) {
            quickFailures = 0
            return n
        }
        val tookMs = (System.nanoTime() - started) / 1_000_000
        if (tookMs < timeoutMs / 2) {
            if (++quickFailures >= MAX_QUICK_FAILURES) return -1
        } else {
            quickFailures = 0
        }
        return 0
    }

    override fun close() {
        closed = true
        runCatching { connection.releaseInterface(data) }
        runCatching { connection.releaseInterface(control) }
    }

    companion object {
        private const val CLASS_CDC_DATA = 0x0A
        private const val SUBCLASS_ECM = 0x06
        private const val SET_ETHERNET_PACKET_FILTER = 0x43
        private const val FILTER_DIRECTED_BROADCAST_MULTICAST = 0x0E
        private const val WRITE_TIMEOUT_MS = 200
        private const val MAX_QUICK_FAILURES = 50

        fun hasEcm(device: UsbDevice): Boolean = find(device) != null

        fun open(device: UsbDevice, connection: UsbDeviceConnection): Result<UsbEcmLink> {
            val (control, data) = find(device) ?: return Result.failure(IOException("no CDC ECM function"))
            if (!connection.claimInterface(control, true) || !connection.claimInterface(data, true)) {
                return Result.failure(IOException("could not claim the ECM interfaces"))
            }
            if (!connection.setInterface(data)) {
                return Result.failure(IOException("could not select the ECM data alternate setting"))
            }
            connection.controlTransfer(
                UsbConstants.USB_TYPE_CLASS or 0x01, SET_ETHERNET_PACKET_FILTER,
                FILTER_DIRECTED_BROADCAST_MULTICAST, control.id, null, 0, WRITE_TIMEOUT_MS,
            )
            val endpoints = (0 until data.endpointCount).map { data.getEndpoint(it) }
            val inEp = endpoints.first { it.direction == UsbConstants.USB_DIR_IN }
            val outEp = endpoints.first { it.direction == UsbConstants.USB_DIR_OUT }
            return Result.success(UsbEcmLink(connection, control, data, inEp, outEp))
        }

        /** The ECM control interface and the alternate setting of its data interface that has endpoints. */
        private fun find(device: UsbDevice): Pair<UsbInterface, UsbInterface>? {
            val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
            val control = interfaces.firstOrNull {
                it.interfaceClass == UsbConstants.USB_CLASS_COMM && it.interfaceSubclass == SUBCLASS_ECM
            } ?: return null
            val data = interfaces.firstOrNull {
                it.id == control.id + 1 && it.interfaceClass == CLASS_CDC_DATA && it.endpointCount == 2 &&
                    (0 until it.endpointCount).all { i -> it.getEndpoint(i).type == UsbConstants.USB_ENDPOINT_XFER_BULK }
            } ?: return null
            return control to data
        }
    }
}

/**
 * Opens TCP ports on a device over [UsbEcmLink] and [NetStack], for when the
 * phone never brings the device's USB network up itself. Needs USB permission,
 * unlike the system-network path.
 */
class UsbNetTransportProvider(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val hosts: List<String>,
    private val log: (String) -> Unit = {},
    private val onFrame: ((Boolean, ByteArray, Int) -> Unit)? = null,
) : HidTransportProvider, AutoCloseable {

    private var stack: NetStack? = null

    @Volatile
    var connectedHost: String? = null
        private set

    @Synchronized
    fun stack(): Result<NetStack> {
        stack?.let { return Result.success(it) }
        val link = UsbEcmLink.open(device, connection).getOrElse { return Result.failure(it) }
        val created = NetStack(link, log, onFrame).apply {
            start()
            configure(fallbackIp = fallbackFor(hosts))
        }
        stack = created
        return Result.success(created)
    }

    override fun open(interfaceId: Int): Result<HidTransport> {
        val net = stack().getOrElse { return Result.failure(it) }
        val failures = mutableListOf<String>()
        for (ip in candidates(net)) {
            net.connect(ip, interfaceId)
                .onSuccess {
                    connectedHost = Packets.ipToString(ip)
                    return Result.success(it)
                }
                .onFailure { failures += it.message ?: "?" }
        }
        return Result.failure(IOException("port $interfaceId unreachable over USB (${failures.joinToString("; ")})"))
    }

    private fun candidates(net: NetStack): List<Int> {
        val subnet = net.localIp ushr 8
        val known = hosts.map { Packets.parseIpv4(it) }
        return (listOfNotNull(connectedHost?.let { Packets.parseIpv4(it) }, net.deviceIp) +
            known.sortedBy { if (it ushr 8 == subnet) 0 else 1 }).distinct()
    }

    override fun close() {
        stack?.close()
        stack = null
        runCatching { connection.close() }
    }

    companion object {
        /** `.10` in the subnet of the first host, which is what the devices' own DHCP servers hand out. */
        fun fallbackFor(hosts: List<String>): Int {
            val first = hosts.firstOrNull()?.let { Packets.parseIpv4(it) } ?: Packets.parseIpv4("169.254.1.1")
            return (first and 0xFFFFFF00.toInt()) or 10
        }
    }
}
