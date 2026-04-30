package com.ducktask.app

import com.ducktask.app.ui.views.HoldState
import com.ducktask.app.ui.views.OverlayHoldController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrongReminderOverlayServiceOverlayHoldControllerTest {
    @Test
    fun pressPartialReleaseResetsToIdle() {
        val controller = OverlayHoldController()

        val press = controller.press()
        val update = controller.updateProgress(0.42f)
        val release = controller.release()

        assertEquals(HoldState.CHARGING, press.state)
        assertEquals(
            OverlayHoldController.TransitionEvent.ENTERED_CHARGING,
            press.event
        )
        assertEquals(0.42f, update.progress, 0.0001f)
        assertEquals(HoldState.IDLE, release.state)
        assertEquals(0f, release.progress, 0.0001f)
        assertEquals(
            OverlayHoldController.TransitionEvent.RESET_TO_IDLE,
            release.event
        )
    }

    @Test
    fun fullProgressKeepsWaitingReleaseWhilePointerIsStillDown() {
        val controller = OverlayHoldController()

        controller.press()
        val charged = controller.updateProgress(1f)
        val stillHolding = controller.updateProgress(1f)

        assertEquals(
            HoldState.CHARGED_WAITING_RELEASE,
            charged.state
        )
        assertEquals(1f, charged.progress, 0.0001f)
        assertEquals(
            OverlayHoldController.TransitionEvent.ENTERED_CHARGED,
            charged.event
        )
        assertEquals(
            HoldState.CHARGED_WAITING_RELEASE,
            stillHolding.state
        )
        assertEquals(
            OverlayHoldController.TransitionEvent.NONE,
            stillHolding.event
        )
    }

    @Test
    fun fullProgressThenReleaseCompletes() {
        val controller = OverlayHoldController()

        controller.press()
        controller.updateProgress(1f)
        val release = controller.release()

        assertEquals(HoldState.COMPLETING, release.state)
        assertEquals(1f, release.progress, 0.0001f)
        assertEquals(
            OverlayHoldController.TransitionEvent.ENTERED_COMPLETING,
            release.event
        )
        assertTrue(release.changed)
    }

    @Test
    fun completingIgnoresRepeatedInput() {
        val controller = OverlayHoldController()

        controller.press()
        controller.updateProgress(1f)
        controller.release()
        val repeatedRelease = controller.release()
        val repeatedPress = controller.press()

        assertEquals(
            OverlayHoldController.TransitionEvent.NONE,
            repeatedRelease.event
        )
        assertEquals(HoldState.COMPLETING, repeatedRelease.state)
        assertFalse(repeatedRelease.changed)
        assertEquals(
            OverlayHoldController.TransitionEvent.NONE,
            repeatedPress.event
        )
        assertEquals(HoldState.COMPLETING, repeatedPress.state)
        assertFalse(repeatedPress.changed)
    }
}
