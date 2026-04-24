package com.ducktask.app

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }.toBundle()
                pendingIntent.send(context, 0, null, null, null, null, options)
            } else {
                pendingIntent.send()
            }
        }.recoverCatching {
            context.startActivity(intent)
        }.onFailure {
            AppLogger.error("StrongReminderLauncher", "Failed to launch strong reminder popup", it)
        }
    }
}
