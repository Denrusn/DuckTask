package com.ducktask.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ducktask.app.domain.model.ReminderExecutionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderLogDao {
    @Query(
        """
        SELECT * FROM reminder_execution_logs
        ORDER BY triggeredAt DESC
        """
    )
    fun observeLogs(): Flow<List<ReminderExecutionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ReminderExecutionLog): Long

    @Query(
        """
        UPDATE reminder_execution_logs
        SET acknowledgedAt = :acknowledgedAt, dismissMethod = :dismissMethod
        WHERE id = :logId
        """
    )
    suspend fun acknowledge(logId: Long, acknowledgedAt: Long, dismissMethod: String)
}
