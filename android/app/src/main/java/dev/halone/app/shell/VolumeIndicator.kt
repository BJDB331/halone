package dev.halone.app.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import kotlin.math.roundToInt

/** Shows the media volume on the glasses for a moment whenever it changes. UI thread. */
class VolumeIndicator(private val context: Context, private val notice: TransientNotice) {
    private val audio = context.getSystemService(AudioManager::class.java)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return
            val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val fraction = level.toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            notice.show(if (level == 0) "Muted" else "Volume ${(fraction * 100).roundToInt()}%", fraction)
        }
    }

    fun start() {
        context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED), Context.RECEIVER_NOT_EXPORTED)
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    private companion object {
        /** Not in the SDK, but what the platform has broadcast for every volume change since Android 4. */
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
