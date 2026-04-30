package com.ducktask.app.ui.views

/**
 * 强提醒持有控制器状态机
 * 管理长按交互的状态转换
 */
class OverlayHoldController {

    enum class TransitionEvent {
        NONE,
        ENTERED_CHARGING,
        RESET_TO_IDLE,
        ENTERED_CHARGED,
        ENTERED_COMPLETING
    }

    data class Transition(
        val state: HoldState,
        val progress: Float,
        val event: TransitionEvent,
        val changed: Boolean
    )

    private var state: HoldState = HoldState.IDLE
    private var progress: Float = 0f

    fun press(): Transition {
        if (state == HoldState.COMPLETING) return snapshot()
        val changed = state != HoldState.CHARGING || progress != 0f
        state = HoldState.CHARGING
        progress = 0f
        return snapshot(TransitionEvent.ENTERED_CHARGING, changed)
    }

    fun updateProgress(value: Float): Transition {
        val clamped = value.coerceIn(0f, 1f)
        return when (state) {
            HoldState.IDLE, HoldState.COMPLETING -> snapshot()
            HoldState.CHARGING -> {
                progress = clamped
                if (clamped >= 1f) {
                    state = HoldState.CHARGED_WAITING_RELEASE
                    progress = 1f
                    snapshot(TransitionEvent.ENTERED_CHARGED, true)
                } else {
                    snapshot(changed = true)
                }
            }
            HoldState.CHARGED_WAITING_RELEASE -> {
                progress = 1f
                snapshot()
            }
        }
    }

    fun release(): Transition {
        return when (state) {
            HoldState.CHARGING -> {
                state = HoldState.IDLE
                progress = 0f
                snapshot(TransitionEvent.RESET_TO_IDLE, true)
            }
            HoldState.CHARGED_WAITING_RELEASE -> {
                state = HoldState.COMPLETING
                progress = 1f
                snapshot(TransitionEvent.ENTERED_COMPLETING, true)
            }
            HoldState.IDLE, HoldState.COMPLETING -> snapshot()
        }
    }

    fun completeFromCharged(): Transition {
        return if (state == HoldState.CHARGED_WAITING_RELEASE) {
            state = HoldState.COMPLETING
            progress = 1f
            snapshot(TransitionEvent.ENTERED_COMPLETING, true)
        } else {
            snapshot()
        }
    }

    fun forceIdle(): Transition {
        val changed = state != HoldState.IDLE || progress != 0f
        state = HoldState.IDLE
        progress = 0f
        return snapshot(if (changed) TransitionEvent.RESET_TO_IDLE else TransitionEvent.NONE, changed)
    }

    private fun snapshot(
        event: TransitionEvent = TransitionEvent.NONE,
        changed: Boolean = false
    ): Transition {
        return Transition(state = state, progress = progress, event = event, changed = changed)
    }
}
