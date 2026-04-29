package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.util.AlarmLoopManager

class AlarmLoopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmLoopManager.ACTION_LOOP_REMINDER) {
            AlarmLoopManager.onLoopTriggered(context)
        }
    }
}