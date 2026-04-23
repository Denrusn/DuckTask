package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.scheduler.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = AppDatabase.getInstance(context).taskDao().getPendingTasksSnapshot()
                ReminderScheduler(context.applicationContext).reschedule(tasks)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
