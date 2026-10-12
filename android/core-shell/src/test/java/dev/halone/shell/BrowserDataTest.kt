package dev.halone.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDataTest {
    private var now = 1_000L
    private val clock = { now++ }

    @Test
    fun `a page visited again moves to the top instead of appearing twice`() {
        val history = BrowserHistory(MemoryStore(), clock)
        history.visited("https://a.example/", "A")
        history.visited("https://b.example/", "B")
        history.visited("https://a.example/")

        assertEquals(listOf("https://a.example/", "https://b.example/"), history.list().map { it.url })
        assertEquals("A", history.list().first().title)
    }

    @Test
    fun `history keeps only web pages, and the newest up to its limit`() {
        val history = BrowserHistory(MemoryStore(), clock, limit = 2)
        history.visited("about:blank")
        history.visited("https://a.example/")
        history.visited("https://b.example/")
        history.visited("https://c.example/")

        assertEquals(listOf("https://c.example/", "https://b.example/"), history.list().map { it.url })
    }

    @Test
    fun `history is searched by title and address, every word matching`() {
        val history = BrowserHistory(MemoryStore(), clock)
        history.visited("https://news.example/today", "Morning headlines")
        history.visited("https://shop.example/", "Shoes")

        assertEquals(listOf("https://news.example/today"), history.list("news morning").map { it.url })
        assertEquals(emptyList<String>(), history.list("news shoes").map { it.url })
    }

    @Test
    fun `history survives a restart, titles included`() {
        val store = MemoryStore()
        BrowserHistory(store, clock).apply {
            visited("https://a.example/")
            titled("https://a.example/", "Late title")
        }

        assertEquals("Late title", BrowserHistory(store, clock).list().single().title)
    }

    @Test
    fun `bookmarking a page twice gives back the first bookmark`() {
        val bookmarks = Bookmarks(MemoryStore(), clock)
        val first = bookmarks.add("https://a.example/", "A")
        val again = bookmarks.add("https://a.example/", "Another title")

        assertEquals(first, again)
        assertEquals(1, bookmarks.list().size)
    }

    @Test
    fun `bookmark ids keep counting after a restart`() {
        val store = MemoryStore()
        val first = Bookmarks(store, clock).add("https://a.example/", "A")
        val second = Bookmarks(store, clock).add("https://b.example/", "B")

        assertTrue(first.id != second.id)
    }

    @Test
    fun `a download in progress when the app stopped comes back as interrupted`() {
        val store = MemoryStore()
        Downloads(store, clock).start("https://a.example/f.zip", "f.zip", "application/zip", 100) {}

        val restored = Downloads(store, clock).list().single()
        assertEquals(DownloadState.FAILED, restored.state)
        assertEquals("interrupted", restored.error)
    }

    @Test
    fun `cancelling a download stops it and keeps it on the list`() {
        val downloads = Downloads(MemoryStore(), clock)
        var stopped = false
        val id = downloads.start("https://a.example/f.zip", "f.zip", null, null) { stopped = true }
        downloads.progress(id, 10)
        downloads.cancel(id)
        downloads.progress(id, 20)

        assertTrue(stopped)
        assertEquals(DownloadState.CANCELLED, downloads.find(id).state)
        assertEquals(10, downloads.find(id).receivedBytes)
        assertEquals(0, downloads.active)
    }

    @Test
    fun `clearing the list leaves downloads still going`() {
        val downloads = Downloads(MemoryStore(), clock)
        val done = downloads.start("https://a.example/1", "1", null, 5) {}
        downloads.finish(done, "/sdcard/Download/1")
        val going = downloads.start("https://a.example/2", "2", null, 5) {}
        downloads.clear()

        assertEquals(listOf(going), downloads.list().map { it.id })
    }

    @Test
    fun `a finished download without a stated size takes what it received`() {
        val downloads = Downloads(MemoryStore(), clock)
        val id = downloads.start("https://a.example/f", "f", null, null) {}
        downloads.progress(id, 42)
        downloads.finish(id, "/sdcard/Download/f")

        assertEquals(42L, downloads.find(id).totalBytes)
        assertNull(downloads.find(id).error)
    }

    @Test
    fun `menu size grows with its rows and its longest label`() {
        val short = MenuLayout.size(listOf(MenuItem("a", "Copy")))
        val long = MenuLayout.size(listOf(MenuItem("a", "Open link in a new private tab"), MenuItem.SEPARATOR, MenuItem("b", "Copy")))

        assertEquals(MenuLayout.MIN_WIDTH_PX, short.first)
        assertEquals(2 * MenuLayout.PADDING_PX + MenuLayout.ROW_PX, short.second)
        assertTrue(long.first > short.first)
        assertEquals(2 * MenuLayout.PADDING_PX + 2 * MenuLayout.ROW_PX + MenuLayout.SEPARATOR_PX, long.second)
    }
}
