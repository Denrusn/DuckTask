package com.ducktask.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrongReminderOverlayServiceOverlayHoldControllerTest {
    @Test
    fun pressPartialReleaseResetsToIdle() {
        val controller = StrongReminderOverlayService.OverlayHoldController()

        val press = controller.press()
        val update = controller.updateProgress(0.42f)
        val release = controller.release()

        assertEquals(StrongReminderOverlayService.HoldState.CHARGING, press.state)
        assertEquals(
            StrongReminderOverlayService.OverlayHoldController.TransitionEvent.ENTERED_CHARGING,
            press.event
        )
        assertEquals(0.42f, update.progress, 0.0001f)
        assertEquals(StrongReminderOverlayService.HoldState.IDLE, release.state)
        assertEquals(0f, release.progress, 0.0001f)
        assertEquals(
            StrongReminderOverlayService.OverlayHoldController.TransitionEvent.RESET_TO_IDLE,
            release.event
        )
    }

    @Test
    fun fullProgressKeepsWaitingReleaseWhilePointerIsStillDown() {
        val controller = StrongReminderOverlayService.OverlayHoldController()

        controller.press()
        val charged = controller.updateProgress(1f)
        val stillHolding = controller.updateProgress(1f)

        assertEquals(
            StrongReminderOverlayService.HoldState.CHARGED_WAITING_RELEASE,
            charged.state
        )
        assertEquals(1f, charged.progress, 0.0001f)
        assertEquals(
            StrongReminderOverlayService.OverlayHoldController.TransitionEvent.ENTERED_CHARGED,
            charged.event
        )
        assertEquals(
            StrongReminderOverlayService.HoldState.CHARGED_WAITING_RELEASE,
            stillHolding.state
        )
        assertEquals(
            StrongReminderOverlayService.OverlayHoldController.TransitionEvent.NONE,
            stillHolding.event
        )
    }

    @Test
    fun fullProgressThenReleaseCompletes() {
        val controller = StrongReminderOverlayService.OverlayHoldController()

        controller.press()
        controller.updateProgress(1f)
        val release = controller.release()

        assertEquals(StrongReminderOverlayService.HoldState.COMPLETING, release.state)
        assertEquals(1f, release.progress, 0.0001f)
        assertEquals(
            StrongReminderOverlayService.OverlayHoldController.TransitionEvent.ENTERED_COMPLETING,
            release.event
        )
        assertTrue(release.changed)
    }

    @Test
    fun completingIgnoresRepeatedInput() {
        val controller = StrongReminderOverlayService.OverlayHoldController()

        controller.press()
        controller.updateProgress(1f)
        controller.release()
        val repeatedRelease = controller.release()
        val repeatedPress = controller.press()

        assertEquals(
            StrongReminderOverlayService.OverlayHoldController.TransitionEvent.NONE,
            repeatedRelease.event
        )
        assertEquals(StrongReminderOverlayService.HoldState.COMPLETING, repeatedRelease.state)
        assertFalse(repeatedRelease.changed)
        assertEquals(
            StrongReminderOverlayService.OverlayHoldController.TransitionEvent.NONE,
            repeatedPress.event
        )
        assertEquals(StrongReminderOverlayService.HoldState.COMPLETING, repeatedPress.state)
        assertFalse(repeatedPress.changed)
    }
}
