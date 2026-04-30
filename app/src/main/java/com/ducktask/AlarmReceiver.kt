package com.ducktask.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.ReminderExecutionLog
import com.ducktask.app.domain.model.ReminderMode
import com.ducktask.app.domain.model.Task
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

                    // STRONG 模式：根据设备状态选择 Overlay 或 Activity
                    if (task.reminderMode == ReminderMode.STRONG) {
                        val taskForService = updatedTask
                        val logIdForService = logId
                        val notificationId = taskForService.taskId.hashCode()

                        // 停止可能存在的旧通知
                        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
                        notificationManager.cancel(notificationId)

                        mainHandler.post {
                            if (PermissionUtils.canDrawOverlay(appContext)) {
                                // 有悬浮窗权限：启动 overlay 服务（支持锁屏）
                                AppLogger.info("AlarmReceiver", "Starting overlay service for STRONG reminder")
                                StrongReminderOverlayService.startIfPossible(appContext, taskForService, logIdForService)
                            } else {
                                // 无悬浮窗权限：尝试启动全屏 Activity（需要 USE_FULL_SCREEN_INTENT 权限）
                                AppLogger.info("AlarmReceiver", "No overlay permission, trying Activity fallback")
                                startFullScreenActivitySafely(appContext, taskForService, logIdForService, notificationId)
                            }
                        }
                    } else {
                        DuckTaskNotifications.showReminder(appContext, task, nextRunTime, logId)
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

                    // STRONG 模式：根据设备状态选择 Overlay 或 Activity
                    if (task.reminderMode == ReminderMode.STRONG) {
                        val taskForService = task
                        val logIdForService = logId
                        val notificationId = taskForService.taskId.hashCode()

                        // 停止可能存在的旧通知
                        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
                        notificationManager.cancel(notificationId)

                        mainHandler.post {
                            if (PermissionUtils.canDrawOverlay(appContext)) {
                                // 有悬浮窗权限：启动 overlay 服务（支持锁屏）
                                AppLogger.info("AlarmReceiver", "Starting overlay service for STRONG reminder (non-repeating)")
                                StrongReminderOverlayService.startIfPossible(appContext, taskForService, logIdForService)
                            } else {
                                // 无悬浮窗权限：尝试启动全屏 Activity
                                AppLogger.info("AlarmReceiver", "No overlay permission, trying Activity fallback (non-repeating)")
                                startFullScreenActivitySafely(appContext, taskForService, logIdForService, notificationId)
                            }
                        }
                    } else {
                        DuckTaskNotifications.showReminder(appContext, task, null, logId)
                    }
                }
            } catch (t: Throwable) {
                AppLogger.error("AlarmReceiver", "Failed to execute reminder: taskId=$taskId", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun startFullScreenActivitySafely(
        context: Context,
        task: com.ducktask.app.domain.model.Task,
        logId: Long,
        notificationId: Int
    ) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val notificationService = context.getSystemService(NotificationManager::class.java)
                if (!notificationService.canUseFullScreenIntent()) {
                    AppLogger.warn("AlarmReceiver", "USE_FULL_SCREEN_INTENT permission not granted")
                    // 保存待显示状态，等待用户在设置中授予权限后恢复
                    PendingOverlayManager.savePending(
                        context,
                        task.taskId,
                        task.event,
                        task.description,
                        logId,
                        notificationId
                    )
                    // 显示通知提醒用户去设置权限
                    DuckTaskNotifications.showReminder(context, task, null, logId)
                    return
                }
            }
            // 权限已授予，启动全屏 Activity
            com.ducktask.app.FullScreenAlarmActivity.start(
                context,
                task.taskId,
                task.event,
                task.description,
                logId,
                notificationId
            )
        } catch (e: Exception) {
            AppLogger.error("AlarmReceiver", "Failed to start FullScreenAlarmActivity", e)
            // 回退到保存待显示状态
            PendingOverlayManager.savePending(
                context,
                task.taskId,
                task.event,
                task.description,
                logId,
                notificationId
            )
            DuckTaskNotifications.showReminder(context, task, null, logId)
        }
    }
}
