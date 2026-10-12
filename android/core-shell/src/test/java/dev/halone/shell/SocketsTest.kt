package dev.halone.shell

import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketsTest {
    private val server = ServerSocket(0)
    private val sockets = Sockets()
    private val events = CopyOnWriteArrayList<Pair<String, JSONObject>>()

    init {
        sockets.onEvent = { _, name, data -> events += name to data }
    }

    @After
    fun tearDown() {
        sockets.closeAll()
        server.close()
    }

    private fun connect(owner: String = "w1"): Int {
        var id = 0
        val latch = CountDownLatch(1)
        sockets.connect(owner, "127.0.0.1", server.localPort, 2_000, tls = false) {
            id = it.getOrThrow()
            latch.countDown()
        }
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        return id
    }

    private fun waitFor(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < until) Thread.sleep(10)
        assertTrue("timed out", condition())
    }

    private fun received() = events.filter { it.first == "socket.data" }.joinToString("") {
        String(Base64.getDecoder().decode(it.second.getString("data")))
    }

    private fun base64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    @Test
    fun `bytes written arrive at the server, and the server's reply comes back as data events`() {
        val accepted = thread {
            server.accept().use { peer ->
                val line = peer.getInputStream().bufferedReader().readLine()
                peer.getOutputStream().write("echo:$line\n".toByteArray())
                peer.getOutputStream().flush()
                Thread.sleep(200)
            }
        }
        val id = connect()
        val written = CountDownLatch(1)
        sockets.write("w1", id, base64("hello\n")) { it.getOrThrow(); written.countDown() }
        assertTrue(written.await(2, TimeUnit.SECONDS))
        waitFor { received() == "echo:hello\n" }
        waitFor { events.any { it.first == "socket.closed" } }
        assertEquals(JSONObject.NULL, events.last { it.first == "socket.closed" }.second.get("error"))
        accepted.join()
    }

    @Test
    fun `a socket stops reading once the window of unacknowledged events is full`() {
        val chunk = ByteArray(1000) { 'x'.code.toByte() }
        thread {
            server.accept().use { peer ->
                runCatching {
                    repeat(Sockets.WINDOW * 4) {
                        peer.getOutputStream().write(chunk)
                        peer.getOutputStream().flush()
                        Thread.sleep(20)
                    }
                    Thread.sleep(2_000)
                }
            }
        }
        val id = connect()
        waitFor { events.count { it.first == "socket.data" } == Sockets.WINDOW }
        Thread.sleep(300)
        assertEquals(Sockets.WINDOW, events.count { it.first == "socket.data" })
        repeat(3) { sockets.ack("w1", id) }
        waitFor { events.count { it.first == "socket.data" } >= Sockets.WINDOW + 1 }
    }

    @Test
    fun `sockets belong to their window, and close when it does`() {
        thread { runCatching { server.accept().use { Thread.sleep(2_000) } } }
        val id = connect("w1")
        val error = runCatching { sockets.close("w2", id) }.exceptionOrNull() as HaloneException
        assertEquals("no-such-socket", error.code)

        sockets.windowsChanged(setOf("w2"))
        assertEquals(0, sockets.openCount("w1"))
    }

    @Test
    fun `a refused connection says where it could not connect`() {
        val port = ServerSocket(0).use { it.localPort }
        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        sockets.connect("w1", "127.0.0.1", port, 2_000, tls = false) {
            failure = it.exceptionOrNull()
            latch.countDown()
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals("connect-failed", (failure as HaloneException).code)
        assertTrue(failure!!.message!!.contains("127.0.0.1:$port"))
    }
}
