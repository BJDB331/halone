package dev.halone.glasses.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import java.net.Inet4Address
import java.net.InetAddress
import javax.net.SocketFactory

/**
 * Finds the network glasses bring up over USB (CDC NCM/ECM), which Android
 * registers as an ethernet link with a 169.254.x.y address.
 *
 * Sockets are created from that [Network]'s factory because a plain socket
 * follows the default route, which is usually Wi-Fi or mobile data.
 */
class LinkLocalNetworks(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun socketFactoryFor(host: String): SocketFactory? = networkFor(host)?.socketFactory

    fun networkFor(host: String): Network? {
        val target = parse(host) ?: return null
        val linkLocal = candidates().filter { (_, addresses) -> addresses.any { it is Inet4Address && it.isLinkLocalAddress } }
        return linkLocal.firstOrNull { (_, addresses) -> addresses.any { sameSlash24(it, target) } }?.first
            ?: linkLocal.firstOrNull()?.first
    }

    fun describe(): String = buildString {
        val all = candidates()
        if (all.isEmpty()) {
            append("no networks with link properties")
            return@buildString
        }
        for ((network, addresses) in all) {
            val iface = connectivity.getLinkProperties(network)?.interfaceName ?: "?"
            appendLine("network $network iface=$iface addresses=${addresses.joinToString { it.hostAddress ?: "?" }}")
        }
    }

    private fun candidates(): List<Pair<Network, List<InetAddress>>> =
        connectivity.allNetworks.mapNotNull { network ->
            val properties: LinkProperties = connectivity.getLinkProperties(network) ?: return@mapNotNull null
            network to properties.linkAddresses.map { it.address }
        }

    private fun parse(host: String): Inet4Address? =
        runCatching { InetAddress.getByName(host) as? Inet4Address }.getOrNull()

    private fun sameSlash24(a: InetAddress, b: Inet4Address): Boolean {
        if (a !is Inet4Address) return false
        val x = a.address
        val y = b.address
        return x[0] == y[0] && x[1] == y[1] && x[2] == y[2]
    }
}
