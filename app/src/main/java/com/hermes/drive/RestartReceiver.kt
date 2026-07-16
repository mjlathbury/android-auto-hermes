package com.hermes.drive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Restarts the assistant service if HyperOS (or the OS) stops it while the app is still in the
 * foreground. Scheduled by DriveAssistantService.onDestroy only when the stop was NOT user-initiated
 * and the app's activity is currently foregrounded — so it self-heals a background-service kill
 * during active use without looping when the app is truly closed.
 */
class RestartReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RESTART = "com.hermes.drive.ACTION_RESTART_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESTART) return
        if (!DriveAssistantService.isAppForeground(context)) {
            DebugLog.event(context, "Restart skipped — app not foreground")
            return
        }
        DebugLog.event(context, "RestartReceiver — restarting service (app foreground)")
        DriveAssistantService.start(context)
    }

    /** Schedules a one-shot restart ~1.5s out (only called when app is foreground). */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, RestartReceiver::class.java).apply { action = ACTION_RESTART },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val trigger = System.currentTimeMillis() + 1500L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }
}
