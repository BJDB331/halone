package dev.halone.plugin.android.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import dev.halone.plugin.android.R

/**
 * Pairing-code entry from the notification shade, so Android's pairing
 * dialog can stay open in Settings while the code is typed: the dialog closes,
 * and the code changes, if Settings loses the screen. The flow follows The
 * Unbound Launcher's (MIT).
 */
object PairingNotification {
    private const val CHANNEL = "android-apps-setup"
    private const val ID = 0x4A10E
    private const val ACTION_CODE = "dev.halone.plugin.android.PAIRING_CODE"
    const val KEY_CODE = "code"

    /** Shows code entry, with [status] as its text when there is something to say. */
    fun show(context: Context, status: String? = null) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.pairing_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val reply = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, PairingReceiver::class.java).setAction(ACTION_CODE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val input = RemoteInput.Builder(KEY_CODE).setLabel(context.getString(R.string.pairing_code_label)).build()
        val action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_apps), context.getString(R.string.pairing_enter_code), reply,
        ).addRemoteInput(input).build()
        manager.notify(
            ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_apps)
                .setContentTitle(context.getString(R.string.pairing_title))
                .setContentText(status ?: context.getString(R.string.pairing_text))
                .setStyle(Notification.BigTextStyle().bigText(status ?: context.getString(R.string.pairing_text)))
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .addAction(action)
                .build(),
        )
    }

    /** Progress or the outcome, in place of code entry. */
    fun showStatus(context: Context, text: String, done: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(
            ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_apps)
                .setContentTitle(context.getString(R.string.pairing_title))
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, !done)
                .setAutoCancel(done)
                .apply { if (done) setTimeoutAfter(DONE_TIMEOUT_MS) }
                .build(),
        )
    }

    private const val TIMEOUT_MS = 5 * 60_000L
    private const val DONE_TIMEOUT_MS = 30_000L
}

/** Takes the code typed into the notification and pairs with it. */
class PairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PairingNotification.KEY_CODE)
            ?.toString()?.filter { it.isDigit() }.orEmpty()
        val app = context.applicationContext
        PairingNotification.showStatus(app, app.getString(R.string.pairing_working), done = false)
        val pending = goAsync()
        AdbSetup.pair(app, code) { ok ->
            if (ok) {
                PairingNotification.showStatus(app, app.getString(R.string.pairing_done), done = true)
            } else {
                PairingNotification.show(app, AdbSetup.message)
            }
            pending.finish()
        }
    }
}
