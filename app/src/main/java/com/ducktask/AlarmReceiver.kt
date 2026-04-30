package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.ducktask.app.FullScreenAlarmActivity
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.ReminderExecutionLog
import com.ducktask.app.domain.model.ReminderMode
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.notification.DuckTaskNotifications
import com.ducktask.app.scheduler.ReminderScheduler
import com.ducktask.app.util.AppLogger
import com.ducktask.app.util.PermissionUtils
import com.ducktask.app.util.nextRunAfter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    private val mainHandler = Handler(Looper.getMainLooper())

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
                    // STRONG 模式：启动全屏提醒 Activity（在主线程）
                    if (task.reminderMode == ReminderMode.STRONG) {
                        val taskForActivity = updatedTask
                        val logIdForActivity = logId
                        mainHandler.post {
                            try {
                                FullScreenAlarmActivity.start(
                                    appContext,
                                    taskForActivity.taskId,
                                    taskForActivity.event,
                                    taskForActivity.description,
                                    logIdForActivity,
                                    taskForActivity.taskId.hashCode()
                                )
                                AppLogger.info("AlarmReceiver", "Started FullScreenAlarmActivity for: ${taskForActivity.event}")
                            } catch (e: Exception) {
                                AppLogger.error("AlarmReceiver", "Failed to start FullScreenAlarmActivity", e)
                                // 备用：启动 StrongReminderOverlayService
                                if (PermissionUtils.canDrawOverlay(appContext)) {
                                    StrongReminderOverlayService.startIfPossible(appContext, taskForActivity, logIdForActivity)
                                }
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
                    // STRONG 模式：启动全屏提醒 Activity（在主线程）
                    if (task.reminderMode == ReminderMode.STRONG) {
                        val taskForActivity = task
                        val logIdForActivity = logId
                        mainHandler.post {
                            try {
                                FullScreenAlarmActivity.start(
                                    appContext,
                                    taskForActivity.taskId,
                                    taskForActivity.event,
                                    taskForActivity.description,
                                    logIdForActivity,
                                    taskForActivity.taskId.hashCode()
                                )
                                AppLogger.info("AlarmReceiver", "Started FullScreenAlarmActivity for: ${taskForActivity.event}")
                            } catch (e: Exception) {
                                AppLogger.error("AlarmReceiver", "Failed to start FullScreenAlarmActivity", e)
                                // 备用：启动 StrongReminderOverlayService
                                if (PermissionUtils.canDrawOverlay(appContext)) {
                                    StrongReminderOverlayService.startIfPossible(appContext, taskForActivity, logIdForActivity)
                                }
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
