package com.ducktask.app.util

import android.content.Context

/**
 * 管理待显示的强提醒悬浮窗
 * 当设备锁屏时，闹钟触发后提醒会存入这里
 * 解锁后，DeviceUnlockReceiver 会读取并显示
 */
object PendingOverlayManager {
    private const val PREFS_NAME = "pending_overlay"
    private const val KEY_TASK_ID = "task_id"
    private const val KEY_EVENT = "event"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_LOG_ID = "log_id"
    private const val KEY_NOTIFICATION_ID = "notification_id"

    fun savePending(context: Context, taskId: String, event: String, description: String, logId: Long, notificationId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_EVENT, event)
            .putString(KEY_DESCRIPTION, description)
            .putLong(KEY_LOG_ID, logId)
            .putInt(KEY_NOTIFICATION_ID, notificationId)
            .apply()
    }

    fun hasPending(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_TASK_ID)
    }

    fun getPending(context: Context): PendingOverlayPayload? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_TASK_ID, null) ?: return null
        return PendingOverlayPayload(
            taskId = taskId,
            event = prefs.getString(KEY_EVENT, "") ?: "",
            description = prefs.getString(KEY_DESCRIPTION, "") ?: "",
            logId = prefs.getLong(KEY_LOG_ID, -1L),
            notificationId = prefs.getInt(KEY_NOTIFICATION_ID, taskId.hashCode())
        )
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun clearPendingIfMatches(context: Context, taskId: String, logId: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTaskId = prefs.getString(KEY_TASK_ID, null) ?: return false
        val currentLogId = prefs.getLong(KEY_LOG_ID, -1L)
        if (currentTaskId != taskId || currentLogId != logId) return false
        prefs.edit().clear().apply()
        return true
    }

}
