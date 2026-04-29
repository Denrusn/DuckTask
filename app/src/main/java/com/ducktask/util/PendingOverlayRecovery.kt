package com.ducktask.app.util

import android.content.Context
import com.ducktask.app.StrongReminderOverlayService

object PendingOverlayRecovery {
    fun recoverIfPossible(context: Context, source: String): PendingOverlayRecoveryCoordinator.RecoveryResult {
        val appContext = context.applicationContext
        val coordinator = PendingOverlayRecoveryCoordinator(
            pendingStore = object : PendingOverlayRecoveryCoordinator.PendingOverlayStore {
                override fun getPending(): PendingOverlayPayload? {
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
        pending: PendingOverlayPayload
    ): Boolean {
        return runCatching {
            StrongReminderOverlayService.startPendingIfPossible(context, pending)
        }.onFailure {
            AppLogger.error("PendingOverlayRecovery", "Failed to start overlay from pending state", it)
        }.getOrDefault(false)
    }
}
