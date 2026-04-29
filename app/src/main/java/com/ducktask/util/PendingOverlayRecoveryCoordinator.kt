package com.ducktask.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ducktask.app.StrongReminderOverlayService
import com.ducktask.app.notification.DuckTaskNotifications

class PendingOverlayRecoveryCoordinator(
    private val pendingStore: PendingOverlayStore,
    private val isDeviceLocked: () -> Boolean,
    private val overlayStarter: (PendingOverlayManager.PendingOverlay) -> Boolean
) {
    fun recoverPendingOverlay(): RecoveryResult {
        val pending = pendingStore.getPending() ?: return RecoveryResult(RecoveryStatus.NO_PENDING)
        if (isDeviceLocked()) {
            return RecoveryResult(RecoveryStatus.DEVICE_LOCKED, pending)
        }
        if (!overlayStarter(pending)) {
            return RecoveryResult(RecoveryStatus.START_FAILED, pending)
        }
        pendingStore.clearPendingIfMatches(pending.taskId, pending.logId)
        return RecoveryResult(RecoveryStatus.STARTED, pending)
    }

    interface PendingOverlayStore {
        fun getPending(): PendingOverlayManager.PendingOverlay?
        fun clearPendingIfMatches(taskId: String, logId: Long): Boolean
    }

    data class RecoveryResult(
        val status: RecoveryStatus,
        val pending: PendingOverlayManager.PendingOverlay? = null
    )

    enum class RecoveryStatus {
        NO_PENDING,
        DEVICE_LOCKED,
        START_FAILED,
        STARTED
    }
}

object PendingOverlayRecovery {
    fun recoverIfPossible(context: Context, source: String): PendingOverlayRecoveryCoordinator.RecoveryResult {
        val appContext = context.applicationContext
        val coordinator = PendingOverlayRecoveryCoordinator(
            pendingStore = object : PendingOverlayRecoveryCoordinator.PendingOverlayStore {
                override fun getPending(): PendingOverlayManager.PendingOverlay? {
                    return PendingOverlayManager.getPending(appContext)
                }

                override fun clearPendingIfMatches(taskId: String, logId: Long): Boolean {
                    return PendingOverlayManager.clearPendingIfMatches(appContext, taskId, logId)
                }
            },
            isDeviceLocked = { PermissionUtils.isDeviceLocked(appContext) },
            overlayStarter = { pending -> startOverlay(appContext, pending) }
        )

        val result = coordinator.recoverPendingOverlay()
        when (result.status) {
            PendingOverlayRecoveryCoordinator.RecoveryStatus.NO_PENDING -> {
                AppLogger.info("PendingOverlayRecovery", "No pending overlay to recover: $source")
            }
            PendingOverlayRecoveryCoordinator.RecoveryStatus.DEVICE_LOCKED -> {
                AppLogger.info(
                    "PendingOverlayRecovery",
                    "Pending overlay still blocked by lock screen: $source for ${result.pending?.event}"
                )
            }
            PendingOverlayRecoveryCoordinator.RecoveryStatus.START_FAILED -> {
                AppLogger.info(
                    "PendingOverlayRecovery",
                    "Pending overlay start failed, will retry later: $source for ${result.pending?.event}"
                )
            }
            PendingOverlayRecoveryCoordinator.RecoveryStatus.STARTED -> {
                AppLogger.info(
                    "PendingOverlayRecovery",
                    "Recovered pending overlay via $source for ${result.pending?.event}"
                )
            }
        }
        return result
    }

    private fun startOverlay(
        context: Context,
        pending: PendingOverlayManager.PendingOverlay
    ): Boolean {
        val overlayIntent = Intent(context, StrongReminderOverlayService::class.java)
            .putExtra(StrongReminderOverlayService.EXTRA_TASK_ID, pending.taskId)
            .putExtra(StrongReminderOverlayService.EXTRA_EVENT, pending.event)
            .putExtra(StrongReminderOverlayService.EXTRA_DESCRIPTION, pending.description)
            .putExtra(StrongReminderOverlayService.EXTRA_LOG_ID, pending.logId)
            .putExtra(StrongReminderOverlayService.EXTRA_NOTIFICATION_ID, pending.notificationId)

        return runCatching {
            DuckTaskNotifications.ensureChannel(context)
            ContextCompat.startForegroundService(context, overlayIntent)
            true
        }.onFailure {
            AppLogger.error("PendingOverlayRecovery", "Failed to start overlay from pending state", it)
        }.getOrDefault(false)
    }
}
