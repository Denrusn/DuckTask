package com.ducktask.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACKNOWLEDGE) return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                if (logId > 0) {
                    db.reminderLogDao()
                        .acknowledge(logId, System.currentTimeMillis(), DISMISS_METHOD_NOTIFICATION)
                }
                if (taskId.isNotBlank()) {
                    val task = db.taskDao().getTaskByTaskId(taskId)
                    if (task?.status == TaskStatus.ALERTING) {
                        db.taskDao().updateStatus(taskId, TaskStatus.COMPLETED)
                    }
                }
                if (notificationId >= 0) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)
                }
            } catch (t: Throwable) {
                AppLogger.error("ReminderActionReceiver", "Failed to acknowledge reminder action", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.ducktask.app.action.ACKNOWLEDGE_REMINDER"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val DISMISS_METHOD_NOTIFICATION = "notification"
    }
}
