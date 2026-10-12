package dev.halone.shell

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * Serves [files] over loopback HTTP, with ranges, for `<img>`, `<video>` and
 * `fetch` in system apps.
 *
 * Not `shouldInterceptRequest`: WebView reaches the start of a range in an
 * intercepted stream by reading through everything before it, which for the
 * index at the end of a 3 GB video takes half a minute and floods memory. A
 * real server seeks. Every path starts with a per-session [token], so other
 * apps and Gecko pages, which can reach loopback too, cannot read files.
 */
class FileServer(
    private val files: SharedFiles,
    private val allowedOrigin: String = Shell.ORIGIN,
    private val token: String = newToken(),
    /** Makes a small JPEG of a picture or a video, or null when it cannot; on a server thread. */
    private val thumbnailer: ((File) -> ByteArray?)? = null,
    private val log: (String) -> Unit = {},
) : Closeable {
    /** Decoding a photo takes tens of megabytes; a grid of them at once would not fit. */
    private val thumbnailing = Semaphore(MAX_THUMBNAILS_AT_ONCE)

    init {
        files.thumbnails = thumbnailer != null
    }
    private val server = ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool { runnable -> Thread(runnable, "halone-files").apply { isDaemon = true } }

    /** Where file URLs start; [SharedFiles.urlBase] is set to this. */
    val base: String = "http://127.0.0.1:${server.localPort}/$token"

    init {
        Thread({
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    return@Thread
                }
                workers.execute { socket.use { serve(it) } }
            }
        }, "halone-files-accept").apply { isDaemon = true }.start()
    }

    override fun close() {
        server.close()
        workers.shutdownNow()
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0) ?: return
        val request = parts.getOrNull(1) ?: return
        val target = request.substringBefore('?')
        val query = request.substringAfter('?', "").split('&').map { it.substringBefore('=') }.toSet()
        val out = socket.getOutputStream()
        if ("thumbnail" !in query) log("  files: $method ${target.substringAfter("/$token", "(no token)")} ${headers["range"].orEmpty()}")
        try {
            when {
                method == "OPTIONS" -> write(out, 204, "No Content", preflightHeaders(headers))
                method != "GET" && method != "HEAD" -> write(out, 405, "Method Not Allowed", emptyMap())
                !target.startsWith("/$token/") -> write(out, 404, "Not Found", emptyMap())
                "thumbnail" in query -> serveThumbnail(out, URLDecoder.decode(target.removePrefix("/$token"), "UTF-8"), method == "HEAD")
                else -> serveFile(out, URLDecoder.decode(target.removePrefix("/$token"), "UTF-8"), headers["range"], method == "HEAD")
            }
        } catch (_: IOException) {
            // The page let go of the response, as a media element does when it seeks.
        }
    }

    private fun serveFile(out: OutputStream, path: String, range: String?, headOnly: Boolean) {
        val file = runCatching { files.resolve(path) }.getOrNull()?.takeIf { it.isFile }
            ?: return write(out, 404, "Not Found", corsHeaders())
        val response = SharedFiles.respond(file, range)
        val headers = corsHeaders() + response.headers + ("Content-Type" to response.mimeType)
        write(out, response.status, response.reason, headers)
        response.body?.use { body -> if (!headOnly) body.copyTo(out, BUFFER_BYTES) }
        out.flush()
    }

    private fun serveThumbnail(out: OutputStream, path: String, headOnly: Boolean) {
        val file = runCatching { files.resolve(path) }.getOrNull()?.takeIf { it.isFile && SharedFiles.hasThumbnail(it.name) }
        val make = thumbnailer
        val bytes = if (file == null || make == null) null else {
            thumbnailing.acquire()
            try {
                runCatching { make(file) }.onFailure { log("  files: no thumbnail for ${file.name}: $it") }.getOrNull()
            } finally {
                thumbnailing.release()
            }
        }
        if (bytes == null) return write(out, 404, "Not Found", corsHeaders())
        val headers = corsHeaders() + mapOf(
            "Content-Type" to "image/jpeg",
            "Content-Length" to bytes.size.toString(),
            "Cache-Control" to "private, max-age=31536000, immutable",
        )
        write(out, 200, "OK", headers)
        if (!headOnly) out.write(bytes)
        out.flush()
    }

    private fun corsHeaders() = mapOf(
        "Access-Control-Allow-Origin" to allowedOrigin,
        "Access-Control-Expose-Headers" to "Content-Range, Content-Length, Accept-Ranges",
        "Cache-Control" to "no-store",
    )

    private fun preflightHeaders(request: Map<String, String>) = corsHeaders() + buildMap {
        put("Access-Control-Allow-Methods", "GET, HEAD")
        put("Access-Control-Allow-Headers", request["access-control-request-headers"] ?: "Range")
        if (request["access-control-request-private-network"] == "true") put("Access-Control-Allow-Private-Network", "true")
    }

    private fun write(out: OutputStream, status: Int, reason: String, headers: Map<String, String>) {
        val head = StringBuilder("HTTP/1.1 $status $reason\r\n")
        headers.forEach { (name, value) -> head.append(name).append(": ").append(value).append("\r\n") }
        if (headers.keys.none { it.equals("Content-Length", ignoreCase = true) }) head.append("Content-Length: 0\r\n")
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (line.isEmpty()) null else line.toString()
            if (byte == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length >= MAX_LINE) return null
            line.append(byte.toChar())
        }
    }

    companion object {
        private const val BACKLOG = 32
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 64 * 1024
        private const val MAX_LINE = 8 * 1024
        private const val MAX_THUMBNAILS_AT_ONCE = 2

        private fun newToken(): String {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
