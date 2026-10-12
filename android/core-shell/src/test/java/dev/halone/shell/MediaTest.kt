package dev.halone.shell

import dev.halone.compositor.MediaProjection
import dev.halone.compositor.StereoLayout
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeBackend : MediaBackend {
    val calls = mutableListOf<String>()
    var report: (Playback) -> Unit = {}

    override fun open(file: File, layout: MediaLayout, startSeconds: Double, onPlayback: (Playback) -> Unit) {
        calls += "open ${file.name} ${layout.projection.wire} $startSeconds"
        report = onPlayback
    }

    override fun play() { calls += "play" }
    override fun pause() { calls += "pause" }
    override fun seek(seconds: Double) { calls += "seek $seconds" }
    override fun setLayout(layout: MediaLayout) { calls += "layout ${layout.stereo.wire}" }
    override fun setRotatable(rotatable: Boolean) { calls += "rotatable $rotatable" }
    override fun recenter() { calls += "recenter" }
    override fun close() { calls += "close" }
}

class MediaTest {
    private val backend = FakeBackend()
    private val media = Media(backend)
    private val told = mutableListOf<Pair<String, JSONObject>>()
    private val sphere = MediaLayout(MediaProjection.EQUIRECT_360, StereoLayout.MONO)

    init {
        media.onChange = { owner, state -> told += owner to state }
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
    fun `only the owner drives the player`() {
        media.open("w1", "/a.mp4", File("/a.mp4"), sphere, 12.0)

        expectCode("not-owner") { media.pause("w2") }
        media.pause("w1")
        assertEquals(listOf("open a.mp4 equirect-360 12.0", "pause"), backend.calls)
    }

    @Test
    fun `playback reports reach the owner`() {
        media.open("w1", "/a.mp4", File("/a.mp4"), sphere, 0.0)
        backend.report(Playback(playing = true, positionSeconds = 3.0, durationSeconds = 10.0, videoWidthPx = 3840, videoHeightPx = 1920))

        val (owner, state) = told.last()
        assertEquals("w1", owner)
        assertEquals(3.0, state.getDouble("positionSeconds"), 0.0)
        assertEquals(3840, state.getInt("videoWidthPx"))
        assertEquals("equirect-360", state.getString("projection"))
    }

    @Test
    fun `a second window takes the player over and the first is told`() {
        media.open("w1", "/a.mp4", File("/a.mp4"), sphere, 0.0)
        val stale = backend.report
        media.open("w2", "/b.mp4", File("/b.mp4"), sphere, 0.0)

        assertTrue(told.any { (owner, state) -> owner == "w1" && !state.getBoolean("open") })
        val before = told.size
        stale(Playback(positionSeconds = 5.0))
        assertEquals(before, told.size)
        assertEquals("w2", media.ownerId)
    }

    @Test
    fun `the player closes with its owner`() {
        media.open("w1", "/a.mp4", File("/a.mp4"), sphere, 0.0)

        media.windowsChanged(setOf("w1", "w2"))
        assertEquals("w1", media.ownerId)
        media.windowsChanged(setOf("w2"))
        assertEquals(null, media.ownerId)
        assertEquals("close", backend.calls.last())
        expectCode("no-media") { media.play("w1") }
    }

    @Test
    fun `a seek is kept inside the video`() {
        media.open("w1", "/a.mp4", File("/a.mp4"), sphere, 0.0)
        backend.report(Playback(durationSeconds = 10.0))

        media.seek("w1", 30.0)
        media.seek("w1", -1.0)
        assertEquals(listOf("seek 10.0", "seek 0.0"), backend.calls.takeLast(2))
    }

    @Test
    fun `a layout must name a known projection and stereo layout`() {
        val layout = MediaLayout.parse(JSONObject().put("projection", "equirect-180").put("stereo", "side-by-side"))
        assertEquals(MediaProjection.EQUIRECT_180, layout.projection)
        assertEquals(StereoLayout.SIDE_BY_SIDE, layout.stereo)
        expectCode("invalid-params") { MediaLayout.parse(JSONObject().put("projection", "cube").put("stereo", "mono")) }
        expectCode("invalid-params") { MediaLayout.parse(null) }
        assertFalse(media.state().getBoolean("open"))
    }

    @Test
    fun `only the owner makes the video rotatable, and a new video starts fixed`() {
        media.open("w1", "/a.mp4", File("/a.mp4"), sphere, 0.0)

        expectCode("not-owner") { media.setRotatable("w2", true) }
        media.setRotatable("w1", true)
        assertTrue(media.state().getBoolean("rotatable"))
        assertEquals("rotatable true", backend.calls.last())

        media.open("w1", "/b.mp4", File("/b.mp4"), sphere, 0.0)
        assertFalse(media.state().getBoolean("rotatable"))
    }
}
