package dev.halone.app.shell

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import dev.halone.compositor.RecordingTarget
import dev.halone.compositor.StereoCompositor
import dev.halone.shell.RecordingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records what the left eye sees to `Movies/Halone`: the compositor draws each
 * frame into the video encoder's surface, and with a [MediaProjection] the
 * sound of whatever is playing is captured alongside. UI thread, but for the
 * encoders' own threads.
 */
class ScreenRecorder(
    private val context: Context,
    private val compositor: () -> StereoCompositor?,
    private val notice: TransientNotice,
    private val log: (String) -> Unit,
    private val onState: (RecordingState) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var session: Session? = null

    val recording: Boolean get() = session != null

    /** [projection] is null when the wearer declined to share the sound; the recording is then silent. */
    fun start(projection: MediaProjection?) {
        if (session != null) return
        val target = compositor() ?: return notice.show("No display to record")
        val started = runCatching { Session(projection) }
        started.onFailure {
            log("  recorder: could not start: $it")
            projection?.stop()
            notice.show("Recording failed")
        }
        val running = started.getOrNull() ?: return
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (session === running) stop()
            }
        }, handler)
        session = running
        target.recording = running.target
        handler.postDelayed({
            if (session === running && running.videoSamples == 0) log("  recorder: no video from the encoder after ${CHECK_MS / 1000} s")
        }, CHECK_MS)
        onState(RecordingState(true, System.currentTimeMillis(), running.audio != null))
        log("  recorder: started${if (running.audio != null) " with sound" else ""}")
    }

    fun stop() {
        val running = session ?: return
        session = null
        compositor()?.let { if (it.recording === running.target) it.recording = null }
        onState(RecordingState())
        Thread({
            val saved = runCatching { running.finish() }
            handler.post {
                saved.onSuccess { log("  recorder: saved $it"); notice.show("Recording saved") }
                    .onFailure { log("  recorder: failed: $it"); notice.show("Recording failed") }
            }
        }, "halone-recorder-finish").start()
    }

    private inner class Session(projection: MediaProjection?) {
        private val name = "halone_${STAMP.format(Date())}.mp4"
        private val uri: Uri
        private val file: ParcelFileDescriptor
        private val muxer: MediaMuxer
        private val video: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val target: RecordingTarget
        val audio: AudioCapture?
        private val lock = Object()
        private var videoTrack = -1
        private var audioTrack = -1
        private var muxing = false

        @Volatile
        var videoSamples = 0
            private set
        private val videoDone = Thread(::drainVideo, "halone-recorder-video")

        /** Cleared when the encoder never says it has finished, so the drain lets go before the encoder is released. */
        @Volatile
        private var draining = true

        init {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Halone")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("the gallery refused it")
            file = context.contentResolver.openFileDescriptor(uri, "rw") ?: error("no file to record into")
            muxer = MediaMuxer(file.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            video.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            target = RecordingTarget(video.createInputSurface(), WIDTH, HEIGHT, FRAME_RATE)
            audio = projection?.let { runCatching { AudioCapture(it) }.onFailure { error -> log("  recorder: no sound: $error") }.getOrNull() }
            video.start()
            videoDone.start()
            audio?.start()
        }

        /** Blocks until both encoders have flushed, then publishes the file. */
        fun finish(): String {
            target.awaitReleased(FINISH_TIMEOUT_MS)
            video.signalEndOfInputStream()
            audio?.finish()
            videoDone.join(FINISH_TIMEOUT_MS)
            draining = false
            videoDone.join(FINISH_TIMEOUT_MS)
            video.release()
            target.surface.release()
            synchronized(lock) { if (muxing) muxer.stop() }
            muxer.release()
            file.close()
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return name
        }

        /** The muxer starts once every track it will have has its format; samples before that are dropped. */
        fun addTrack(format: MediaFormat, isVideo: Boolean) = synchronized(lock) {
            val track = muxer.addTrack(format)
            if (isVideo) videoTrack = track else audioTrack = track
            if (videoTrack >= 0 && (audio == null || audioTrack >= 0)) {
                muxer.start()
                muxing = true
            }
        }

        fun write(isVideo: Boolean, data: java.nio.ByteBuffer, info: MediaCodec.BufferInfo) = synchronized(lock) {
            if (isVideo && info.size > 0) videoSamples++
            if (!muxing || info.size == 0 || info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return@synchronized
            muxer.writeSampleData(if (isVideo) videoTrack else audioTrack, data, info)
        }

        private fun drainVideo() = runCatching {
            val info = MediaCodec.BufferInfo()
            while (draining) {
                val index = video.dequeueOutputBuffer(info, DRAIN_TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> addTrack(video.outputFormat, isVideo = true)
                    index >= 0 -> {
                        write(true, video.getOutputBuffer(index)!!, info)
                        video.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return@runCatching
                    }
                }
            }
        }.onFailure { log("  recorder: video stopped: $it") }

        /**
         * The sound of what is playing, captured from the mix: on Android
         * that needs a [MediaProjection], even for this app's own playback.
         */
        inner class AudioCapture(private val projection: MediaProjection) {
            private val bytesPerSecond = SAMPLE_RATE * CHANNELS * 2
            private val bufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 2

            @SuppressLint("MissingPermission")
            private val record: AudioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(
                    AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
            private val encoder: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferBytes)
                }
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            @Volatile
            private var stopping = false
            private val thread = Thread(::run, "halone-recorder-audio")

            fun start() {
                encoder.start()
                record.startRecording()
                thread.start()
            }

            fun finish() {
                stopping = true
                thread.join(FINISH_TIMEOUT_MS)
                runCatching { record.stop() }
                record.release()
                encoder.release()
                projection.stop()
            }

            private fun run() = runCatching { encode() }.onFailure { log("  recorder: sound stopped: $it") }

            private fun encode() {
                val info = MediaCodec.BufferInfo()
                val pcm = ByteArray(bufferBytes / 2)
                var sentEnd = false
                while (draining) {
                    if (!sentEnd) {
                        val input = encoder.dequeueInputBuffer(DRAIN_TIMEOUT_US)
                        if (input >= 0) {
                            val buffer = encoder.getInputBuffer(input)!!
                            buffer.clear()
                            val read = if (stopping) 0 else record.read(pcm, 0, minOf(pcm.size, buffer.capacity()))
                            val timeUs = System.nanoTime() / 1_000 - read.coerceAtLeast(0) * 1_000_000L / bytesPerSecond
                            if (stopping) {
                                encoder.queueInputBuffer(input, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sentEnd = true
                            } else if (read > 0) {
                                buffer.put(pcm, 0, read)
                                encoder.queueInputBuffer(input, 0, read, timeUs, 0)
                            } else {
                                encoder.queueInputBuffer(input, 0, 0, timeUs, 0)
                            }
                        }
                    }
                    val output = encoder.dequeueOutputBuffer(info, if (sentEnd) DRAIN_TIMEOUT_US else 0)
                    when {
                        output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> addTrack(encoder.outputFormat, isVideo = false)
                        output >= 0 -> {
                            write(false, encoder.getOutputBuffer(output)!!, info)
                            encoder.releaseOutputBuffer(output, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val STAMP = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val FRAME_RATE = 30
        const val VIDEO_BITRATE = 16_000_000
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val AUDIO_BITRATE = 192_000
        const val DRAIN_TIMEOUT_US = 10_000L
        const val FINISH_TIMEOUT_MS = 5_000L
        const val CHECK_MS = 3_000L
    }
}
