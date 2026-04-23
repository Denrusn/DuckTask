package com.ducktask.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ducktask.app.AlarmReceiver
import com.ducktask.app.domain.model.Task
import com.ducktask.app.domain.model.TaskStatus

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(task: Task) {
        if (task.status != TaskStatus.PENDING) return
        val pendingIntent = pendingIntent(task.taskId)
        val triggerAtMillis = task.nextRunTime.coerceAtLeast(System.currentTimeMillis() + 1_000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(taskId: String) {
        alarmManager.cancel(pendingIntent(taskId))
    }

    fun reschedule(tasks: List<Task>) {
        tasks.forEach(::schedule)
    }

    private fun pendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_REMINDER = "com.ducktask.app.action.REMINDER"
        const val EXTRA_TASK_ID = "task_id"
    }
}
