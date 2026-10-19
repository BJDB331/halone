package dev.halone.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import dev.halone.app.shell.PreferencesStore
import dev.halone.shell.ShellSettings
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reports a tester can send in one tap, holding only Halone's own log
 * rather than the whole system's. Opt-in, as the privacy policy promises:
 * while the `diagnostics.crashReports` setting is off nothing is kept at all,
 * and turning it off deletes what was.
 *
 * Java crashes are written as they happen. Native crashes (GeckoView is native
 * code) and freezes can't be caught in-process, so Android's record of how the
 * process ended is read on the next launch instead (Android 11 and later), next
 * to the log that run left on disk.
 *
 * Everything that goes into a report is scrubbed of web addresses, storage
 * paths and downloaded file names, which Halone's log otherwise carries.
 */
object CrashReports {

    private const val RECENT_LINES = 400
    private const val MAX_TRACE_BYTES = 512 * 1024
    private const val PREFS = "crash-reports"
    private const val SEEN_UNTIL = "exit-info-seen-until"

    private val lock = Any()
    private val recent = ArrayDeque<String>()
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    var enabled = false
        private set

    private lateinit var app: Context
    private var runLog: Writer? = null

    /** Kept so the listener isn't garbage-collected; SharedPreferences holds it weakly. */
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun install(context: Context) {
        app = context.applicationContext
        val shellPrefs = app.getSharedPreferences(PreferencesStore.NAME, Context.MODE_PRIVATE)
        setEnabled(readSetting(shellPrefs), atStart = true)
        listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == PreferencesStore.KEY) setEnabled(readSetting(prefs), atStart = false)
        }.also { shellPrefs.registerOnSharedPreferenceChangeListener(it) }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (enabled) {
                runCatching { javaCrashFile().writeText(describe("Java crash on thread '${thread.name}'", stackTrace(error))) }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun readSetting(prefs: SharedPreferences): Boolean =
        prefs.getString(PreferencesStore.KEY, null)
            ?.let { runCatching { JSONObject(it).optBoolean(ShellSettings.CRASH_REPORTS, false) }.getOrNull() }
            ?: false

    private fun setEnabled(on: Boolean, atStart: Boolean) = synchronized(lock) {
        if (!atStart && on == enabled) return@synchronized
        enabled = on
        if (!on) {
            runLog?.let { runCatching { it.close() } }
            runLog = null
            recent.clear()
            dir().listFiles()?.forEach { it.delete() }
            return@synchronized
        }
        val current = File(dir(), "run.log")
        if (atStart && current.exists()) current.renameTo(File(dir(), "previous-run.log"))
        if (!atStart) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(SEEN_UNTIL, System.currentTimeMillis()).apply()
        }
        runLog = runCatching { current.bufferedWriter() }.getOrNull()
    }

    fun log(line: String) {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return
            val stamped = "${stamp.format(Date())} $line"
            recent.addLast(stamped)
            while (recent.size > RECENT_LINES) recent.removeFirst()
            runCatching {
                runLog?.apply {
                    write(stamped)
                    write("\n")
                    flush()
                }
            }
        }
    }

    /** The report waiting from a previous run, or null. Folds in how earlier runs ended. */
    fun pending(context: Context): File? {
        if (!enabled) return null
        val report = reportFile(context)
        val sections = mutableListOf<String>()
        val javaCrash = javaCrashFile()
        if (javaCrash.exists()) {
            sections += javaCrash.readText()
            javaCrash.delete()
        }
        val ended = exitReasons(context, skipJavaCrashes = sections.isNotEmpty())
        if (ended.isNotEmpty()) {
            sections += ended
            File(dir(), "previous-run.log").takeIf { it.exists() }?.let { log ->
                sections += "--- Halone log from the run that ended, most recent last ---\n" +
                    log.readLines().takeLast(RECENT_LINES).joinToString("\n")
            }
        }
        if (sections.isNotEmpty()) {
            val existing = if (report.exists()) report.readText() + "\n\n" else ""
            report.writeText(existing + scrub(sections.joinToString("\n\n")))
        }
        return report.takeIf { it.exists() }
    }

    /**
     * Halone's log as it stands, plus the previous run's, for sharing on
     * demand. The previous run matters most: it's the one that just vanished.
     */
    fun snapshot(context: Context): File? {
        if (!enabled) return null
        val text = buildString {
            appendLine("=== Halone log shared at ${wallClock(System.currentTimeMillis())} ===")
            appendLine(device())
            File(dir(), "previous-run.log").takeIf { it.exists() }?.let {
                appendLine("--- previous run, most recent last ---")
                it.readLines().takeLast(RECENT_LINES).forEach(::appendLine)
            }
            appendLine("--- this run, most recent last ---")
            synchronized(lock) { recent.forEach(::appendLine) }
        }
        return File(dir(), "halone-log.txt").apply { writeText(scrub(text)) }
    }

    fun clear(context: Context) {
        reportFile(context).delete()
    }

    private fun exitReasons(context: Context, skipJavaCrashes: Boolean): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val seenUntil = prefs.getLong(SEEN_UNTIL, 0L)
        prefs.edit().putLong(SEEN_UNTIL, now).apply()
        if (seenUntil == 0L) return emptyList()

        val am = context.getSystemService(ActivityManager::class.java)
        return am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            .filter { it.timestamp > seenUntil }
            .filter {
                it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                    it.reason == ApplicationExitInfo.REASON_ANR ||
                    (it.reason == ApplicationExitInfo.REASON_CRASH && !skipJavaCrashes)
            }
            .map { info ->
                val kind = when (info.reason) {
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
                    ApplicationExitInfo.REASON_ANR -> "App not responding"
                    else -> "Crash"
                }
                val trace = runCatching {
                    info.traceInputStream?.use { String(it.readUpTo(MAX_TRACE_BYTES), Charsets.UTF_8) }
                }.getOrNull()
                buildString {
                    appendLine("=== $kind at ${wallClock(info.timestamp)} (recorded by Android) ===")
                    appendLine(device())
                    appendLine("description: ${info.description}")
                    appendLine("importance: ${info.importance}, status: ${info.status}")
                    if (trace != null) {
                        appendLine("--- trace ---")
                        appendLine(trace)
                    } else {
                        appendLine("(Android kept no trace for this one)")
                    }
                }
            }
    }

    private fun describe(what: String, trace: String): String = scrub(
        buildString {
            appendLine("=== $what at ${wallClock(System.currentTimeMillis())} ===")
            appendLine(device())
            appendLine("--- stack ---")
            appendLine(trace)
            appendLine("--- Halone log, most recent last ---")
            synchronized(lock) { recent.forEach { appendLine(it) } }
        }
    )

    private fun device() =
        "Halone ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    private val URL = Regex("""\b(?:https?|ftp|file|content|data|blob|about|moz-extension|intent|javascript):[^\s)'"]*""")
    /** To the end of the line: paths can hold spaces, and Halone always logs them last. */
    private val STORAGE_PATH = Regex("""/(?:storage|sdcard|mnt|data/user|data/media)/.*""")
    /** `Downloader` logs `saved <name> (<n> bytes) to <path>`, and names may hold spaces. */
    private val SAVED_FILE = Regex("""(downloads: saved ).+?( \(\d+ bytes\))""")
    private val UNIT = Regex("""\(unit [^,)]+""")
    private val SERIAL = Regex("""serial=\S+""")

    /**
     * Strips what the privacy policy says a report never holds: browsing data
     * and files. Halone's own system pages (`halone.local`) stay, since they say
     * which screen was open and nothing about the person. Glasses serials go too.
     */
    fun scrub(text: String): String = text
        .replace(URL) { if (it.value.contains("://halone.local")) it.value else "<url>" }
        .replace(STORAGE_PATH, "<path>")
        .replace(SAVED_FILE, "$1<file>$2")
        .replace(UNIT, "(unit <removed>")
        .replace(SERIAL, "serial=<removed>")

    private fun stackTrace(error: Throwable) = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    private fun wallClock(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    private fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun dir() = File(app.filesDir, "crash").apply { mkdirs() }
    private fun javaCrashFile() = File(dir(), "java-crash.txt")
    fun reportFile(context: Context) = File(File(context.filesDir, "crash").apply { mkdirs() }, "halone-crash-report.txt")
}
