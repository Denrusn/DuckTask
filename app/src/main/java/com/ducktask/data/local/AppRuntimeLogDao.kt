package com.ducktask.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ducktask.app.domain.model.AppRuntimeLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuntimeLogDao {
    @Query(
        """
        SELECT * FROM app_runtime_logs
        ORDER BY createdAt DESC
        """
    )
    fun observeLogs(): Flow<List<AppRuntimeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AppRuntimeLog): Long

    @Query("DELETE FROM app_runtime_logs")
    suspend fun clearAll()
}
