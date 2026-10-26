package dev.halone.plugin.android.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Parcel
import android.os.Process
import kotlin.system.exitProcess

/**
 * The shell helper: [DisplayService] in a process of its own, started by
 * Halone through the phone's own Wireless debugging rather than by Shizuku.
 *
 * Run as `app_process` from Halone's APK, as Android's shell user (uid 2000),
 * which is what gives it the rights a normal app lacks: trusted virtual
 * displays, launching other apps onto them, and injecting input. It hands its
 * Binder to Halone through [HelperBridge] and answers only Halone's uid.
 *
 * The start-up and provider rendezvous follow The Unbound Launcher's helper
 * (github.com/MarkPronkin/the-unbound-launcher, MIT).
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
object ShellMain {
    private const val TAG = "HaloneShellHelper"
    private val providerToken = Binder()
    private lateinit var main: Handler

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            run(args)
        } catch (t: Throwable) {
            System.err.println("$TAG failed")
            t.printStackTrace(System.err)
            exitProcess(1)
        }
    }

    private fun run(args: Array<String>) {
        check(Process.myUid() == SHELL_UID) { "start this helper through ADB shell" }
        require(args.size >= 2) { "usage: ShellMain <halone uid> <bridge authority>" }
        val allowedUid = args[0].toInt()
        val authority = args[1]
        println("$TAG start pid=${Process.myPid()} for uid $allowedUid")

        Looper.prepareMainLooper()
        main = Handler(Looper.getMainLooper())
        val service = DisplayService(shellContext())
        val guarded = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                val caller = Binder.getCallingUid()
                if (caller != allowedUid && caller != SHELL_UID) throw SecurityException("only Halone may use this helper")
                return service.transact(code, data, reply, flags)
            }
        }
        publish(authority, guarded, attempt = 0)
        Looper.loop()
    }

    /**
     * Hands [binder] to Halone's provider. If that process dies (an update,
     * a crash, Android reclaiming it), publishes again once it can be
     * started, so Halone finds the helper still there; gives up, and exits,
     * after [RETRY_LIMIT] failed attempts in a row.
     */
    private fun publish(authority: String, binder: IBinder, attempt: Int) {
        try {
            val provider = acquire(authority) ?: error("bridge provider unavailable")
            val payload = Bundle().apply { putBinder("binder", binder) }
            val result = ProviderCallCompat.call(provider, authority, "publish", payload)
            check(result?.getBoolean("published") == true) { "publication refused" }
            println("$TAG ready")
            (provider as IInterface).asBinder().linkToDeath({
                println("$TAG Halone's provider died; publishing again")
                main.postDelayed({ publish(authority, binder, 0) }, RETRY_MS)
            }, 0)
        } catch (e: Exception) {
            if (attempt + 1 >= RETRY_LIMIT) {
                System.err.println("$TAG giving up: $e")
                exitProcess(2)
            }
            main.postDelayed({ publish(authority, binder, attempt + 1) }, RETRY_MS)
        }
    }

    private fun acquire(authority: String): Any? {
        val activityManager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
        val method = Class.forName("android.app.IActivityManager").getMethod(
            "getContentProviderExternal", String::class.java, Int::class.javaPrimitiveType, IBinder::class.java, String::class.java,
        )
        val holder = method.invoke(activityManager, authority, 0, providerToken, TAG) ?: return null
        return holder.javaClass.getField("provider").get(holder)
    }

    /** The system's context, made to name the shell package, as the shell user's own calls must. */
    private fun shellContext(): Context {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("systemMain").invoke(null)
        val system = activityThread.getMethod("getSystemContext").invoke(thread) as Context
        val shell = system.createPackageContext(ShellContext.SHELL_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        setField(shell, "mOpPackageName", ShellContext.SHELL_PACKAGE)
        ProviderCallCompat.shellAttribution()?.let { source ->
            setField(shell, "mAttributionSource", source)
            setField(shell.contentResolver, "mAttributionSource", source)
        }
        setField(shell.contentResolver, "mPackageName", ShellContext.SHELL_PACKAGE)
        return shell
    }

    private fun setField(owner: Any, name: String, value: Any) {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try {
                type.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
    }

    const val SHELL_UID = 2000
    private const val RETRY_MS = 3_000L
    private const val RETRY_LIMIT = 100
}

/** The provider call's signature differs from Android 11 to 12; matched by reflection so 11 never loads 12's classes. */
@SuppressLint("PrivateApi")
internal object ProviderCallCompat {
    fun shellAttribution(): Any? {
        val builder = try {
            Class.forName("android.content.AttributionSource\$Builder")
        } catch (_: ClassNotFoundException) {
            return null
        }
        val source = builder.getConstructor(Int::class.javaPrimitiveType).newInstance(Process.myUid())
        builder.getMethod("setPackageName", String::class.java).invoke(source, ShellContext.SHELL_PACKAGE)
        return builder.getMethod("build").invoke(source)
    }

    fun call(provider: Any, authority: String, method: String, extras: Bundle): Bundle? {
        val api = Class.forName("android.content.IContentProvider")
        val source = shellAttribution()
        if (source != null) {
            val modern = runCatching {
                api.getMethod("call", source.javaClass, String::class.java, String::class.java, String::class.java, Bundle::class.java)
            }.getOrNull()
            if (modern != null) return modern.invoke(provider, source, authority, method, null, extras) as Bundle?
        }
        val legacy = api.getMethod(
            "call", String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, Bundle::class.java,
        )
        return legacy.invoke(provider, ShellContext.SHELL_PACKAGE, null, authority, method, null, extras) as Bundle?
    }
}
