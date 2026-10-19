package dev.halone.testkit.android

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.format.Formatter
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import dev.halone.testkit.BuildConfig
import dev.halone.testkit.R
import dev.halone.testkit.ab.HeadView
import dev.halone.testkit.ab.SceneParams
import dev.halone.testkit.engine.Choice
import dev.halone.testkit.engine.GenericProfile
import dev.halone.testkit.engine.Io
import dev.halone.testkit.engine.ReportBundle
import dev.halone.testkit.engine.Runner
import dev.halone.testkit.engine.Session
import dev.halone.testkit.profiles.XrealOneProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), Io {

    private lateinit var progressView: TextView
    private lateinit var bodyView: TextView
    private lateinit var textInput: EditText
    private lateinit var choicesView: LinearLayout
    private lateinit var primaryButton: Button

    private lateinit var card: GlassesCard
    private var speech: TextToSpeech? = null
    private val speechReady = CompletableDeferred<Boolean>()
    private var speechWorks = false
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        progressView = findViewById(R.id.progress)
        bodyView = findViewById(R.id.body)
        textInput = findViewById(R.id.text_input)
        choicesView = findViewById(R.id.choices)
        primaryButton = findViewById(R.id.primary)

        card = GlassesCard(this).also { it.start() }
        speech = TextToSpeech(this) { status ->
            val ok = status == TextToSpeech.SUCCESS &&
                speech?.setLanguage(Locale.forLanguageTag(getString(R.string.tts_locale))) !in
                setOf(TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED)
            speechReady.complete(ok)
        }

        findViewById<TextView>(R.id.title).text = getString(R.string.start_title)
        bodyView.text = getString(R.string.start_body)
        primaryButton.text = getString(R.string.start_button)
        primaryButton.setOnClickListener { if (!running) begin() }
    }

    override fun onDestroy() {
        card.stop()
        speech?.shutdown()
        super.onDestroy()
    }

    private fun begin() {
        running = true
        primaryButton.visibility = View.GONE
        findViewById<TextView>(R.id.title).visibility = View.GONE
        lifecycleScope.launch {
            speechWorks = withTimeoutOrNull(SPEECH_INIT_TIMEOUT_MS) { speechReady.await() } == true
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val workDir = File(cacheDir, "sessions/$stamp").apply { mkdirs() }
            val session = Session()
            val kit = AndroidKit(this@MainActivity, this@MainActivity, session, workDir)

            val gathered = try {
                Runner(kit, listOf(XrealOneProfile, GenericProfile)).run()
            } finally {
                withContext(Dispatchers.IO) { kit.close() }
            }
            if (!gathered) {
                show(getString(R.string.cancelled_body))
                workDir.deleteRecursively()
                return@launch
            }
            val report = withContext(Dispatchers.IO) {
                File(cacheDir, "reports").mkdirs()
                File(cacheDir, "reports/halone-testkit-$stamp.zip").also { file ->
                    file.outputStream().use { ReportBundle.write(session, BuildConfig.VERSION_NAME, it) }
                    workDir.deleteRecursively()
                }
            }
            finished(report)
        }
    }

    private fun finished(report: File) {
        progressView.text = ""
        show(getString(R.string.finished_body, Formatter.formatShortFileSize(this, report.length())))
        primaryButton.text = getString(R.string.share_button)
        primaryButton.visibility = View.VISIBLE
        primaryButton.setOnClickListener {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", report)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, getString(R.string.share_button)))
        }
    }

    private fun show(text: String, choices: List<Choice> = emptyList(), onChoice: ((Choice) -> Unit)? = null) {
        bodyView.text = text
        card.show(text)
        textInput.visibility = View.GONE
        choicesView.removeAllViews()
        for (choice in choices) {
            val button = layoutInflater.inflate(R.layout.choice_button, choicesView, false) as Button
            button.text = choice.label
            button.setOnClickListener { onChoice?.invoke(choice) }
            choicesView.addView(button)
        }
    }

    override suspend fun say(text: String) {
        show(text)
        speakAndWait(text)
    }

    override suspend fun ask(question: String, choices: List<Choice>): Choice {
        val answer = CompletableDeferred<Choice>()
        show(question, choices) { answer.complete(it) }
        speak(question)
        return answer.await().also { stopSpeaking() }
    }

    override suspend fun askText(question: String, skip: Choice): String? {
        val answer = CompletableDeferred<String?>()
        val submit = Choice("submit", getString(R.string.continue_))
        show(question, listOf(submit, skip)) { choice ->
            answer.complete(if (choice == submit) textInput.text.toString() else null)
        }
        textInput.setText("")
        textInput.visibility = View.VISIBLE
        speak(question)
        return answer.await().also {
            stopSpeaking()
            textInput.visibility = View.GONE
        }
    }

    override suspend fun waitUntil(
        prompt: String,
        timeoutMs: Long,
        skip: Choice?,
        condition: suspend () -> Boolean,
    ): Boolean {
        if (condition()) return true
        var skipped = false
        show(prompt, listOfNotNull(skip)) { skipped = true }
        speak(prompt)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !skipped) {
            if (condition()) return true.also { stopSpeaking() }
            delay(POLL_MS)
        }
        stopSpeaking()
        return false
    }

    override suspend fun cue() {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, CUE_VOLUME) }.getOrNull() ?: return
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, CUE_MS)
        delay(CUE_MS.toLong())
        tone.release()
    }

    override fun showScene(view: HeadView?, params: SceneParams?) = card.showScene(view, params)

    override fun progress(done: Int, total: Int) {
        progressView.text = getString(R.string.step_progress, done, total)
    }

    private fun speak(text: String) {
        speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    private fun stopSpeaking() {
        speech?.stop()
    }

    private suspend fun speakAndWait(text: String) {
        val tts = speech
        if (tts == null || !speechWorks) {
            delay(READING_MS_PER_CHAR * text.length)
            return
        }
        val done = CompletableDeferred<Unit>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                done.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                done.complete(Unit)
            }
        })
        speak(text)
        withTimeoutOrNull(SPEECH_TIMEOUT_BASE_MS + READING_MS_PER_CHAR * text.length) { done.await() }
    }

    private companion object {
        const val SPEECH_INIT_TIMEOUT_MS = 5_000L
        const val SPEECH_TIMEOUT_BASE_MS = 3_000L
        const val READING_MS_PER_CHAR = 70L
        const val POLL_MS = 300L
        const val CUE_MS = 150
        const val CUE_VOLUME = 90
    }
}
