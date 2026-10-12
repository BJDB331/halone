package dev.halone.app

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import dev.halone.glasses.api.GlassesDevice
import dev.halone.glasses.registry.GlassesRegistry
import dev.halone.glasses.registry.ProbeResult
import dev.halone.glasses.transport.UsbHidTransportProvider

/**
 * Ties the vendor-neutral registry to Android's USB stack: find a recognised
 * device, open it once permission exists, hand the driver its transports.
 *
 * Nothing here knows what XREAL is, which is the point of the exercise.
 */
class GlassesConnection(
    private val context: Context,
    private val registry: GlassesRegistry = GlassesRegistry.default(),
) {
    private val usbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private var provider: UsbHidTransportProvider? = null
    var device: GlassesDevice? = null
        private set

    fun attachedDevices(): List<UsbDevice> = usbManager.deviceList.values.toList()

    fun findSupported(): Pair<UsbDevice, ProbeResult>? =
        UsbHidTransportProvider.identities(usbManager)
            .firstNotNullOfOrNull { (usbDevice, identity) ->
                registry.probe(identity)?.let { usbDevice to it }
            }

    fun hasPermission(usbDevice: UsbDevice) = usbManager.hasPermission(usbDevice)

    fun requestPermission(usbDevice: UsbDevice, intent: android.app.PendingIntent) =
        usbManager.requestPermission(usbDevice, intent)

    fun connect(): Result<GlassesDevice> {
        val (usbDevice, probe) = findSupported()
            ?: return Result.failure(IllegalStateException("no supported glasses attached"))

        close()

        val connection = usbManager.openDevice(usbDevice)
            ?: return Result.failure(IllegalStateException("openDevice returned null — permission not granted?"))

        val transports = UsbHidTransportProvider(usbDevice, connection)
        provider = transports

        return registry.connect(probe, transports)
            .onSuccess { device = it }
            .onFailure { close() }
    }

    fun close() {
        device?.let { runCatching { it.close() } }
        device = null
        provider?.let { runCatching { it.close() } }
        provider = null
    }

    fun describeSupported(): String = registry.describeSupported()
}
