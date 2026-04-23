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
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showReminder(context: Context, task: Task, nextRunTime: Long?) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            task.taskId.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextLine = nextRunTime?.let { "\n下次提醒时间：${formatAbsoluteTime(it)}" }.orEmpty()
        val repeatLine = task.repeatRule()?.toHumanText()?.takeIf { task.hasRepeat() }?.let { "\n重复：$it" }.orEmpty()
        val message = "备注：${task.description}$nextLine$repeatLine"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("DuckTask：${task.event}")
            .setContentText(task.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()

        NotificationManagerCompat.from(context).notify(task.taskId.hashCode(), notification)
    }
}
