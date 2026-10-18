package dev.halone.glasses.transport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import dev.halone.glasses.api.UsbIdentity

/**
 * A claimed USB HID interface, exposed as raw bytes.
 *
 * `claimInterface(force = true)` detaches the kernel HID driver, which is what
 * makes this equivalent to writing hidraw on Linux — no report IDs are
 * involved, so protocol bytes go on the wire exactly as the reference drivers
 * send them.
 */
class UsbHidTransport internal constructor(
    override val name: String,
    private val connection: UsbDeviceConnection,
    internal val iface: UsbInterface,
    private val inEndpoint: UsbEndpoint,
    /**
     * Null for an interface with only an IN endpoint. HID lets those take
     * output reports over the control pipe instead, as SET_REPORT requests,
     * which is what hidapi does in the same situation.
     */
    private val outEndpoint: UsbEndpoint?,
) : HidTransport {

    override fun write(packet: ByteArray, timeoutMs: Int): Int =
        if (outEndpoint != null) {
            connection.bulkTransfer(outEndpoint, packet, packet.size, timeoutMs)
        } else {
            connection.controlTransfer(
                SET_REPORT_REQUEST_TYPE,
                SET_REPORT,
                (OUTPUT_REPORT shl 8) or NO_REPORT_ID,
                iface.id,
                packet,
                packet.size,
                timeoutMs,
            )
        }

    override fun read(into: ByteArray, timeoutMs: Int): Int =
        connection.bulkTransfer(inEndpoint, into, into.size, timeoutMs)

    override fun close() {
        runCatching { connection.releaseInterface(iface) }
    }

    private companion object {
        /** Host to device, class request, to the interface. */
        const val SET_REPORT_REQUEST_TYPE = 0x21
        const val SET_REPORT = 0x09
        const val OUTPUT_REPORT = 0x02
        const val NO_REPORT_ID = 0x00
    }
}

/**
 * Claims interfaces on one USB device and hands them out as transports.
 *
 * Owns the [UsbDeviceConnection] shared by every interface on the device, so
 * [close] must outlive all transports it produced.
 */
class UsbHidTransportProvider(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
) : HidTransportProvider, AutoCloseable {

    private val opened = mutableListOf<UsbHidTransport>()

    override fun open(interfaceId: Int): Result<HidTransport> {
        val iface = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { it.id == interfaceId }
            ?: return Result.failure(IllegalArgumentException("no interface with id $interfaceId"))

        if (!connection.claimInterface(iface, true)) {
            return Result.failure(IllegalStateException("claimInterface($interfaceId) refused"))
        }

        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) inEp = ep
            if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) outEp = ep
        }

        // An IN-only HID interface still takes output reports, over the
        // control pipe; anything else without an OUT endpoint is not ours.
        val controlWrites = outEp == null && iface.interfaceClass == UsbConstants.USB_CLASS_HID
        if (inEp == null || (outEp == null && !controlWrites)) {
            connection.releaseInterface(iface)
            return Result.failure(
                IllegalStateException("interface $interfaceId lacks a bidirectional endpoint pair")
            )
        }

        // The name carries the descriptor, so a bring-up failure that quotes
        // it says what the interface actually is.
        val name = "usb:iface$interfaceId class=%d/%d/%d in=%s out=%s".format(
            iface.interfaceClass, iface.interfaceSubclass, iface.interfaceProtocol,
            describe(inEp), outEp?.let(::describe) ?: "SET_REPORT",
        )
        val transport = UsbHidTransport(name, connection, iface, inEp, outEp)
        opened += transport
        return Result.success(transport)
    }

    private fun describe(ep: UsbEndpoint): String {
        val type = when (ep.type) {
            UsbConstants.USB_ENDPOINT_XFER_INT -> "int"
            UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
            else -> "type${ep.type}"
        }
        return "0x%02x/%s/%d".format(ep.address, type, ep.maxPacketSize)
    }

    override fun hidInterfaceIds(): List<Int> =
        (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .filter { it.interfaceClass == UsbConstants.USB_CLASS_HID }
            .map { it.id }
            .distinct()

    override fun close() {
        opened.forEach { runCatching { it.close() } }
        opened.clear()
        runCatching { connection.close() }
    }

    companion object {
        fun identityOf(device: UsbDevice) = UsbIdentity(
            vendorId = device.vendorId,
            productId = device.productId,
            manufacturerName = runCatching { device.manufacturerName }.getOrNull(),
            productName = runCatching { device.productName }.getOrNull(),
            busAddress = device.deviceName,
        )

        fun identities(manager: UsbManager): List<Pair<UsbDevice, UsbIdentity>> =
            manager.deviceList.values.map { it to identityOf(it) }
    }
}
