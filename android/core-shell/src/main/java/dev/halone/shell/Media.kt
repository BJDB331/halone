package dev.halone.shell

import dev.halone.compositor.MediaProjection
import dev.halone.compositor.StereoLayout
import java.io.File
import org.json.JSONObject

data class MediaLayout(val projection: MediaProjection, val stereo: StereoLayout) {
    fun toJson(): JSONObject = JSONObject().put("projection", projection.wire).put("stereo", stereo.wire)

    companion object {
        fun parse(json: JSONObject?): MediaLayout {
            val projection = MediaProjection.entries.firstOrNull { it.wire == json?.optString("projection") }
            val stereo = StereoLayout.entries.firstOrNull { it.wire == json?.optString("stereo") }
            if (json == null || projection == null || stereo == null) {
                throw HaloneException(
                    "invalid-params",
                    "layout must be one of ${MediaProjection.entries.joinToString { it.wire }} " +
                        "with one of ${StereoLayout.entries.joinToString { it.wire }}",
                )
            }
            return MediaLayout(projection, stereo)
        }
    }
}

/** What the decoder reports; everything else in the state is the controller's. */
data class Playback(
    val playing: Boolean = false,
    val ended: Boolean = false,
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val videoWidthPx: Int = 0,
    val videoHeightPx: Int = 0,
    val error: String? = null,
)

/** Decodes one video into the compositor's immersive layer. UI thread. */
interface MediaBackend {
    /** [onPlayback] is called on the UI thread whenever the playback changes, and a few times a second while playing. */
    fun open(file: File, layout: MediaLayout, startSeconds: Double, onPlayback: (Playback) -> Unit)
    fun play()
    fun pause()
    fun seek(seconds: Double)
    fun setLayout(layout: MediaLayout)
    fun setRotatable(rotatable: Boolean)
    fun recenter()
    fun close()
}

/**
 * `halone.media`: one video at a time, owned by the window that opened it. Only
 * the owner may drive it, a window that opens another takes it over, and the
 * player closes with its owner.
 */
class Media(private val backend: MediaBackend) {
    private var owner: String? = null
    private var path: String? = null
    private var layout = MediaLayout(MediaProjection.FLAT, StereoLayout.MONO)
    private var playback = Playback()
    private var rotatable = false

    /** Told the owner's new state; the old owner is told once, with `open` false, when it loses the player. */
    var onChange: (owner: String, state: JSONObject) -> Unit = { _, _ -> }

    val ownerId: String? get() = owner

    fun state(): JSONObject = JSONObject()
        .put("open", owner != null)
        .put("path", path ?: JSONObject.NULL)
        .put("projection", layout.projection.wire)
        .put("stereo", layout.stereo.wire)
        .put("playing", playback.playing)
        .put("ended", playback.ended)
        .put("positionSeconds", playback.positionSeconds)
        .put("durationSeconds", playback.durationSeconds)
        .put("videoWidthPx", playback.videoWidthPx)
        .put("videoHeightPx", playback.videoHeightPx)
        .put("rotatable", rotatable)
        .put("error", playback.error ?: JSONObject.NULL)

    fun open(caller: String, path: String, file: File, layout: MediaLayout, startSeconds: Double): JSONObject {
        if (owner != null) close()
        owner = caller
        this.path = path
        this.layout = layout
        rotatable = false
        playback = Playback(playing = true, positionSeconds = startSeconds.coerceAtLeast(0.0))
        backend.open(file, layout, startSeconds.coerceAtLeast(0.0)) { update ->
            if (owner != caller || this.path != path) return@open
            playback = update
            onChange(caller, state())
        }
        return state()
    }

    fun play(caller: String) {
        own(caller)
        backend.play()
    }

    fun pause(caller: String) {
        own(caller)
        backend.pause()
    }

    fun seek(caller: String, seconds: Double) {
        own(caller)
        val duration = playback.durationSeconds.takeIf { it > 0 } ?: Double.MAX_VALUE
        backend.seek(seconds.coerceIn(0.0, duration))
    }

    fun setLayout(caller: String, layout: MediaLayout) {
        own(caller)
        this.layout = layout
        backend.setLayout(layout)
        onChange(caller, state())
    }

    fun setRotatable(caller: String, rotatable: Boolean) {
        own(caller)
        this.rotatable = rotatable
        backend.setRotatable(rotatable)
        onChange(caller, state())
    }

    fun recenter(caller: String) {
        own(caller)
        backend.recenter()
    }

    fun close(caller: String) {
        own(caller)
        close()
    }

    /** Closes the player when its owner is not among [open]. */
    fun windowsChanged(open: Set<String>) {
        if (owner != null && owner !in open) close()
    }

    private fun close() {
        val previous = owner ?: return
        backend.close()
        owner = null
        path = null
        playback = Playback()
        onChange(previous, state())
    }

    private fun own(caller: String) {
        val current = owner ?: throw HaloneException("no-media", "no video is open")
        if (current != caller) throw HaloneException("not-owner", "another window owns the player")
    }
}
