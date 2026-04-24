package com.ducktask.app.data.repository

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.ducktask.app.data.local.ReminderLogDao
import com.ducktask.app.data.local.TaskDao
import com.ducktask.app.data.local.AppRuntimeLogDao
import com.ducktask.app.StrongReminderOverlayService
import com.ducktask.app.domain.model.AppRuntimeLog
import com.ducktask.app.domain.model.DEFAULT_USER_ID
import com.ducktask.app.domain.model.ReminderExecutionLog
import com.ducktask.app.domain.model.ReminderMode
import com.ducktask.app.domain.model.Task
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.domain.model.TRIGGER_TYPE_DATE
import com.ducktask.app.parser.TimeParser
import com.ducktask.app.scheduler.ReminderScheduler
import com.ducktask.app.util.AppLogger
import com.ducktask.app.util.parseEditableDateTime
import com.ducktask.app.util.toEpochMillis
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val appContext: Context,
    private val taskDao: TaskDao,
    private val reminderLogDao: ReminderLogDao,
    private val appRuntimeLogDao: AppRuntimeLogDao,
    private val scheduler: ReminderScheduler
) {
    fun getAllPendingTasks(): Flow<List<Task>> = taskDao.observePendingTasks()

    fun getAllTasks(): Flow<List<Task>> = taskDao.observeAllTasks()

    fun getExecutionLogs(): Flow<List<ReminderExecutionLog>> = reminderLogDao.observeLogs()

    fun getRuntimeLogs(): Flow<List<AppRuntimeLog>> = appRuntimeLogDao.observeLogs()

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    suspend fun createTaskFromText(text: String, reminderMode: Int = ReminderMode.NORMAL): Result<Task> {
        return runCatching {
            val parsed = TimeParser.parse(text)
            val repeat = parsed.repeat?.takeIf { it.isRepeating() }
            val firstRunTime = parsed.time.toEpochMillis()
            val task = Task(
                userId = DEFAULT_USER_ID,
                event = parsed.event,
                description = parsed.description,
                reminderTime = firstRunTime,
                repeat = repeat?.toJson(),
                reminderMode = reminderMode,
                triggerType = repeat?.triggerType() ?: TRIGGER_TYPE_DATE,
                nextRunTime = firstRunTime
            )
            val id = taskDao.insert(task)
            task.copy(id = id).also(scheduler::schedule)
        }.onFailure {
            AppLogger.error("TaskRepository", "createTaskFromText failed: $text", it)
        }
    }

    suspend fun updateTask(
        task: Task,
        event: String,
        description: String,
        nextRunTimeText: String,
        reminderMode: Int
    ): Result<Task> {
        return runCatching {
            val editedTime = parseEditableDateTime(nextRunTimeText)?.toEpochMillis()
                ?: error("时间格式错误，请使用 yyyy-MM-dd HH:mm")
            if (editedTime <= System.currentTimeMillis()) {
                error("提醒时间必须晚于当前时间")
            }

            val updatedTask = task.copy(
                event = event.trim().ifBlank { task.event },
                description = description.trim().ifBlank { task.description },
                reminderTime = editedTime,
                nextRunTime = editedTime,
                reminderMode = reminderMode
            )
            scheduler.cancel(task.taskId)
            taskDao.update(updatedTask)
            scheduler.schedule(updatedTask)
            updatedTask
        }.onFailure {
            AppLogger.error(
                "TaskRepository",
                "updateTask failed: taskId=${task.taskId}, nextRunTimeText=$nextRunTimeText",
                it
            )
        }
    }

    suspend fun deleteTask(task: Task) {
        scheduler.cancel(task.taskId)
        cancelReminderUi(task.taskId)
        taskDao.update(task.copy(status = TaskStatus.DELETED))
    }

    suspend fun markAsDone(task: Task) {
        scheduler.cancel(task.taskId)
        cancelReminderUi(task.taskId)
        taskDao.update(task.copy(status = TaskStatus.COMPLETED))
        // 同步更新 execution log
        val latestLog = reminderLogDao.findLatestUnacknowledged(task.taskId)
        if (latestLog != null) {
            reminderLogDao.acknowledge(latestLog.id, System.currentTimeMillis(), "手动完成")
        }
    }

    suspend fun acknowledgeExecution(logId: Long, dismissMethod: String) {
        reminderLogDao.acknowledge(logId, System.currentTimeMillis(), dismissMethod)
    }

    suspend fun reschedulePendingTasks() {
        scheduler.reschedule(taskDao.getPendingTasksSnapshot())
    }

    private fun cancelReminderUi(taskId: String) {
        val notificationId = taskId.hashCode()
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
        runCatching {
            appContext.stopService(Intent(appContext, StrongReminderOverlayService::class.java))
        }
    }
}
