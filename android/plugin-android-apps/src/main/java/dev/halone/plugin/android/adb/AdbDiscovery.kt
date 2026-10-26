package dev.halone.plugin.android.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Finds the ports Wireless debugging is listening on, pairing and connect,
 * by the mDNS services Android advertises for them. Only this phone's own
 * services are kept; connections still go to loopback. After The Unbound
 * Launcher's (MIT).
 */
internal class AdbDiscovery(context: Context) : AutoCloseable {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val pairing = LinkedHashMap<String, Int>()
    private val connect = LinkedHashMap<String, Int>()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var closed = false

    init {
        discover(PAIRING_TYPE)
        discover(CONNECT_TYPE)
    }

    @Synchronized
    fun ports(pair: Boolean): List<Int> = (if (pair) pairing else connect).values.toList()

    private fun discover(type: String) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(this@AdbDiscovery) {
                    pairing.remove(info.serviceName)
                    connect.remove(info.serviceName)
                }
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(this@AdbDiscovery) {
                    if (closed || queue.size >= MAX_QUEUED) return
                    queue.addLast(info)
                    resolveNext()
                }
            }
        }
        runCatching {
            manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            listeners += listener
        }
    }

    @Suppress("DEPRECATION")
    @Synchronized
    private fun resolveNext() {
        if (closed || resolving || queue.isEmpty()) return
        val next = queue.removeFirst()
        resolving = true
        try {
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = done(null)
                override fun onServiceResolved(info: NsdServiceInfo) = done(info)
                private fun done(info: NsdServiceInfo?) {
                    synchronized(this@AdbDiscovery) {
                        resolving = false
                        if (!closed && info != null && local(info.host) && info.port in 1..65535) {
                            (if (info.serviceType.contains("pairing")) pairing else connect)[info.serviceName] = info.port
                        }
                        resolveNext()
                    }
                }
            })
        } catch (e: RuntimeException) {
            resolving = false
            resolveNext()
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        queue.clear()
        pairing.clear()
        connect.clear()
        listeners.forEach { runCatching { manager.stopServiceDiscovery(it) } }
        listeners.clear()
    }

    private companion object {
        const val PAIRING_TYPE = "_adb-tls-pairing._tcp."
        const val CONNECT_TYPE = "_adb-tls-connect._tcp."
        const val MAX_QUEUED = 16

        fun local(address: InetAddress?): Boolean {
            if (address == null) return false
            if (address.isLoopbackAddress) return true
            return runCatching { NetworkInterface.getByInetAddress(address) != null }.getOrDefault(false)
        }
    }
}
