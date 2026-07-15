package com.hermes.drive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput as CoreRemoteInput

/**
 * Receives the driver's voice-dictated reply (via Android Auto's RemoteInput) and forwards
 * it to [DriveAssistantService] as a query.
 */
class MessageReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChatNotificationManager.ACTION_REPLY) return
        val results = CoreRemoteInput.getResultsFromIntent(intent)
        val reply = results?.getCharSequence(ChatNotificationManager.KEY_TEXT_REPLY)?.toString()
        if (!reply.isNullOrBlank()) {
            DriveAssistantService.enqueueQuery(context, reply)
        }
    }
}
