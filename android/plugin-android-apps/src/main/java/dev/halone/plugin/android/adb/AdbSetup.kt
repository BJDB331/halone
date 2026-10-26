package dev.halone.plugin.android.adb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import dev.halone.plugin.android.HelperBridge
import dev.halone.plugin.android.privileged.ShellMain
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Starts the shell helper through the phone's own Wireless debugging, so
 * Android apps need no Shizuku: pair once with Android's pairing code, then
 * connect over loopback, check the connection really is the shell user, and
 * launch [ShellMain] from this APK with `app_process`, detached so it
 * outlives the connection. Ready only once the helper's Binder has arrived.
 *
 * After The Unbound Launcher's embedded ADB bootstrap (MIT). Pairing codes
 * are never stored or logged.
 */
object AdbSetup {
    enum class Phase { UNPAIRED, PAIRED, WORKING, READY, FAILED }

    @Volatile
    var phase = Phase.UNPAIRED
        private set

    /** One line for the setup screen and notices. */
    @Volatile
    var message = ""
        private set

    private val work = Executors.newSingleThreadExecutor { Thread(it, "halone-adb") }
    private val watchdog: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun paired(context: Context) = prefs(context).getBoolean(KEY_PAIRED, false)

    /** As [paired], from another of Halone's processes: pairing happens in ":apps", whose writes a cached copy would miss. */
    @Suppress("DEPRECATION")
    fun pairedSeenFromElsewhere(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_MULTI_PROCESS).getBoolean(KEY_PAIRED, false)

    /** Where things stand, without doing anything. */
    fun refresh(context: Context) {
        if (phase == Phase.WORKING) return
        update(
            when {
                HelperBridge.alive() -> Phase.READY
                paired(context) -> if (phase == Phase.FAILED) Phase.FAILED else Phase.PAIRED
                else -> Phase.UNPAIRED
            },
            null,
        )
    }

    /** Pairs with the six-digit code Android shows, then starts the helper. */
    fun pair(context: Context, code: String, pairingPort: Int = 0, done: ((Boolean) -> Unit)? = null) {
        val app = context.applicationContext
        if (!code.matches(Regex("[0-9]{6}"))) {
            update(Phase.FAILED, "Enter the six-digit code Android shows.")
            done?.let { main.post { it(false) } }
            return
        }
        if (phase == Phase.WORKING) return
        update(Phase.WORKING, "Pairing with this phone…")
        work.execute {
            var adb: LocalAdb? = null
            var discovery: AdbDiscovery? = null
            var ok = false
            try {
                discovery = AdbDiscovery(app)
                val port = pairingPort.takeIf { it > 0 } ?: waitFor(discovery, pair = true)
                    ?: throw Failure("Couldn't find the pairing port. Keep Android's pairing-code dialog open and try again.")
                adb = LocalAdb(app)
                if (!adb.pair(LocalAdb.LOOPBACK, port, code)) throw Failure("Android didn't accept that code. Check it and try again.")
                prefs(app).edit().putBoolean(KEY_PAIRED, true).apply()
                update(Phase.WORKING, "Paired. Starting Android apps…")
                start(app, adb, discovery)
                ok = true
            } catch (t: Throwable) {
                failed("Pairing", t, app)
            } finally {
                disconnect(adb)
                discovery?.close()
                done?.let { callback -> main.post { callback(ok) } }
            }
        }
    }

    /** Connects with the identity already paired and starts the helper; Wireless debugging must be on. */
    fun connect(context: Context) {
        val app = context.applicationContext
        if (phase == Phase.WORKING || !paired(app)) return
        if (HelperBridge.alive()) return update(Phase.READY, null)
        update(Phase.WORKING, "Starting Android apps…")
        work.execute {
            var adb: LocalAdb? = null
            var discovery: AdbDiscovery? = null
            try {
                discovery = AdbDiscovery(app)
                adb = LocalAdb(app)
                start(app, adb, discovery)
            } catch (t: Throwable) {
                failed("Connection", t, app)
            } finally {
                disconnect(adb)
                discovery?.close()
            }
        }
    }

    private fun start(context: Context, adb: LocalAdb, discovery: AdbDiscovery) {
        if (HelperBridge.alive()) return update(Phase.READY, null)
        val ports = LinkedHashSet<Int>()
        waitFor(discovery, pair = false)
        ports += discovery.ports(pair = false)
        prefs(context).getInt(KEY_CONNECT_PORT, 0).takeIf { it > 0 }?.let { ports += it }
        if (ports.isEmpty()) throw Failure("Wireless debugging seems to be off. Turn it on in Developer options and try again.")

        var connected = 0
        var last: Exception? = null
        for (port in ports) {
            try {
                if (adb.connect(LocalAdb.LOOPBACK, port)) {
                    connected = port
                    break
                }
            } catch (e: Exception) {
                last = e
                runCatching { adb.disconnect() }
            }
        }
        if (connected == 0) {
            throw Failure("Couldn't connect to Wireless debugging. Check it's on; if Android forgot Halone, pair again.", last)
        }
        if (shell(adb, "id -u").trim() != ShellMain.SHELL_UID.toString()) throw SecurityException("ADB did not give shell access")

        val apk = quote(context.applicationInfo.sourceDir)
        val main = ShellMain::class.java.name
        val authority = HelperBridge.authority(context.packageName)
        // An older helper, from before an update, goes first: it runs the
        // classes of the APK it was started from. A command of its own, and
        // a bracket in the pattern, so it cannot match its own command line.
        val pattern = "[" + main.first() + "]" + main.drop(1)
        runCatching { shell(adb, "pkill -f '$pattern'; true") }
        val command =
            "(CLASSPATH=$apk /system/bin/nohup /system/bin/setsid /system/bin/app_process / $main " +
            "${context.applicationInfo.uid} $authority > $LOG 2>&1 < /dev/null & sleep 0.3) && echo $STARTED"
        update(Phase.WORKING, "Starting the helper…")
        val output = shell(adb, command)
        if (!output.contains(STARTED)) {
            Log.w(TAG, "helper start: $output")
            throw Failure("Connected, but the helper didn't start. Try again.")
        }
        val until = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
        while (!HelperBridge.alive() && SystemClock.elapsedRealtime() < until) Thread.sleep(200)
        if (!HelperBridge.alive()) {
            Log.w(TAG, "helper log: " + runCatching { shell(adb, "tail -c 3000 $LOG") }.getOrDefault("?"))
            throw Failure("The helper started but didn't answer. Try again.")
        }
        prefs(context).edit().putInt(KEY_CONNECT_PORT, connected).apply()
        update(Phase.READY, null)
    }

    private fun waitFor(discovery: AdbDiscovery, pair: Boolean): Int? {
        val until = SystemClock.elapsedRealtime() + DISCOVERY_MS
        while (discovery.ports(pair).isEmpty() && SystemClock.elapsedRealtime() < until) Thread.sleep(150)
        return discovery.ports(pair).firstOrNull()
    }

    private fun shell(adb: LocalAdb, command: String): String {
        val timeout = watchdog.schedule({ disconnect(adb) }, SHELL_TIMEOUT_S, TimeUnit.SECONDS)
        try {
            adb.openStream("shell:$command").use { stream ->
                stream.openInputStream().use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > MAX_OUTPUT) throw IOException("unexpected helper output")
                        out.write(buffer, 0, n)
                    }
                    return out.toString(Charsets.UTF_8.name())
                }
            }
        } finally {
            timeout.cancel(false)
        }
    }

    private fun failed(action: String, t: Throwable, context: Context) {
        Log.w(TAG, "$action failed", t)
        update(
            Phase.FAILED,
            (t as? Failure)?.message ?: if (paired(context)) {
                "Couldn't start. Turn on Wireless debugging and try again; if Android forgot Halone, pair again."
            } else {
                "Pairing failed. Keep Android's pairing-code dialog open, check the code, and try again."
            },
        )
    }

    /**
     * Listeners hear only real changes. They react by reading the state
     * again, and reading it goes through here: announcing a non-change made
     * an endless loop that kept the Android apps process busy and Halone,
     * waiting on it, unresponsive.
     */
    @Synchronized
    private fun update(next: Phase, text: String?) {
        val nextMessage = text ?: when (next) {
            Phase.UNPAIRED -> "Not set up yet."
            Phase.PAIRED -> "Paired. Turn on Wireless debugging to start."
            Phase.READY -> "Ready. Android apps open on the glasses."
            Phase.WORKING, Phase.FAILED -> message
        }
        if (next == phase && nextMessage == message) return
        phase = next
        message = nextMessage
        main.post { listeners.toList().forEach { it() } }
    }

    private fun disconnect(adb: LocalAdb?) {
        runCatching { adb?.disconnect() }
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private class Failure(message: String, cause: Throwable? = null) : IOException(message, cause)

    private const val TAG = "HaloneAndroidApps"
    private const val PREFS = "android-apps-adb"
    private const val KEY_PAIRED = "paired"
    private const val KEY_CONNECT_PORT = "connect-port"
    private const val LOG = "/data/local/tmp/halone-android-apps.log"
    private const val STARTED = "HALONE_HELPER_STARTED"
    private const val READY_TIMEOUT_MS = 12_000L
    private const val DISCOVERY_MS = 5_000L
    private const val SHELL_TIMEOUT_S = 20L
    private const val MAX_OUTPUT = 32_768
}
