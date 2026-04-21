package com.ducktask.data.repository

import com.ducktask.data.local.TaskDao
import com.ducktask.domain.model.Task
import com.ducktask.parser.ParsedResult
import com.ducktask.parser.TimeParser
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {

    fun getAllPendingTasks(): Flow<List<Task>> = taskDao.getAllPendingTasks()

    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    suspend fun createTaskFromText(text: String): Result<Task> {
        return try {
            val parsed = TimeParser.parse(text)
            val task = Task(
                time = parsed.time,
                event = parsed.event,
                desc = text,
                repeatYears = parsed.repeat?.years ?: 0,
                repeatMonths = parsed.repeat?.months ?: 0,
                repeatWeeks = parsed.repeat?.weeks ?: 0,
                repeatDays = parsed.repeat?.days ?: 0,
                repeatHours = parsed.repeat?.hours ?: 0,
                repeatMinutes = parsed.repeat?.minutes ?: 0
            )
            val id = taskDao.insert(task)
            Result.success(task.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTask(task: Task): Long = taskDao.insert(task)

    suspend fun updateTask(task: Task) = taskDao.update(task)

    suspend fun deleteTask(task: Task) = taskDao.delete(task)

    suspend fun deleteTaskById(id: Long) = taskDao.deleteById(id)

    suspend fun markAsDone(id: Long) = taskDao.markAsDone(id)
}
