package dev.halone.shell

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeAccess(override var granted: Boolean = true) : StorageAccess {
    var requested = 0
    override fun request() {
        requested++
    }
}

class SharedFilesTest {
    private val base: File = Files.createTempDirectory("halone-files").toFile().canonicalFile
    private val pictures = File(base, "Pictures").apply { mkdirs() }
    private val access = FakeAccess()
    private val files = SharedFiles({ listOf(FileRoot("storage", "Storage", base.path), FileRoot("pictures", "Pictures", pictures.path), FileRoot("movies", "Movies", File(base, "Movies").path)) }, access)

    init {
        File(pictures, "a.jpg").writeBytes(ByteArray(100) { it.toByte() })
        File(pictures, ".hidden").writeText("x")
        File(base, "notes.txt").writeText("hello")
    }

    private inline fun expectCode(code: String, block: () -> Unit) {
        try {
            block()
            fail("expected $code")
        } catch (error: HaloneException) {
            assertEquals(code, error.code)
        }
    }

    @Test
    fun `lists only roots that exist`() {
        assertEquals(listOf("storage", "pictures"), files.roots().map { it.kind })
    }

    @Test
    fun `lists a folder without hidden files, typed and with urls`() {
        val entries = files.list(pictures.path)

        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("a.jpg", entry.getString("name"))
        assertEquals("image/jpeg", entry.getString("mimeType"))
        assertEquals(100L, entry.getLong("sizeBytes"))
        assertTrue(entry.getString("url").startsWith(files.urlBase + "/"))
    }

    @Test
    fun `a directory has no url or type`() {
        val entry = files.stat(pictures.path)

        assertEquals("directory", entry.getString("kind"))
        assertTrue(entry.isNull("url"))
        assertTrue(entry.isNull("mimeType"))
    }

    @Test
    fun `paths outside every root are refused`() {
        expectCode("no-such-file") { files.stat(base.parentFile!!.path) }
        expectCode("no-such-file") { files.stat("${pictures.path}/../../..") }
        expectCode("invalid-params") { files.stat("relative/path") }
    }

    @Test
    fun `nothing is readable without access`() {
        access.granted = false

        expectCode("permission-denied") { files.roots() }
        expectCode("permission-denied") { files.list(pictures.path) }
        expectCode("permission-denied") { files.resolve(File(pictures, "a.jpg").path) }
    }

    @Test
    fun `access changes are reported once`() {
        assertFalse(files.accessChanged())
        access.granted = false
        assertTrue(files.accessChanged())
        assertFalse(files.accessChanged())
    }

    @Test
    fun `a file is answered whole or in a range`() {
        val file = File(pictures, "a.jpg")

        val whole = SharedFiles.respond(file, null)
        val part = SharedFiles.respond(file, "bytes=10-19")

        assertEquals(200, whole.status)
        assertEquals(100, whole.body!!.readBytes().size)
        assertEquals(206, part.status)
        assertEquals("bytes 10-19/100", part.headers["Content-Range"])
        assertEquals((10..19).map { it.toByte() }, part.body!!.readBytes().toList())
        assertEquals(416, SharedFiles.respond(file, "bytes=100-").status)
    }

    @Test
    fun `ranges follow the HTTP rules a media element relies on`() {
        assertEquals(0L to 99L, Ranges.parse("bytes=0-", 100))
        assertEquals(90L to 99L, Ranges.parse("bytes=90-200", 100))
        assertEquals(90L to 99L, Ranges.parse("bytes=-10", 100))
        assertEquals(Ranges.UNSATISFIABLE, Ranges.parse("bytes=100-", 100))
        assertNull(Ranges.parse("items=0-1", 100))
    }
}
