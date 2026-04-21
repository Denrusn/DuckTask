package com.ducktask.data.local

import androidx.room.*
import com.ducktask.domain.model.Task
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY time ASC")
    fun getAllPendingTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY time DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND notify_time BETWEEN :start AND :end ORDER BY time ASC")
    suspend fun getTasksDueBetween(start: LocalDateTime, end: LocalDateTime): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET isDone = 1 WHERE id = :id")
    suspend fun markAsDone(id: Long)
}
