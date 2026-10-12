package dev.halone.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.halone.shell.FileRoot
import dev.halone.shell.FileServer
import dev.halone.shell.SharedFiles
import dev.halone.shell.StorageAccess
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pages on `https://halone.local/` reading files through [FileServer], as the
 * Video and Images windows do on the phone: a secure page taking media and
 * fetches from loopback HTTP. Run on the emulator only.
 */
@RunWith(AndroidJUnit4::class)
class FileServingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val folder = File(context.filesDir, "media").apply { mkdirs() }
    private val files = SharedFiles({ listOf(FileRoot("storage", "Storage", folder.path)) }, object : StorageAccess {
        override val granted = true
        override fun request() = Unit
    })
    private lateinit var server: FileServer
    private lateinit var webView: WebView

    @Before
    fun open() {
        server = FileServer(files, log = { android.util.Log.i("FileServingTest", it) }).also { files.urlBase = it.base }
    }

    @After
    fun close() {
        server.close()
        folder.listFiles()?.forEach { it.delete() }
    }

    private fun load(html: String) {
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.mediaPlaybackRequiresUserGesture = false
            webView.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.i("FileServingTest", "console: ${message.message()}")
                    return true
                }
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) = loaded.countDown()
            }
            webView.loadDataWithBaseURL("https://halone.local/", html, "text/html", "utf-8", null)
        }
        assertTrue(loaded.await(30, TimeUnit.SECONDS))
    }

    @Test
    fun videoPlaysAndSeeksFromLoopback() {
        val video = File(folder, "test_pattern.mp4")
        instrumentation.context.assets.open("test_pattern.mp4").use { input -> video.outputStream().use { input.copyTo(it) } }
        load("<video id=v muted src=\"${files.stat(video.path).getString("url")}\"></video>")

        evaluate("document.getElementById('v').play().catch(() => {}), '{}'")
        val state = waitFor { it.optInt("readyState") >= 2 && it.optDouble("currentTime") > 0.5 || it.has("error") }
        assertTrue("video did not play: $state", !state.has("error") && state.optDouble("currentTime") > 0.5)

        evaluate("document.getElementById('v').currentTime = 9, '{}'")
        val sought = waitFor { it.optDouble("currentTime") >= 9 || it.has("error") }
        assertTrue("video did not seek: $sought", sought.optDouble("currentTime") >= 9)
    }

    @Test
    fun aPageFetchesARangePastTwoGigabytes() {
        val sparse = File(folder, "sparse.bin")
        java.io.RandomAccessFile(sparse, "rw").use { file ->
            file.setLength(3_100_000_000L)
            file.seek(3_000_000_000L)
            file.write("far!".toByteArray())
        }
        load("<p>ranges</p>")
        val url = files.stat(sparse.path).getString("url")

        evaluate(
            "window.got = null; fetch('$url', { headers: { Range: 'bytes=3000000000-3000000003' } })" +
                ".then(async (r) => window.got = r.status + ':' + new TextDecoder().decode(await r.arrayBuffer()))" +
                ".catch((e) => window.got = 'error:' + e), '{}'",
        )
        var got = ""
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && (got.isEmpty() || got == "null")) {
            got = JSONObject(evaluate("JSON.stringify({ got: String(window.got) })")).optString("got")
            Thread.sleep(250)
        }
        assertTrue("fetched $got", got == "206:far!")
    }

    private val describe = """
        (() => {
          const v = document.getElementById('v');
          const s = { readyState: v.readyState, currentTime: v.currentTime, duration: v.duration, networkState: v.networkState };
          if (v.error) s.error = v.error.code + ' ' + v.error.message;
          return JSON.stringify(s);
        })()
    """.trimIndent()

    private fun waitFor(done: (JSONObject) -> Boolean): JSONObject {
        var state = JSONObject()
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            state = JSONObject(evaluate(describe))
            if (done(state)) return state
            Thread.sleep(250)
        }
        return state
    }

    private fun evaluate(script: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        instrumentation.runOnMainSync { webView.evaluateJavascript(script) { result = it; latch.countDown() } }
        latch.await(5, TimeUnit.SECONDS)
        return (JSONTokener(result).nextValue() as? String) ?: "{}"
    }
}
