package dev.halone.testkit.android

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.view.Display
import dev.halone.display.DisplayProbe
import dev.halone.glasses.api.DeviceDescriptorDump
import dev.halone.glasses.transport.UsbHidTransportProvider
import dev.halone.testkit.engine.DisplayState
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object PhoneProbe {
    fun describe(context: Context): String = JSONObject().apply {
        put("manufacturer", Build.MANUFACTURER)
        put("model", Build.MODEL)
        put("device", Build.DEVICE)
        put("android", Build.VERSION.RELEASE)
        put("sdk", Build.VERSION.SDK_INT)
        put("kernel", System.getProperty("os.version"))
        put("abis", Build.SUPPORTED_ABIS.joinToString())
        put("locale", context.resources.configuration.locales[0].toLanguageTag())
    }.toString(2)
}

/** Descriptors are readable without USB permission; the serial number is not, so it never appears. */
object UsbProbe {
    fun describe(context: Context): String {
        val manager = context.getSystemService(UsbManager::class.java)
        val devices = manager.deviceList.values
        if (devices.isEmpty()) return "no USB devices\n"
        return devices.joinToString("\n") { DeviceDescriptorDump.describe(it) }
    }

    fun identities(context: Context) =
        UsbHidTransportProvider.identities(context.getSystemService(UsbManager::class.java)).map { it.second }
}

object DisplayInfoProbe {
    fun describe(context: Context): String = buildString {
        append(DisplayProbe.report(context))
        val manager = context.getSystemService(DisplayManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (display in manager.displays) {
                val info = display.deviceProductInfo ?: continue
                appendLine("display ${display.displayId} product info:")
                appendLine("  name=${info.name} manufacturer=${info.manufacturerPnpId} product=${info.productId}")
                appendLine("  modelYear=${info.modelYear}")
                appendLine("  connection=${info.connectionToSinkType}")
            }
        }
    }

    fun current(context: Context): DisplayState? {
        val display: Display = DisplayProbe.externalDisplay(context) ?: return null
        val modes = display.supportedModes.map {
            "${it.physicalWidth}x${it.physicalHeight}@${"%.0f".format(it.refreshRate)}"
        }.sorted().joinToString(",")
        return DisplayState(display.name, modes)
    }
}

/**
 * Glasses that network over USB appear as a link with a 169.254.x.y address.
 * Sockets come from that network's factory, because a plain socket follows the
 * default route, usually Wi-Fi or mobile data.
 */
class NetworkProbe(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** The phone's own network to the glasses, if it brought one up. */
    fun linkFor(host: String): Network? {
        val target = runCatching { InetAddress.getByName(host) as? Inet4Address }.getOrNull() ?: return null
        val linkLocal = networks()
            .filter { (_, addresses) -> addresses.any { it is Inet4Address && it.isLinkLocalAddress } }
            .filter { (network, _) -> runCatching { network.socketFactory.createSocket().close() }.isSuccess }
        val match = linkLocal.firstOrNull { (_, addresses) ->
            addresses.any { it is Inet4Address && it.address.take(3) == target.address.take(3) }
        } ?: linkLocal.firstOrNull()
        return match?.first
    }

    fun describe(): String = buildString {
        appendLine("android networks:")
        for ((network, addresses) in networks()) {
            val properties = connectivity.getLinkProperties(network)
            val caps = connectivity.getNetworkCapabilities(network)
            appendLine(
                "  $network iface=${properties?.interfaceName} transports=${transports(caps)} " +
                    "addresses=${addresses.joinToString { it.hostAddress ?: "?" }}",
            )
        }
        appendLine("interfaces:")
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrDefault(emptyList())
        for (iface in interfaces) {
            val addresses = Collections.list(iface.inetAddresses).joinToString { it.hostAddress ?: "?" }
            appendLine("  ${iface.name} up=${runCatching { iface.isUp }.getOrNull()} mtu=${runCatching { iface.mtu }.getOrNull()} addresses=$addresses")
        }
    }

    @Suppress("DEPRECATION")
    private fun networks(): List<Pair<Network, List<InetAddress>>> =
        connectivity.allNetworks.mapNotNull { network ->
            val properties = connectivity.getLinkProperties(network) ?: return@mapNotNull null
            network to properties.linkAddresses.map { it.address }
        }

    private fun transports(caps: NetworkCapabilities?): String {
        caps ?: return "?"
        return listOf(
            NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
            NetworkCapabilities.TRANSPORT_USB to "usb",
            NetworkCapabilities.TRANSPORT_WIFI to "wifi",
            NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
        ).filter { caps.hasTransport(it.first) }.joinToString("+") { it.second }.ifEmpty { "other" }
    }
}
