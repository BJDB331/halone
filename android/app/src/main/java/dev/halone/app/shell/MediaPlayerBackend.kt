package dev.halone.app.shell

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Surface
import dev.halone.compositor.ImmersiveLayer
import dev.halone.compositor.StereoCompositor
import dev.halone.shell.MediaBackend
import dev.halone.shell.MediaLayout
import dev.halone.shell.Playback
import java.io.File

/**
 * `halone.media` on the phone: `MediaPlayer` decodes at the file's own
 * resolution into the compositor's immersive layer, which draws it around the
 * wearer or on a screen behind the panels. UI thread.
 */
class MediaPlayerBackend(
    private val compositor: () -> StereoCompositor?,
    private val log: (String) -> Unit,
) : MediaBackend {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var layer: ImmersiveLayer? = null
    private var surface: Surface? = null
    private var report: (Playback) -> Unit = {}
    private var prepared = false
    private var wantPlaying = true
    private var pendingSeekMs = 0
    private var ended = false
    private var error: String? = null

    var screenWidthDegrees: Float = 40f
        set(value) {
            field = value
            layer?.screenWidthDegrees = value
        }

    var sphereZoom: Float = 1f
        set(value) {
            field = value
            layer?.sphereZoom = value
        }

    var sphereDistanceMetres: Float = 6f
        set(value) {
            field = value
            layer?.sphereDistanceMetres = value
        }

    private val ticker = object : Runnable {
        override fun run() {
            publish()
            if (player?.isPlaying == true) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun open(file: File, layout: MediaLayout, startSeconds: Double, onPlayback: (Playback) -> Unit) {
        close()
        report = onPlayback
        prepared = false
        wantPlaying = true
        ended = false
        error = null
        pendingSeekMs = (startSeconds * 1000).toInt()
        val target = compositor()
        if (target == null) {
            fail("no display to play on")
            return
        }
        val player = MediaPlayer()
        this.player = player
        player.setOnPreparedListener {
            prepared = true
            if (pendingSeekMs > 0) it.seekTo(pendingSeekMs.toLong(), MediaPlayer.SEEK_CLOSEST)
            if (wantPlaying) it.start()
            startTicking()
        }
        player.setOnVideoSizeChangedListener { _, width, height ->
            layer?.videoWidthPx = width
            layer?.videoHeightPx = height
            publish()
        }
        player.setOnSeekCompleteListener { publish() }
        player.setOnCompletionListener {
            ended = true
            wantPlaying = false
            publish()
        }
        player.setOnErrorListener { _, what, extra ->
            fail("the video could not be played ($what/$extra)")
            true
        }
        try {
            player.setDataSource(file.path)
        } catch (failure: Exception) {
            fail("the video could not be opened: ${failure.message}")
            return
        }
        val layer = ImmersiveLayer(layout.projection, layout.stereo, target.headYawRadians) { texture ->
            if (this.player !== player) return@ImmersiveLayer
            val surface = Surface(texture)
            this.surface = surface
            player.setSurface(surface)
            player.prepareAsync()
        }
        layer.screenWidthDegrees = screenWidthDegrees
        layer.sphereZoom = sphereZoom
        layer.sphereDistanceMetres = sphereDistanceMetres
        this.layer = layer
        target.immersive = layer
        log("  media: opened ${file.name} as ${layout.projection.wire}/${layout.stereo.wire}")
    }

    override fun play() {
        val player = player ?: return
        wantPlaying = true
        if (!prepared) return
        if (ended) {
            ended = false
            player.seekTo(0)
        }
        player.start()
        startTicking()
    }

    override fun pause() {
        wantPlaying = false
        if (prepared) player?.pause()
        publish()
    }

    override fun seek(seconds: Double) {
        val ms = (seconds * 1000).toInt()
        ended = false
        if (prepared) player?.seekTo(ms.toLong(), MediaPlayer.SEEK_CLOSEST) else pendingSeekMs = ms
    }

    override fun setLayout(layout: MediaLayout) {
        layer?.projection = layout.projection
        layer?.stereo = layout.stereo
    }

    override fun setRotatable(rotatable: Boolean) {
        layer?.rotatable = rotatable
    }

    override fun recenter() {
        val yaw = compositor()?.headYawRadians ?: return
        layer?.anchorYawRadians = yaw
    }

    /** The decoder lets go of the surface before the compositor releases the texture behind it. */
    override fun close() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
        val compositor = compositor()
        if (compositor != null && compositor.immersive === layer) compositor.immersive = null
        layer = null
        surface?.release()
        surface = null
        prepared = false
    }

    private fun startTicking() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun fail(message: String) {
        error = message
        wantPlaying = false
        log("  media: $message")
        publish()
    }

    private fun publish() {
        val player = player
        val ready = prepared && player != null
        report(
            Playback(
                playing = ready && player!!.isPlaying,
                ended = ended,
                positionSeconds = if (ready) player!!.currentPosition / 1000.0 else pendingSeekMs / 1000.0,
                durationSeconds = if (ready) player!!.duration.coerceAtLeast(0) / 1000.0 else 0.0,
                videoWidthPx = layer?.videoWidthPx ?: 0,
                videoHeightPx = layer?.videoHeightPx ?: 0,
                error = error,
            ),
        )
    }

    companion object {
        private const val TICK_MS = 250L
    }
}
