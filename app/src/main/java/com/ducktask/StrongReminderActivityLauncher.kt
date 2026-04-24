package com.ducktask.app

import android.content.Context
import android.content.Intent
import com.ducktask.app.domain.model.Task
import com.ducktask.app.util.AppLogger

object StrongReminderActivityLauncher {
    fun launch(context: Context, task: Task, logId: Long) {
        val notificationId = task.taskId.hashCode()
        val intent = Intent(context, StrongReminderActivity::class.java)
            .putExtra(StrongReminderActivity.EXTRA_EVENT, task.event)
            .putExtra(StrongReminderActivity.EXTRA_DESCRIPTION, task.description)
            .putExtra(StrongReminderActivity.EXTRA_LOG_ID, logId)
            .putExtra(StrongReminderActivity.EXTRA_NOTIFICATION_ID, notificationId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            AppLogger.error("StrongReminderLauncher", "Failed to launch strong reminder popup", it)
        }
    }
}
