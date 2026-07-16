package com.hermes.drive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.SharedPreferences

/**
 * Self-heals a transient OS/service kill by restarting DriveAssistantService shortly after a
 * non-user stop. A restart counter (in a private SharedPreferences) caps the attempts so a hard
 * HyperOS kill can't loop forever. The counter resets once the service runs cleanly for a while.
 */
class RestartReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RESTART = "com.hermes.drive.ACTION_RESTART_SERVICE"
        private const val PREFS = "hermes_restart"
        private const val KEY_COUNT = "restart_count"
        private const val MAX_RESTARTS = 5
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESTART) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_COUNT, 0)
        if (count >= MAX_RESTARTS) {
            DebugLog.event(context, "Restart skipped — cap reached ($count/$MAX_RESTARTS), assuming hard kill")
            prefs.edit().remove(KEY_COUNT).apply()
            return
        }
        DebugLog.event(context, "RestartReceiver — restarting service (attempt ${count + 1}/$MAX_RESTARTS)")
        DriveAssistantService.start(context)
    }

    /** Schedules a one-shot restart ~1.5s out and bumps the attempt counter. */
    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_COUNT, 0)
        prefs.edit().putInt(KEY_COUNT, count + 1).apply()
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

    /** Called from the service once it has run cleanly, to reset the restart cap. */
    fun noteHealthy(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_COUNT).apply()
    }
}
