package com.ducktask.app

import android.app.Application
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.data.repository.TaskRepository
import com.ducktask.app.notification.DuckTaskNotifications
import com.ducktask.app.scheduler.ReminderScheduler
import com.ducktask.app.util.AppLogger
import com.ducktask.app.util.PendingOverlayRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DuckTaskApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val scheduler: ReminderScheduler by lazy { ReminderScheduler(this) }
    val repository: TaskRepository by lazy {
        TaskRepository(applicationContext, database.taskDao(), database.reminderLogDao(), database.appRuntimeLogDao(), scheduler)
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        DuckTaskNotifications.ensureChannel(this)
        appScope.launch {
            repository.reschedulePendingTasks()
        }
        PendingOverlayRecovery.recoverIfPossible(this, "app_start")
    }
}
