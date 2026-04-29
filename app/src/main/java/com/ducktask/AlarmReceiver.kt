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
import com.ducktask.app.util.AlarmLoopManager
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
            val appContext = context.applicationContext
            try {
                val database = AppDatabase.getInstance(appContext)
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
                    ReminderScheduler(appContext).schedule(updatedTask)
                    DuckTaskNotifications.showReminder(appContext, task, nextRunTime, logId)
                    // STRONG 模式：仅通过悬浮窗提醒
                    if (task.reminderMode == ReminderMode.STRONG) {
                        if (!PermissionUtils.canDrawOverlay(appContext)) {
                            AppLogger.info("AlarmReceiver", "Overlay permission missing, skip strong overlay for: ${task.event}")
                        } else {
                            val deviceLocked = PermissionUtils.isDeviceLocked(appContext)
                            if (deviceLocked) {
                                // 设备锁屏，保存待显示状态，并由前台服务守候到解锁
                                PendingOverlayManager.savePending(
                                    appContext,
                                    task.taskId,
                                    task.event,
                                    task.description,
                                    logId,
                                    task.taskId.hashCode()
                                )
                                AppLogger.info("AlarmReceiver", "Device locked, saved pending overlay for: ${task.event}")
                            }
                            val started = StrongReminderOverlayService.startIfPossible(appContext, updatedTask, logId)
                            if (!started) {
                                AppLogger.info(
                                    "AlarmReceiver",
                                    "Failed to start strong overlay service for: ${task.event}"
                                )
                            }

                            // 如果启用了闹钟样式或循环提醒（周期性任务也支持）
                            if (task.alarmEnabled) {
                                // 启动闹钟全屏
                                val alarmIntent = AlarmFullScreenActivity.createIntent(
                                    appContext,
                                    task.taskId,
                                    task.event,
                                    task.description,
                                    task.alarmRingtone,
                                    task.alarmVibrateCount,
                                    logId
                                )
                                appContext.startActivity(alarmIntent)
                            }

                            // 如果启用了循环提醒
                            if (task.alertLoopEnabled) {
                                AlarmLoopManager.startLoop(
                                    context = appContext,
                                    taskId = task.taskId,
                                    event = task.event,
                                    description = task.description,
                                    ringtone = task.alarmRingtone,
                                    vibrateCount = task.alarmVibrateCount,
                                    logId = logId,
                                    intervalMinutes = task.alertLoopIntervalMinutes,
                                    maxCount = task.alertLoopMaxCount
                                )
                            }
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
                    DuckTaskNotifications.showReminder(appContext, task, null, logId)
                    // STRONG 模式：仅通过悬浮窗提醒
                    if (task.reminderMode == ReminderMode.STRONG) {
                        if (!PermissionUtils.canDrawOverlay(appContext)) {
                            AppLogger.info("AlarmReceiver", "Overlay permission missing, skip strong overlay for: ${task.event}")
                        } else {
                            val deviceLocked = PermissionUtils.isDeviceLocked(appContext)
                            if (deviceLocked) {
                                // 设备锁屏，保存待显示状态，并由前台服务守候到解锁
                                PendingOverlayManager.savePending(
                                    appContext,
                                    task.taskId,
                                    task.event,
                                    task.description,
                                    logId,
                                    task.taskId.hashCode()
                                )
                                AppLogger.info("AlarmReceiver", "Device locked, saved pending overlay for: ${task.event}")
                            }
                            val started = StrongReminderOverlayService.startIfPossible(appContext, task, logId)
                            if (!started) {
                                AppLogger.info(
                                    "AlarmReceiver",
                                    "Failed to start strong overlay service for: ${task.event}"
                                )
                            }

                            // 如果启用了闹钟样式或循环提醒
                            if (task.alarmEnabled) {
                                // 启动闹钟全屏
                                val alarmIntent = AlarmFullScreenActivity.createIntent(
                                    appContext,
                                    task.taskId,
                                    task.event,
                                    task.description,
                                    task.alarmRingtone,
                                    task.alarmVibrateCount,
                                    logId
                                )
                                appContext.startActivity(alarmIntent)
                            }

                            // 如果启用了循环提醒
                            if (task.alertLoopEnabled) {
                                AlarmLoopManager.startLoop(
                                    context = appContext,
                                    taskId = task.taskId,
                                    event = task.event,
                                    description = task.description,
                                    ringtone = task.alarmRingtone,
                                    vibrateCount = task.alarmVibrateCount,
                                    logId = logId,
                                    intervalMinutes = task.alertLoopIntervalMinutes,
                                    maxCount = task.alertLoopMaxCount
                                )
                            }
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
