package com.hermes.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput as CoreRemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Builds the notification Hermes shows while driving. A rich MessagingStyle notification is what
 * Android Auto consumes for read-aloud + voice-dictated replies. The minimal notification (see
 * buildForegroundNotification) is used only for startForeground(); the rich one is posted
 * separately. If HyperOS rejects the rich one, the global RemoteServiceException guard swallows
 * the throw so the process survives.
 */
object ChatNotificationManager {

    const val CHANNEL_ID = "hermes_drive_chat"
    const val NOTIF_ID = 1001
    const val ACTION_REPLY = "com.hermes.drive.ACTION_REPLY"
    const val KEY_TEXT_REPLY = "hermes_reply"
    private const val SHORTCUT_ID = "hermes_conv"

    private fun hermesPerson(context: Context): Person =
        Person.Builder()
            .setName("Hermes")
            .setKey("hermes")
            .setBot(true)
            .setImportant(true)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_hermes))
            .build()

    fun ensureChannel(context: Context) {
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val chan = NotificationChannel(
            CHANNEL_ID,
            "Hermes Drive Chat",
            NotificationManagerCompat.IMPORTANCE_HIGH,
        ).apply {
            description = "Voice chat with Hermes while driving"
        }
        mgr.createNotificationChannel(chan)
    }

    /** Registers a long-lived conversation shortcut so the MessagingStyle notification is valid
     *  on Android 12+ (a MessagingStyle without an associated shortcut falls into a non-conversation
     *  fallback that some OEMs reject). Safe to call repeatedly. */
    fun ensureShortcut(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val sm = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            if (sm == null) return
            if (sm.pinnedShortcuts.any { it.id == SHORTCUT_ID }) return
            if (sm.dynamicShortcuts.any { it.id == SHORTCUT_ID }) return
            val info = android.content.pm.ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setLongLived(true)
                .setShortLabel("Hermes")
                .setIcon(android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_hermes))
                .setIntent(Intent(context, com.hermes.drive.ui.SettingsActivity::class.java))
                .build()
            sm.addDynamicShortcuts(listOf(info))
        } catch (_: Exception) { /* OEM may restrict; MessagingStyle still posts, just without ranking */ }
    }

    fun buildNotification(
        context: Context,
        history: List<Pair<Boolean, String>>,
        showThinking: Boolean,
    ): Notification {
        ensureShortcut(context)
        val person = hermesPerson(context)
        val style = NotificationCompat.MessagingStyle(person)
        style.setConversationTitle("Hermes Drive")
        style.isGroupConversation = false
        for ((fromUser, text) in history) {
            val p = if (fromUser) {
                Person.Builder().setName("You").setKey("you").build()
            } else {
                person
            }
            style.addMessage(
                NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), p),
            )
        }
        if (showThinking) {
            style.addMessage(
                NotificationCompat.MessagingStyle.Message("…", System.currentTimeMillis(), person),
            )
        }

        val replyIntent = Intent(context, MessageReplyReceiver::class.java).apply {
            action = ACTION_REPLY
        }
        val replyPending = PendingIntent.getBroadcast(
            context,
            0,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val remoteInput = CoreRemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Speak to Hermes")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_hermes,
            "Reply",
            replyPending,
        ).addRemoteInput(remoteInput).build()

        val openIntent = Intent(context, com.hermes.drive.ui.SettingsActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hermes)
            .setStyle(style)
            .setShortcutId(SHORTCUT_ID)
            .setLocusId(LocusIdCompat(SHORTCUT_ID))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .addAction(replyAction)
            .setContentIntent(openPending)
            .setOngoing(true)
            .build()
    }

    /**
     * Minimal, guaranteed-valid notification used for startForeground(). The full MessagingStyle
     * notification is posted separately (see post) so a malformed rich style can never cause
     * "Bad notification for startForeground" / CannotPostForegroundServiceNotificationException.
     */
    fun buildForegroundNotification(context: Context): Notification {
        val openIntent = Intent(context, com.hermes.drive.ui.SettingsActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hermes)
            .setContentTitle("Hermes Drive")
            .setContentText("Starting…")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setShortcutId(SHORTCUT_ID)
            .setLocusId(LocusIdCompat(SHORTCUT_ID))
            .setContentIntent(openPending)
            .setOngoing(true)
            .build()
    }

    fun post(context: Context, notif: Notification) {
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }
}
