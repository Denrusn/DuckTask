package com.ducktask

import android.app.Application
import com.ducktask.data.local.AppDatabase
import com.ducktask.data.repository.TaskRepository

class DuckTaskApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
}
