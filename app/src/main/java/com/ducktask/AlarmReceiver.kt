package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.notification.DuckTaskNotifications
import com.ducktask.app.scheduler.ReminderScheduler
import com.ducktask.app.util.nextRunAfter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER) return
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).taskDao()
                val task = dao.getTaskByTaskId(taskId) ?: return@launch
                if (task.status != TaskStatus.PENDING) return@launch

                val repeat = task.repeatRule()
                if (repeat?.isRepeating() == true) {
                    val nextRunTime = repeat.nextRunAfter(task.nextRunTime)
                    val updatedTask = task.copy(nextRunTime = nextRunTime)
                    dao.update(updatedTask)
                    ReminderScheduler(context.applicationContext).schedule(updatedTask)
                    DuckTaskNotifications.showReminder(context, task, nextRunTime)
                } else {
                    dao.update(task.copy(status = TaskStatus.COMPLETED))
                    DuckTaskNotifications.showReminder(context, task, null)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
