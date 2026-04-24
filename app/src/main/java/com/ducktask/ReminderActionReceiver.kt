package com.ducktask.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACKNOWLEDGE) return
        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (logId > 0) {
                    AppDatabase.getInstance(context).reminderLogDao()
                        .acknowledge(logId, System.currentTimeMillis(), DISMISS_METHOD_NOTIFICATION)
                }
                if (notificationId >= 0) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.ducktask.app.action.ACKNOWLEDGE_REMINDER"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val DISMISS_METHOD_NOTIFICATION = "notification"
    }
}
