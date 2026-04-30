package com.ducktask.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ducktask.app.domain.model.AppRuntimeLog
import com.ducktask.app.domain.model.ReminderExecutionLog
import com.ducktask.app.domain.model.Task

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alarm_enabled INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alarm_ringtone INTEGER DEFAULT 1")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alarm_vibrate_count INTEGER DEFAULT 5")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alert_loop_enabled INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alert_loop_interval_minutes INTEGER DEFAULT 1")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alert_loop_max_count INTEGER DEFAULT 5")
    }
}

@Database(entities = [Task::class, ReminderExecutionLog::class, AppRuntimeLog::class], version = 4, exportSchema = false)
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
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
