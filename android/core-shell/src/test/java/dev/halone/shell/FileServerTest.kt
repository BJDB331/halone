package dev.halone.shell

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileServerTest {
    private val base: File = Files.createTempDirectory("halone-server").toFile().canonicalFile
    private val access = FakeAccess()
    private val files = SharedFiles({ listOf(FileRoot("storage", "Storage", base.path)) }, access)
    private val thumbnailed = mutableListOf<String>()
    private val server = FileServer(files, token = "secret", thumbnailer = { file ->
        thumbnailed += file.name
        if (file.name.startsWith("broken")) null else "thumb:${file.name}".toByteArray()
    }).also { files.urlBase = it.base }

    @After
    fun close() = server.close()

    private fun get(url: String, range: String? = null, method: String = "GET"): Triple<Int, Map<String, String>, ByteArray> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        range?.let { connection.setRequestProperty("Range", it) }
        connection.setRequestProperty("Origin", Shell.ORIGIN)
        val status = connection.responseCode
        val headers = connection.headerFields.filterKeys { it != null }.mapValues { it.value.first() }
        val body = (if (status < 400) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        return Triple(status, headers, body)
    }

    @Test
    fun `serves a file, and a range far into a large one without reading up to it`() {
        val small = File(base, "clip one.mp4").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        val large = File(base, "large.mp4")
        java.io.RandomAccessFile(large, "rw").use { file ->
            file.setLength(3_100_000_000L)
            file.seek(3_000_000_000L)
            file.write("far!".toByteArray())
        }

        val (status, headers, body) = get(files.urlFor(small.path))
        assertEquals(200, status)
        assertEquals("video/mp4", headers["Content-Type"])
        assertEquals(Shell.ORIGIN, headers["Access-Control-Allow-Origin"])
        assertEquals(100, body.size)

        val (farStatus, farHeaders, far) = get(files.urlFor(large.path), "bytes=3000000000-3000000003")
        assertEquals(206, farStatus)
        assertEquals("bytes 3000000000-3000000003/3100000000", farHeaders["Content-Range"])
        assertEquals("far!", String(far))
        large.delete()
    }

    @Test
    fun `nothing is served without the token, outside the roots, or without access`() {
        val file = File(base, "a.jpg").apply { writeText("jpeg") }

        assertEquals(404, get(files.urlFor(file.path).replace("/secret/", "/guess/")).first)
        assertEquals(404, get(server.base + base.parentFile!!.path).first)
        access.granted = false
        assertEquals(404, get(files.urlFor(file.path)).first)
    }

    @Test
    fun `a head request has the headers and no body`() {
        val file = File(base, "a.jpg").apply { writeText("jpeg") }

        val (status, headers, body) = get(files.urlFor(file.path), method = "HEAD")

        assertEquals(200, status)
        assertEquals("4", headers["Content-Length"])
        assertEquals(0, body.size)
    }

    @Test
    fun `pictures and videos have thumbnails, versioned by their modification time`() {
        val photo = File(base, "a.jpg").apply { writeText("jpeg") }
        val clip = File(base, "b.mp4").apply { writeText("mp4") }
        val notes = File(base, "c.txt").apply { writeText("text") }

        val photoUrl = files.stat(photo.path).getString("thumbnailUrl")
        assertEquals(files.urlFor(photo.path) + "?thumbnail&v=" + photo.lastModified(), photoUrl)
        assertTrue(files.stat(notes.path).isNull("thumbnailUrl"))

        val (status, headers, body) = get(photoUrl)
        assertEquals(200, status)
        assertEquals("image/jpeg", headers["Content-Type"])
        assertEquals("thumb:a.jpg", String(body))
        assertEquals(200, get(files.stat(clip.path).getString("thumbnailUrl")).first)
    }

    @Test
    fun `a file without a thumbnail is a 404, and never reaches the thumbnailer if it is not a picture`() {
        File(base, "broken.jpg").writeText("not really")
        val notes = File(base, "c.txt").apply { writeText("text") }

        assertEquals(404, get(files.urlFor(File(base, "broken.jpg").path) + "?thumbnail").first)
        assertEquals(404, get(files.urlFor(notes.path) + "?thumbnail").first)
        assertEquals(listOf("broken.jpg"), thumbnailed)
    }

    @Test
    fun `without a thumbnailer no entry offers one`() {
        val plain = SharedFiles({ listOf(FileRoot("storage", "Storage", base.path)) }, access)
        FileServer(plain).use { plain.urlBase = it.base }
        val photo = File(base, "a.jpg").apply { writeText("jpeg") }

        assertTrue(plain.stat(photo.path).isNull("thumbnailUrl"))
    }
}
