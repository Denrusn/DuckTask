package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.ReminderExecutionLog
import com.ducktask.app.domain.model.ReminderMode
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.notification.DuckTaskNotifications
import com.ducktask.app.scheduler.ReminderScheduler
import com.ducktask.app.util.AppLogger
import com.ducktask.app.util.PendingOverlayManager
import com.ducktask.app.util.PermissionUtils
import com.ducktask.app.util.nextRunAfter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER) return
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val dao = database.taskDao()
                val logDao = database.reminderLogDao()
                val task = dao.getTaskByTaskId(taskId) ?: return@launch
                if (task.status != TaskStatus.PENDING) return@launch

                val repeat = task.repeatRule()
                if (repeat?.isRepeating() == true) {
                    val nextRunTime = repeat.nextRunAfter(task.nextRunTime)
                    val updatedTask = task.copy(nextRunTime = nextRunTime)
                    dao.update(updatedTask)
                    val logId = logDao.insert(
                        ReminderExecutionLog(
                            taskId = task.taskId,
                            event = task.event,
                            description = task.description,
                            reminderMode = task.reminderMode,
                            triggeredAt = System.currentTimeMillis(),
                            nextRunTime = nextRunTime
                        )
                    )
                    ReminderScheduler(context.applicationContext).schedule(updatedTask)
                    DuckTaskNotifications.showReminder(context, task, nextRunTime, logId)
                    // STRONG 模式：仅通过悬浮窗提醒
                    if (task.reminderMode == ReminderMode.STRONG) {
                        if (PermissionUtils.canDrawOverlay(this) && !PermissionUtils.isDeviceLocked(this)) {
                            // 设备未锁屏，直接显示悬浮窗
                            StrongReminderOverlayService.startIfPossible(this, updatedTask, logId)
                        } else {
                            // 设备锁屏，保存待显示状态，等解锁后显示
                            PendingOverlayManager.savePending(
                                this,
                                task.taskId,
                                task.event,
                                task.description,
                                logId,
                                task.taskId.hashCode()
                            )
                            AppLogger.info("AlarmReceiver", "Device locked, saved pending overlay for: ${task.event}")
                        }
                    }
                } else {
                    dao.update(task.copy(status = TaskStatus.ALERTING))
                    val logId = logDao.insert(
                        ReminderExecutionLog(
                            taskId = task.taskId,
                            event = task.event,
                            description = task.description,
                            reminderMode = task.reminderMode,
                            triggeredAt = System.currentTimeMillis()
                        )
                    )
                    DuckTaskNotifications.showReminder(context, task, null, logId)
                    // STRONG 模式：仅通过悬浮窗提醒
                    if (task.reminderMode == ReminderMode.STRONG) {
                        if (PermissionUtils.canDrawOverlay(this) && !PermissionUtils.isDeviceLocked(this)) {
                            // 设备未锁屏，直接显示悬浮窗
                            StrongReminderOverlayService.startIfPossible(this, task, logId)
                        } else {
                            // 设备锁屏，保存待显示状态，等解锁后显示
                            PendingOverlayManager.savePending(
                                this,
                                task.taskId,
                                task.event,
                                task.description,
                                logId,
                                task.taskId.hashCode()
                            )
                            AppLogger.info("AlarmReceiver", "Device locked, saved pending overlay for: ${task.event}")
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.error("AlarmReceiver", "Failed to execute reminder: taskId=$taskId", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
