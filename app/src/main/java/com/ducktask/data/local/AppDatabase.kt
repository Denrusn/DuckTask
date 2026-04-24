package com.ducktask.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ducktask.app.domain.model.AppRuntimeLog
import com.ducktask.app.domain.model.ReminderExecutionLog
import com.ducktask.app.domain.model.Task

@Database(entities = [Task::class, ReminderExecutionLog::class, AppRuntimeLog::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderLogDao(): ReminderLogDao
    abstract fun appRuntimeLogDao(): AppRuntimeLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ducktask_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
