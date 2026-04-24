package com.ducktask.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ducktask.app.MainActivity
import com.ducktask.app.R
import com.ducktask.app.ReminderActionReceiver
import com.ducktask.app.StrongReminderActivity
import com.ducktask.app.domain.model.ReminderMode
import com.ducktask.app.domain.model.Task
import com.ducktask.app.util.formatAbsoluteTime

object DuckTaskNotifications {
    const val CHANNEL_ID = "ducktask_reminders"
    private const val CHANNEL_NAME = "DuckTask 提醒"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "DuckTask 定时提醒通知"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showReminder(context: Context, task: Task, nextRunTime: Long?, logId: Long) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notificationId = task.taskId.hashCode()
        val openAppIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextLine = nextRunTime?.let { "\n下次提醒时间：${formatAbsoluteTime(it)}" }.orEmpty()
        val repeatLine = task.repeatRule()?.toHumanText()?.takeIf { task.hasRepeat() }?.let { "\n重复：$it" }.orEmpty()
        val modeLine = "\n方式：${task.reminderModeLabel()}"
        val message = "备注：${task.description}$modeLine$nextLine$repeatLine"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("DuckTask：${task.event}")
            .setContentText(task.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(
                if (task.reminderMode == ReminderMode.STRONG) {
                    NotificationCompat.PRIORITY_MAX
                } else {
                    NotificationCompat.PRIORITY_HIGH
                }
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(
                if (task.reminderMode == ReminderMode.STRONG) {
                    NotificationCompat.CATEGORY_ALARM
                } else {
                    NotificationCompat.CATEGORY_REMINDER
                }
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent)

        if (task.reminderMode == ReminderMode.NORMAL) {
            builder.addAction(
                android.R.drawable.checkbox_on_background,
                "已处理",
                acknowledgeIntent(context, notificationId, logId)
            )
        } else {
            builder.setFullScreenIntent(
                strongReminderIntent(context, notificationId, logId, task),
                canUseFullScreenIntent(context)
            )
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun acknowledgeIntent(context: Context, notificationId: Int, logId: Long): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_ACKNOWLEDGE)
            .putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(ReminderActionReceiver.EXTRA_LOG_ID, logId)
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun strongReminderIntent(
        context: Context,
        notificationId: Int,
        logId: Long,
        task: Task
    ): PendingIntent {
        val intent = Intent(context, StrongReminderActivity::class.java)
            .putExtra(StrongReminderActivity.EXTRA_EVENT, task.event)
            .putExtra(StrongReminderActivity.EXTRA_DESCRIPTION, task.description)
            .putExtra(StrongReminderActivity.EXTRA_LOG_ID, logId)
            .putExtra(StrongReminderActivity.EXTRA_NOTIFICATION_ID, notificationId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 34) {
            val notificationService = context.getSystemService(NotificationManager::class.java)
            notificationService.canUseFullScreenIntent()
        } else {
            true
        }
    }
}
