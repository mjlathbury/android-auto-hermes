package com.hermes.drive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Boots the assistant automatically when Android Auto / a car connection starts, so the driver
 * never has to open the app. Triggered by the standard car-connection intents that head units
 * and the Desktop Head Unit (DHU) broadcast.
 */
class CarModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.intent.action.ENTER_CAR",
            "android.intent.action.HEAD_UNIT_START",
            -> DriveAssistantService.start(context)
        }
    }
}
