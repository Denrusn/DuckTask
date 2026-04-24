package com.ducktask.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ducktask.app.domain.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT * FROM reminder_tasks
        WHERE status IN (0, 3)
        ORDER BY CASE WHEN status = 3 THEN 0 ELSE 1 END, nextRunTime ASC
        """
    )
    fun observePendingTasks(): Flow<List<Task>>

    @Query("SELECT * FROM reminder_tasks ORDER BY createTime DESC")
    fun observeAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM reminder_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM reminder_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun getTaskByTaskId(taskId: String): Task?

    @Query("SELECT * FROM reminder_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun findTaskForEdit(taskId: String): Task?

    @Query(
        """
        SELECT * FROM reminder_tasks
        WHERE status = 0
        ORDER BY nextRunTime ASC
        """
    )
    suspend fun getPendingTasksSnapshot(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE reminder_tasks SET status = :status WHERE taskId = :taskId")
    suspend fun updateStatus(taskId: String, status: Int)

    @Query("UPDATE reminder_tasks SET nextRunTime = :nextRunTime WHERE taskId = :taskId")
    suspend fun updateNextRunTime(taskId: String, nextRunTime: Long)

    @Query("UPDATE reminder_tasks SET status = :status WHERE taskId = :taskId AND status = :fromStatus")
    suspend fun updateStatusIfMatches(taskId: String, fromStatus: Int, status: Int)
}
