package com.ducktask.app.data.repository

import com.ducktask.app.data.local.TaskDao
import com.ducktask.app.domain.model.DEFAULT_USER_ID
import com.ducktask.app.domain.model.Task
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.domain.model.TRIGGER_TYPE_DATE
import com.ducktask.app.parser.TimeParser
import com.ducktask.app.scheduler.ReminderScheduler
import com.ducktask.app.util.toEpochMillis
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val taskDao: TaskDao,
    private val scheduler: ReminderScheduler
) {
    fun getAllPendingTasks(): Flow<List<Task>> = taskDao.observePendingTasks()

    fun getAllTasks(): Flow<List<Task>> = taskDao.observeAllTasks()

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    suspend fun createTaskFromText(text: String): Result<Task> {
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
                triggerType = repeat?.triggerType() ?: TRIGGER_TYPE_DATE,
                nextRunTime = firstRunTime
            )
            val id = taskDao.insert(task)
            task.copy(id = id).also(scheduler::schedule)
        }
    }

    suspend fun deleteTask(task: Task) {
        scheduler.cancel(task.taskId)
        taskDao.update(task.copy(status = TaskStatus.DELETED))
    }

    suspend fun markAsDone(task: Task) {
        scheduler.cancel(task.taskId)
        taskDao.update(task.copy(status = TaskStatus.COMPLETED))
    }

    suspend fun reschedulePendingTasks() {
        scheduler.reschedule(taskDao.getPendingTasksSnapshot())
    }
}
