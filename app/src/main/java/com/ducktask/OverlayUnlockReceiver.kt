package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.notification.DuckTaskNotifications
import com.ducktask.app.util.AppLogger
import com.ducktask.app.util.PendingOverlayManager

/**
 * 监听设备解锁事件
 * 当设备解锁时，检查是否有待显示的强提醒，有则立即显示悬浮窗
 */
class OverlayUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val pending = PendingOverlayManager.getPending(context)
        if (pending != null) {
            AppLogger.info("OverlayUnlockReceiver", "Device unlocked, showing pending overlay for: ${pending.event}")
            // 启动悬浮窗
            val overlayIntent = Intent(context, StrongReminderOverlayService::class.java)
                .putExtra(StrongReminderOverlayService.EXTRA_TASK_ID, pending.taskId)
                .putExtra(StrongReminderOverlayService.EXTRA_EVENT, pending.event)
                .putExtra(StrongReminderOverlayService.EXTRA_DESCRIPTION, pending.description)
                .putExtra(StrongReminderOverlayService.EXTRA_LOG_ID, pending.logId)
                .putExtra(StrongReminderOverlayService.EXTRA_NOTIFICATION_ID, pending.notificationId)
            DuckTaskNotifications.ensureChannel(context)
            context.startService(overlayIntent)
            // 不在这里清除 PendingOverlay，让悬浮窗完成后再清除
        }
    }
}