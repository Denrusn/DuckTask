package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.ReminderMode
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val alertingTasks = db.taskDao().getTasksByStatus(TaskStatus.ALERTING)
                for (task in alertingTasks) {
                    if (task.reminderMode == ReminderMode.STRONG) {
                        val latestLog = db.reminderLogDao().findLatestUnacknowledged(task.taskId)
                        val logId = latestLog?.id ?: -1L
                        StrongReminderOverlayService.startIfPossible(context, task, logId)
                    }
                }
            } catch (t: Throwable) {
                AppLogger.error("DeviceUnlockReceiver", "Failed to handle unlock", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
