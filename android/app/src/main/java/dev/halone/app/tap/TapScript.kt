package dev.halone.app.tap

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.random.Random

/**
 * Spoken cues for recording taps: the wearer cannot see the phone, and in
 * stereo the glasses show the scene rather than anything the phone draws.
 *
 * Taps are cued by a beep, marked at the moment it sounds, at jittered
 * intervals so a detector cannot learn the rhythm. The motions afterwards are
 * the ones a tap detector must not fire on, the frame adjustment above all.
 */
class TapScript(private val context: Context, private val log: (String) -> Unit) {
    private lateinit var speech: TextToSpeech
    private val beeper = ToneGenerator(AudioManager.STREAM_MUSIC, BEEP_VOLUME)

    suspend fun run(recorder: TapRecorder) {
        if (!startSpeech()) {
            log("  text-to-speech unavailable; cannot cue the wearer")
            return
        }
        try {
            say("Tap recording. Keep your head still until you hear a beep.")
            recorder.mark("still")
            delay(5_000)
            taps(recorder, "left", "Tap the left side of the glasses at each beep.")
            taps(recorder, "right", "Now the right side, at each beep.")
            taps(recorder, "double-left", "Now a quick double tap on the left, at each beep.", count = DOUBLE_COUNT)
            motion(recorder, "nod", "Nod your head, yes, until the next instruction.")
            motion(recorder, "shake", "Shake your head, no.")
            motion(recorder, "turn", "Look around quickly, left, right, up and down.")
            motion(recorder, "adjust", "Adjust the glasses on your face, as you normally would.")
            motion(recorder, "talk", "Talk or chew for a few seconds.")
            motion(recorder, "walk", "Stand up and walk around, if you can.", seconds = 10)
            recorder.mark("end")
            say("Done. Thank you.")
        } finally {
            speech.shutdown()
            beeper.release()
        }
    }

    private suspend fun taps(recorder: TapRecorder, label: String, instruction: String, count: Int = TAP_COUNT) {
        say(instruction)
        delay(1_500)
        repeat(count) {
            beeper.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
            recorder.mark("tap-$label")
            delay(Random.nextLong(2_200, 3_400))
        }
    }

    private suspend fun motion(recorder: TapRecorder, label: String, instruction: String, seconds: Int = 6) {
        say(instruction)
        recorder.mark("$label-start")
        delay(seconds * 1_000L)
        recorder.mark("$label-end")
    }

    private suspend fun startSpeech(): Boolean {
        val ready = CompletableDeferred<Boolean>()
        speech = TextToSpeech(context) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
        if (withTimeoutOrNull(5_000) { ready.await() } != true) return false
        speech.language = Locale.US
        return true
    }

    private suspend fun say(text: String) {
        log("  cue: $text")
        val done = CompletableDeferred<Unit>()
        speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                done.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                done.complete(Unit)
            }
        })
        speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
        withTimeoutOrNull(10_000) { done.await() }
    }

    private companion object {
        const val TAP_COUNT = 8
        const val DOUBLE_COUNT = 5
        const val BEEP_MS = 120
        const val BEEP_VOLUME = 80
    }
}
