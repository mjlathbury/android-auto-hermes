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
import androidx.core.graphics.drawable.IconCompat

/**
 * Builds and posts the MessagingStyle notification that Android Auto surfaces in the car.
 * AA reads new Hermes messages aloud and voice-dictates the driver's reply via RemoteInput.
 */
object ChatNotificationManager {

    const val CHANNEL_ID = "hermes_drive_chat"
    const val NOTIF_ID = 1001
    const val ACTION_REPLY = "com.hermes.drive.ACTION_REPLY"
    const val KEY_TEXT_REPLY = "hermes_reply"

    private fun hermesPerson(context: Context): Person =
        Person.Builder()
            .setName("Hermes")
            .setKey("hermes")
            .setBot(true)
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
            setDescription("Voice chat with Hermes while driving")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowBubbles(true)
            }
        }
        mgr.createNotificationChannel(chan)
    }

    fun buildNotification(
        context: Context,
        history: List<Pair<Boolean, String>>,
        showThinking: Boolean,
    ): Notification {
        val last = history.lastOrNull()?.second ?: "…"
        val text = if (showThinking) "Thinking…" else last

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

        // Plain (non-MessagingStyle) notification: always valid on HyperOS/Android 12+ and avoids
        // CannotPostForegroundServiceNotificationException / RemoteServiceException kills.
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hermes)
            .setContentTitle("Hermes Drive")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
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
     * "Bad notification for startForeground" / CannotPostForegroundServiceNotificationException,
     * which HyperOS turns into an instant process kill.
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
            .setContentIntent(openPending)
            .setOngoing(true)
            .build()
    }

    fun post(context: Context, notif: Notification) {
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }
}
