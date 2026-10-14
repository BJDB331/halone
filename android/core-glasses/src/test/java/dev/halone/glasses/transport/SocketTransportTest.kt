package dev.halone.glasses.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.SocketFactory
import kotlin.concurrent.thread

class SocketTransportTest {

    private fun echoServer(): ServerSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).also { server ->
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { client ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val n = client.getInputStream().read(buffer)
                        if (n < 0) break
                        client.getOutputStream().write(buffer, 0, n)
                    }
                }
            }
        }
    }

    private val refusing = object : SocketFactory() {
        override fun createSocket(): Socket = throw IOException("no route")
        override fun createSocket(host: String, port: Int) = createSocket()
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) = createSocket()
        override fun createSocket(host: InetAddress, port: Int) = createSocket()
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) = createSocket()
    }

    @Test
    fun `bytes round trip, a quiet read times out, and a closed peer reads as gone`() {
        echoServer().use { server ->
            SocketTransportProvider(listOf("127.0.0.1")).use { provider ->
                val transport = provider.open(server.localPort).getOrThrow()
                val buffer = ByteArray(16)

                assertEquals(3, transport.write(byteArrayOf(1, 2, 3)))
                val n = transport.read(buffer, 1_000)
                assertArrayEquals(byteArrayOf(1, 2, 3), buffer.copyOf(n))

                assertEquals(0, transport.read(buffer, 50))

                transport.close()
                assertEquals(-1, transport.read(buffer, 50))
            }
        }
    }

    @Test
    fun `an unreachable host is skipped and the one that answered is preferred afterwards`() {
        echoServer().use { first ->
            echoServer().use { second ->
                val asked = mutableListOf<String>()
                val provider = SocketTransportProvider(listOf("dead", "127.0.0.1")) { host ->
                    asked += host
                    if (host == "dead") refusing else SocketFactory.getDefault()
                }
                provider.use {
                    assertTrue(it.open(first.localPort).isSuccess)
                    assertTrue(it.open(second.localPort).isSuccess)
                }
                assertEquals(listOf("dead", "127.0.0.1", "127.0.0.1"), asked)
                assertEquals("127.0.0.1", provider.connectedHost)
            }
        }
    }

    @Test
    fun `failure names every host tried`() {
        val provider = SocketTransportProvider(listOf("a", "b")) { refusing }
        val error = provider.open(1).exceptionOrNull()!!
        assertTrue(error.message!!.contains("a: no route"))
        assertTrue(error.message!!.contains("b: no route"))
    }
}
