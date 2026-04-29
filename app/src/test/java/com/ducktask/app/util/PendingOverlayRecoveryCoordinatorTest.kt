package com.ducktask.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PendingOverlayRecoveryCoordinatorTest {
    private val pending = PendingOverlayPayload(
        taskId = "task-1",
        event = "吃饭",
        description = "测试提醒",
        logId = 42L,
        notificationId = 7
    )

    @Test
    fun startsAndClearsPendingOverlayWhenDeviceIsUnlocked() {
        val store = FakePendingOverlayStore(pending)
        var startedWith: PendingOverlayPayload? = null
        val coordinator = PendingOverlayRecoveryCoordinator(
            pendingStore = store,
            isDeviceLocked = { false },
            overlayStarter = {
                startedWith = it
                true
            }
        )

        val result = coordinator.recoverPendingOverlay()

        assertEquals(PendingOverlayRecoveryCoordinator.RecoveryStatus.STARTED, result.status)
        assertSame(pending, result.pending)
        assertSame(pending, startedWith)
        assertEquals("task-1" to 42L, store.clearedMatch)
        assertNull(store.storedPending)
    }

    @Test
    fun keepsPendingOverlayWhenDeviceIsStillLocked() {
        val store = FakePendingOverlayStore(pending)
        var starterCalled = false
        val coordinator = PendingOverlayRecoveryCoordinator(
            pendingStore = store,
            isDeviceLocked = { true },
            overlayStarter = {
                starterCalled = true
                true
            }
        )

        val result = coordinator.recoverPendingOverlay()

        assertEquals(PendingOverlayRecoveryCoordinator.RecoveryStatus.DEVICE_LOCKED, result.status)
        assertSame(pending, result.pending)
        assertFalse(starterCalled)
        assertNull(store.clearedMatch)
        assertSame(pending, store.storedPending)
    }

    @Test
    fun keepsPendingOverlayWhenServiceStartFails() {
        val store = FakePendingOverlayStore(pending)
        val coordinator = PendingOverlayRecoveryCoordinator(
            pendingStore = store,
            isDeviceLocked = { false },
            overlayStarter = { false }
        )

        val result = coordinator.recoverPendingOverlay()

        assertEquals(PendingOverlayRecoveryCoordinator.RecoveryStatus.START_FAILED, result.status)
        assertSame(pending, result.pending)
        assertNull(store.clearedMatch)
        assertSame(pending, store.storedPending)
    }

    @Test
    fun doesNothingWhenNoPendingOverlayExists() {
        val store = FakePendingOverlayStore()
        var starterCalled = false
        val coordinator = PendingOverlayRecoveryCoordinator(
            pendingStore = store,
            isDeviceLocked = { false },
            overlayStarter = {
                starterCalled = true
                true
            }
        )

        val result = coordinator.recoverPendingOverlay()

        assertEquals(PendingOverlayRecoveryCoordinator.RecoveryStatus.NO_PENDING, result.status)
        assertNull(result.pending)
        assertFalse(starterCalled)
        assertNull(store.clearedMatch)
        assertNull(store.storedPending)
    }

    private class FakePendingOverlayStore(
        initialPending: PendingOverlayPayload? = null
    ) : PendingOverlayRecoveryCoordinator.PendingOverlayStore {
        var storedPending: PendingOverlayPayload? = initialPending
        var clearedMatch: Pair<String, Long>? = null

        override fun getPending(): PendingOverlayPayload? = storedPending

        override fun clearPendingIfMatches(taskId: String, logId: Long): Boolean {
            val current = storedPending ?: return false
            if (current.taskId != taskId || current.logId != logId) return false
            storedPending = null
            clearedMatch = taskId to logId
            return true
        }
    }
}
