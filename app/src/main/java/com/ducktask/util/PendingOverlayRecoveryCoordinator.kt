package com.ducktask.app.util

class PendingOverlayRecoveryCoordinator(
    private val pendingStore: PendingOverlayStore,
    private val isDeviceLocked: () -> Boolean,
    private val overlayStarter: (PendingOverlayPayload) -> Boolean
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
        fun getPending(): PendingOverlayPayload?
        fun clearPendingIfMatches(taskId: String, logId: Long): Boolean
    }

    data class RecoveryResult(
        val status: RecoveryStatus,
        val pending: PendingOverlayPayload? = null
    )

    enum class RecoveryStatus {
        NO_PENDING,
        DEVICE_LOCKED,
        START_FAILED,
        STARTED
    }
}
