package com.ducktask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule all pending tasks after boot
            // In a full implementation, this would query the database
            // and reschedule all pending alarms
        }
    }
}
