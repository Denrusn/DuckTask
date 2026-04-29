package com.ducktask.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ducktask.app.AlarmLoopReceiver
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AlarmLoopManager {
    private const val PREFS_NAME = "alarm_loop"
    private const val KEY_TASK_ID = "loop_task_id"
    private const val KEY_EVENT = "loop_event"
    private const val KEY_DESCRIPTION = "loop_description"
    private const val KEY_RINGTONE = "loop_ringtone"
    private const val KEY_VIBRATE_COUNT = "loop_vibrate_count"
    private const val KEY_LOG_ID = "loop_log_id"
    private const val KEY_INTERVAL_MINUTES = "loop_interval"
    private const val KEY_MAX_COUNT = "loop_max_count"
    private const val KEY_CURRENT_COUNT = "loop_current_count"
    const val ACTION_LOOP_REMINDER = "com.ducktask.app.ACTION_LOOP_REMINDER"

    fun startLoop(
        context: Context,
        taskId: String,
        event: String,
        description: String,
        ringtone: Boolean,
        vibrateCount: Int,
        logId: Long,
        intervalMinutes: Int,
        maxCount: Int
    ) {
        // 保存循环状态
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_EVENT, event)
            .putString(KEY_DESCRIPTION, description)
            .putBoolean(KEY_RINGTONE, ringtone)
            .putInt(KEY_VIBRATE_COUNT, vibrateCount)
            .putLong(KEY_LOG_ID, logId)
            .putInt(KEY_INTERVAL_MINUTES, intervalMinutes)
            .putInt(KEY_MAX_COUNT, maxCount)
            .putInt(KEY_CURRENT_COUNT, 1)
            .apply()

        // 立即显示第一次
        showAlarmActivity(context)

        // 调度下次提醒
        scheduleNextLoop(context, intervalMinutes)
    }

    fun onLoopTriggered(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_TASK_ID, null) ?: return

        val currentCount = prefs.getInt(KEY_CURRENT_COUNT, 0)
        val maxCount = prefs.getInt(KEY_MAX_COUNT, 5)
        val intervalMinutes = prefs.getInt(KEY_INTERVAL_MINUTES, 1)

        if (currentCount >= maxCount) {
            // 达到最大次数，结束循环
            endLoopWithExpiration(context)
            return
        }

        // 显示闹钟
        showAlarmActivity(context)

        // 更新计数并调度下次
        prefs.edit().putInt(KEY_CURRENT_COUNT, currentCount + 1).apply()
        scheduleNextLoop(context, intervalMinutes)
    }

    fun onTaskCompleted(context: Context, taskId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_TASK_ID, null) == taskId) {
            clearLoopState(context)
        }
    }

    fun cancelLoop(context: Context) {
        clearLoopState(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmLoopReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun showAlarmActivity(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_TASK_ID, null) ?: return
        val event = prefs.getString(KEY_EVENT, "") ?: ""
        val description = prefs.getString(KEY_DESCRIPTION, "") ?: ""
        val ringtone = prefs.getBoolean(KEY_RINGTONE, true)
        val vibrateCount = prefs.getInt(KEY_VIBRATE_COUNT, 5)
        val logId = prefs.getLong(KEY_LOG_ID, -1)

        val intent = com.ducktask.app.AlarmFullScreenActivity.createIntent(
            context, taskId, event, description, ringtone, vibrateCount, logId
        )
        context.startActivity(intent)
    }

    private fun scheduleNextLoop(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmLoopReceiver::class.java).apply {
            action = ACTION_LOOP_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun endLoopWithExpiration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_TASK_ID, null) ?: return
        val logId = prefs.getLong(KEY_LOG_ID, -1)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val task = db.taskDao().getTaskByTaskId(taskId)

            if (task != null) {
                if (task.hasRepeat()) {
                    // 周期性提醒：重置为待提醒
                    db.taskDao().updateStatus(taskId, TaskStatus.PENDING)
                    AppLogger.info("AlarmLoopManager", "Loop expired for periodic task, reset to PENDING: $taskId")
                } else {
                    // 一次性提醒：删除
                    db.taskDao().updateStatus(taskId, TaskStatus.DELETED)
                    if (logId > 0) {
                        db.reminderLogDao().acknowledge(logId, System.currentTimeMillis(), "loop_expired")
                    }
                    AppLogger.info("AlarmLoopManager", "Loop expired for one-time task, deleted: $taskId")
                }
            }

            clearLoopState(context)
        }
    }

    private fun clearLoopState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}