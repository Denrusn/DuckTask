package com.ducktask.app

import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.ui.views.HoldState
import com.ducktask.app.ui.views.OverlayActionClusterView
import com.ducktask.app.ui.views.OverlayHoldController
import com.ducktask.app.util.AppLogger
import com.ducktask.app.util.PendingOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FullScreenAlarmActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val accentColor = Color.parseColor("#FFFF6B35")
    private val armedColor = Color.parseColor("#FFFFC857")
    private val successColor = Color.parseColor("#FF4CAF50")

    // UI components
    private var eventText: TextView? = null
    private var statusText: TextView? = null
    private var backdropTintView: View? = null
    private var glowView: View? = null
    private var leftBeamView: View? = null
    private var rightBeamView: View? = null
    private var topBeamView: View? = null
    private var bottomBeamView: View? = null
    private var flashView: View? = null
    private var actionClusterView: OverlayActionClusterView? = null
    private var particleViews = mutableListOf<View>()

    // State
    private val holdController = OverlayHoldController()
    private var holdRunnable: Runnable? = null
    private val dismissRunnable = Runnable { dismissReminder() }
    private var currentTaskId: String = ""
    private var currentEvent: String = ""
    private var currentDescription: String = ""
    private var currentLogId: Long = -1L
    private var currentNotificationId: Int = -1
    private var holdState = HoldState.IDLE
    private var isPointerDown = false
    private var holdProgress = 0f
    private var completionProgress = 0f
    private var currentMilestone = 0
    private var ambientPulse = 0f
    private var chargedPulse = 0f
    private var orbitAnimator: ValueAnimator? = null
    private var ambientPulseAnimator: ValueAnimator? = null
    private var chargedStateAnimator: ValueAnimator? = null
    private var completionAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup window flags to show over lock screen
        setupWindowFlags()

        setContentView(buildUI())

        // Parse intent extras
        currentTaskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        currentEvent = intent.getStringExtra(EXTRA_EVENT).orEmpty().ifBlank { "DuckTask 提醒" }
        currentDescription = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        currentLogId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        currentNotificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, currentEvent.hashCode())

        // Clear pending overlay if matches
        PendingOverlayManager.clearPendingIfMatches(this, currentTaskId, currentLogId)

        // Initialize UI state
        syncHoldTransition(holdController.forceIdle())
        applyActionClusterBase(accentColor)
        enterIdleState()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        cancelVisualAnimators()
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Prevent back press from dismissing
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Fullscreen immersive
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E60D0D0D"))
            isClickable = true
            isFocusable = true
            clipChildren = false
            clipToPadding = false
        }

        backdropTintView = View(this).apply {
            setBackgroundColor(Color.parseColor("#FF8C42"))
            alpha = 0f
        }
        root.addView(
            backdropTintView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        glowView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.parseColor("#45FF6B35"), Color.TRANSPARENT)
            }
            alpha = 0.62f
        }
        root.addView(glowView, FrameLayout.LayoutParams(dp(520), dp(520)).apply {
            gravity = Gravity.CENTER
        })

        leftBeamView = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#18FFC857"),
                    Color.parseColor("#70FFC857"),
                    Color.TRANSPARENT
                )
            )
            alpha = 0.18f
        }
        root.addView(leftBeamView, FrameLayout.LayoutParams(dp(148), FrameLayout.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.START
        })

        rightBeamView = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#18FFC857"),
                    Color.parseColor("#70FFC857"),
                    Color.TRANSPARENT
                )
            )
            alpha = 0.18f
        }
        root.addView(rightBeamView, FrameLayout.LayoutParams(dp(148), FrameLayout.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.END
        })

        topBeamView = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#24FF9F43"),
                    Color.parseColor("#7AFFC857"),
                    Color.TRANSPARENT
                )
            )
            alpha = 0.14f
        }
        root.addView(topBeamView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(120)).apply {
            gravity = Gravity.TOP
        })

        bottomBeamView = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#24FF9F43"),
                    Color.parseColor("#7AFFC857"),
                    Color.TRANSPARENT
                )
            )
            alpha = 0.14f
        }
        root.addView(bottomBeamView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(120)).apply {
            gravity = Gravity.BOTTOM
        })

        eventText = TextView(this).apply {
            text = currentEvent.ifBlank { "DuckTask 提醒" }
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 34f
            gravity = Gravity.CENTER
            letterSpacing = 0.03f
            setShadowLayer(dp(18).toFloat(), 0f, 0f, Color.parseColor("#55FF6B35"))
        }
        root.addView(eventText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            topMargin = -dp(190)
        })

        statusText = TextView(this).apply {
            text = "长按确认"
            setTextColor(Color.parseColor("#F6FFD7B0"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 15f
            gravity = Gravity.CENTER
            letterSpacing = 0.06f
            alpha = 0.95f
            includeFontPadding = false
            setShadowLayer(dp(10).toFloat(), 0f, 0f, Color.parseColor("#5535180A"))
        }
        root.addView(statusText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(66)
        })

        actionClusterView = OverlayActionClusterView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(240), dp(240)).apply {
                gravity = Gravity.CENTER
                topMargin = dp(60)
            }
            setOnTouchListener { _, event ->
                handleButtonTouch(event)
                true
            }
        }
        root.addView(actionClusterView)

        flashView = View(this).apply {
            setBackgroundColor(Color.WHITE)
            alpha = 0f
        }
        root.addView(
            flashView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        return root
    }

    private fun handleButtonTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startHold()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> releaseHold()
        }
    }

    private fun startHold() {
        if (holdState == HoldState.COMPLETING) return
        mainHandler.removeCallbacks(dismissRunnable)
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        completionAnimator?.cancel()
        isPointerDown = true
        completionProgress = 0f
        currentMilestone = 0
        chargedPulse = 0f
        syncHoldTransition(holdController.press())
        updateStatusText("保持按住  能量校准中", Color.parseColor("#FFF9D8AF"))
        applyActionClusterBase(accentColor)
        actionClusterView?.setChargedPulse(0f)
        actionClusterView?.setCompletionProgress(0f)
        applyChargingVisuals(0f)
        startOrbitAnimator(1_450L)
        stopChargedStateAnimator()
        vibrateTick(20L, 40)
        val startedAt = SystemClock.elapsedRealtime()
        holdRunnable = object : Runnable {
            override fun run() {
                val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(HOLD_DURATION_MS)
                val progress = elapsed.toFloat() / HOLD_DURATION_MS.toFloat()
                val transition = holdController.updateProgress(progress)
                syncHoldTransition(transition)
                emitChargeMilestone(holdProgress)

                if (transition.event == OverlayHoldController.TransitionEvent.ENTERED_CHARGED || elapsed >= HOLD_DURATION_MS) {
                    enterChargedWaitingRelease()
                } else {
                    applyChargingVisuals(holdProgress)
                    mainHandler.postDelayed(this, 16)
                }
            }
        }
        mainHandler.post(holdRunnable!!)
    }

    private fun releaseHold() {
        isPointerDown = false
        val transition = holdController.release()
        syncHoldTransition(transition)
        when (transition.event) {
            OverlayHoldController.TransitionEvent.RESET_TO_IDLE -> enterIdleState()
            OverlayHoldController.TransitionEvent.ENTERED_COMPLETING -> triggerCompletionSequence()
            OverlayHoldController.TransitionEvent.NONE,
            OverlayHoldController.TransitionEvent.ENTERED_CHARGING,
            OverlayHoldController.TransitionEvent.ENTERED_CHARGED -> Unit
        }
    }

    private fun enterIdleState() {
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable = null
        syncHoldTransition(holdController.forceIdle())
        currentMilestone = 0
        chargedPulse = 0f
        completionProgress = 0f
        updateStatusText("长按确认", Color.parseColor("#F6FFD7B0"))
        applyActionClusterBase(accentColor)
        actionClusterView?.setChargedPulse(0f)
        actionClusterView?.setCompletionProgress(0f)
        startOrbitAnimator(4_200L)
        stopChargedStateAnimator()
        applyIdleVisuals()
    }

    private fun enterChargedWaitingRelease() {
        if (holdState == HoldState.COMPLETING) return
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable = null
        if (holdState != HoldState.CHARGED_WAITING_RELEASE) {
            syncHoldTransition(holdController.updateProgress(1f))
        }
        if (holdState != HoldState.CHARGED_WAITING_RELEASE) return
        completionProgress = 0f
        updateStatusText("已锁定  松手立即完成", Color.parseColor("#FFFFF3D6"))
        applyActionClusterBase(armedColor)
        actionClusterView?.setChargedPulse(0f)
        startOrbitAnimator(900L)
        startChargedStateAnimator()
        applyChargedWaitingVisuals(0f)
        vibrateTick(40L, 180)
        if (!isPointerDown) {
            triggerCompletionSequence()
        }
    }

    private fun triggerCompletionSequence() {
        val transition = holdController.completeFromCharged()
        if (transition.event == OverlayHoldController.TransitionEvent.ENTERED_COMPLETING) {
            syncHoldTransition(transition)
        }
        if (holdState != HoldState.COMPLETING) return
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable = null
        mainHandler.removeCallbacks(dismissRunnable)
        completionProgress = 0f
        updateStatusText("提醒已完成", Color.parseColor("#FFD9FFE1"))
        applyActionClusterBase(successColor)
        actionClusterView?.setCompletionProgress(0f)
        stopChargedStateAnimator()
        startOrbitAnimator(700L)
        vibrateTick(80L, 220)
        completionAnimator?.cancel()
        completionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                completionProgress = progress
                actionClusterView?.setCompletionProgress(progress)
                applyCompletionVisuals(progress)
            }
            start()
        }
        triggerParticleEffect()
        mainHandler.postDelayed(dismissRunnable, 920L)
    }

    private fun syncHoldTransition(transition: OverlayHoldController.Transition) {
        holdState = transition.state
        holdProgress = transition.progress
        actionClusterView?.setHoldState(holdState)
        actionClusterView?.setProgress(holdProgress)
    }

    private fun applyActionClusterBase(color: Int) {
        actionClusterView?.setAccentColor(color)
        actionClusterView?.setOrbitRotation(0f)
        actionClusterView?.setAmbientPulse(ambientPulse)
        actionClusterView?.setChargedPulse(chargedPulse)
        actionClusterView?.setCompletionProgress(completionProgress)
    }

    private fun applyIdleVisuals() {
        val pulse = ambientPulse
        backdropTintView?.alpha = 0.02f + pulse * 0.02f
        glowView?.apply {
            scaleX = 1f + pulse * 0.08f
            scaleY = 1f + pulse * 0.08f
            alpha = 0.56f + pulse * 0.14f
        }
        leftBeamView?.apply {
            alpha = 0.12f + pulse * 0.08f
            scaleX = 1f + pulse * 0.08f
            translationX = -dp(8).toFloat() + pulse * dp(4).toFloat()
        }
        rightBeamView?.apply {
            alpha = 0.12f + pulse * 0.08f
            scaleX = 1f + pulse * 0.08f
            translationX = dp(8).toFloat() - pulse * dp(4).toFloat()
        }
        topBeamView?.apply {
            alpha = 0.08f + pulse * 0.06f
            scaleY = 1f + pulse * 0.12f
            translationY = -dp(6).toFloat() + pulse * dp(4).toFloat()
        }
        bottomBeamView?.apply {
            alpha = 0.08f + pulse * 0.06f
            scaleY = 1f + pulse * 0.12f
            translationY = dp(6).toFloat() - pulse * dp(4).toFloat()
        }
        eventText?.apply {
            scaleX = 1f + pulse * 0.01f
            scaleY = 1f + pulse * 0.01f
            alpha = 0.96f
            letterSpacing = 0.03f
        }
        statusText?.apply {
            alpha = 0.84f + pulse * 0.12f
            scaleX = 1f + pulse * 0.015f
            scaleY = 1f + pulse * 0.015f
            translationY = 0f
        }
        actionClusterView?.setAmbientPulse(pulse)
        actionClusterView?.setChargedPulse(0f)
        actionClusterView?.setCompletionProgress(0f)
        flashView?.alpha = 0f
    }

    private fun applyChargingVisuals(progress: Float) {
        val pulse = ambientPulse
        backdropTintView?.alpha = 0.05f + progress * 0.09f + pulse * 0.02f
        glowView?.apply {
            scaleX = 1.04f + progress * 0.34f + pulse * 0.08f
            scaleY = 1.04f + progress * 0.34f + pulse * 0.08f
            alpha = 0.58f + progress * 0.22f + pulse * 0.08f
        }
        leftBeamView?.apply {
            alpha = 0.18f + progress * 0.26f + pulse * 0.05f
            scaleX = 1.02f + progress * 0.28f
            translationX = -dp(4).toFloat() + progress * dp(10).toFloat()
        }
        rightBeamView?.apply {
            alpha = 0.18f + progress * 0.26f + pulse * 0.05f
            scaleX = 1.02f + progress * 0.28f
            translationX = dp(4).toFloat() - progress * dp(10).toFloat()
        }
        topBeamView?.apply {
            alpha = 0.12f + progress * 0.18f + pulse * 0.04f
            scaleY = 1.04f + progress * 0.32f
            translationY = -dp(4).toFloat() + progress * dp(10).toFloat()
        }
        bottomBeamView?.apply {
            alpha = 0.12f + progress * 0.18f + pulse * 0.04f
            scaleY = 1.04f + progress * 0.32f
            translationY = dp(4).toFloat() - progress * dp(10).toFloat()
        }
        eventText?.apply {
            scaleX = 1f + progress * 0.045f
            scaleY = 1f + progress * 0.045f
            alpha = 0.95f + progress * 0.05f
            letterSpacing = 0.03f + progress * 0.03f
        }
        statusText?.apply {
            alpha = 0.86f + progress * 0.12f
            scaleX = 1f + progress * 0.03f
            scaleY = 1f + progress * 0.03f
            translationY = -progress * dp(3).toFloat()
        }
        actionClusterView?.setAmbientPulse(pulse)
        actionClusterView?.setChargedPulse(0f)
    }

    private fun applyChargedWaitingVisuals(pulse: Float) {
        chargedPulse = pulse
        actionClusterView?.setChargedPulse(pulse)
        backdropTintView?.alpha = 0.14f + pulse * 0.08f
        glowView?.apply {
            scaleX = 1.34f + pulse * 0.16f
            scaleY = 1.34f + pulse * 0.16f
            alpha = 0.78f + pulse * 0.16f
        }
        leftBeamView?.apply {
            alpha = 0.48f + pulse * 0.22f
            scaleX = 1.34f + pulse * 0.24f
            translationX = dp(10).toFloat() + pulse * dp(8).toFloat()
        }
        rightBeamView?.apply {
            alpha = 0.48f + pulse * 0.22f
            scaleX = 1.34f + pulse * 0.24f
            translationX = -dp(10).toFloat() - pulse * dp(8).toFloat()
        }
        topBeamView?.apply {
            alpha = 0.24f + pulse * 0.18f
            scaleY = 1.3f + pulse * 0.28f
            translationY = dp(8).toFloat() + pulse * dp(10).toFloat()
        }
        bottomBeamView?.apply {
            alpha = 0.24f + pulse * 0.18f
            scaleY = 1.3f + pulse * 0.28f
            translationY = -dp(8).toFloat() - pulse * dp(10).toFloat()
        }
        eventText?.apply {
            scaleX = 1.05f + pulse * 0.02f
            scaleY = 1.05f + pulse * 0.02f
            alpha = 1f
            letterSpacing = 0.07f + pulse * 0.02f
        }
        statusText?.apply {
            alpha = 0.94f + pulse * 0.06f
            scaleX = 1.02f + pulse * 0.03f
            scaleY = 1.02f + pulse * 0.03f
            translationY = -dp(5).toFloat() - pulse * dp(2).toFloat()
        }
    }

    private fun applyCompletionVisuals(progress: Float) {
        actionClusterView?.setChargedPulse(1f - progress * 0.45f)
        actionClusterView?.setCompletionProgress(progress)
        backdropTintView?.alpha = 0.26f - progress * 0.16f
        glowView?.apply {
            scaleX = 1.46f + progress * 0.48f
            scaleY = 1.46f + progress * 0.48f
            alpha = 0.92f - progress * 0.42f
        }
        leftBeamView?.apply {
            alpha = 0.72f - progress * 0.58f
            scaleX = 1.56f + progress * 0.34f
            translationX = dp(18).toFloat() + progress * dp(24).toFloat()
        }
        rightBeamView?.apply {
            alpha = 0.72f - progress * 0.58f
            scaleX = 1.56f + progress * 0.34f
            translationX = -dp(18).toFloat() - progress * dp(24).toFloat()
        }
        topBeamView?.apply {
            alpha = 0.58f - progress * 0.44f
            scaleY = 1.48f + progress * 0.34f
            translationY = dp(14).toFloat() + progress * dp(20).toFloat()
        }
        bottomBeamView?.apply {
            alpha = 0.58f - progress * 0.44f
            scaleY = 1.48f + progress * 0.34f
            translationY = -dp(14).toFloat() - progress * dp(20).toFloat()
        }
        eventText?.apply {
            scaleX = 1.07f + progress * 0.06f
            scaleY = 1.07f + progress * 0.06f
            alpha = 1f - progress * 0.28f
        }
        statusText?.apply {
            alpha = 1f - progress * 0.35f
            scaleX = 1.03f + progress * 0.05f
            scaleY = 1.03f + progress * 0.05f
            translationY = -dp(6).toFloat() + progress * dp(12).toFloat()
        }
        flashView?.alpha = when {
            progress < 0.18f -> progress / 0.18f * 0.52f
            progress < 0.42f -> 0.52f - ((progress - 0.18f) / 0.24f) * 0.34f
            else -> (1f - progress) * 0.18f
        }.coerceAtLeast(0f)
    }

    private fun emitChargeMilestone(progress: Float) {
        val milestone = when {
            progress >= 1f -> 4
            progress >= 0.75f -> 3
            progress >= 0.5f -> 2
            progress >= 0.25f -> 1
            else -> 0
        }
        if (milestone > currentMilestone) {
            currentMilestone = milestone
            when (milestone) {
                1 -> vibrateTick(12L, 50)
                2 -> vibrateTick(16L, 70)
                3 -> vibrateTick(22L, 110)
                4 -> Unit
            }
        }
    }

    private fun startOrbitAnimator(durationMs: Long) {
        orbitAnimator?.cancel()
        orbitAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animator ->
                val rotation = animator.animatedValue as Float
                actionClusterView?.setOrbitRotation(rotation)
            }
            start()
        }
        if (ambientPulseAnimator == null) {
            ambientPulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1_200L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { animator ->
                    ambientPulse = animator.animatedValue as Float
                    if (holdState == HoldState.IDLE) {
                        applyIdleVisuals()
                    } else if (holdState == HoldState.CHARGING) {
                        applyChargingVisuals(holdProgress)
                    }
                }
                start()
            }
        }
    }

    private fun startChargedStateAnimator() {
        chargedStateAnimator?.cancel()
        chargedStateAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 780L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animator ->
                if (holdState != HoldState.CHARGED_WAITING_RELEASE) return@addUpdateListener
                applyChargedWaitingVisuals(animator.animatedValue as Float)
            }
            start()
        }
    }

    private fun stopChargedStateAnimator() {
        chargedStateAnimator?.cancel()
        chargedStateAnimator = null
        chargedPulse = 0f
        actionClusterView?.setChargedPulse(0f)
    }

    private fun cancelVisualAnimators() {
        orbitAnimator?.cancel()
        orbitAnimator = null
        ambientPulseAnimator?.cancel()
        ambientPulseAnimator = null
        chargedStateAnimator?.cancel()
        chargedStateAnimator = null
        completionAnimator?.cancel()
        completionAnimator = null
        ambientPulse = 0f
        chargedPulse = 0f
        completionProgress = 0f
    }

    private fun updateStatusText(text: String, color: Int) {
        statusText?.text = text
        statusText?.setTextColor(color)
    }

    private fun currentActionClusterCenter(): Pair<Float, Float>? {
        val cluster = actionClusterView ?: return null
        return (cluster.x + cluster.width / 2f) to (cluster.y + cluster.height / 2f)
    }

    private fun vibrateTick(durationMs: Long, amplitude: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            }
        }
    }

    private fun triggerParticleEffect() {
        val (centerX, centerY) = currentActionClusterCenter() ?: return
        val particleCount = 28

        for (i in 0 until particleCount) {
            val angle = (i * (360f / particleCount) - 90f) * PI / 180.0
            val distance = (120 + Math.random() * 130).toFloat()
            val size = (8 + Math.random() * 18).toFloat()
            val color = when {
                i % 7 == 0 -> Color.WHITE
                i % 3 == 0 -> armedColor
                else -> successColor
            }

            val particle = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            }
            particle.layoutParams = FrameLayout.LayoutParams(size.toInt(), size.toInt())
            particle.x = centerX - size / 2
            particle.y = centerY - size / 2

            val rootView = findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(particle)
            particleViews.add(particle)

            // Animation
            val targetX = centerX + (distance * cos(angle)).toFloat() - size / 2
            val targetY = centerY + (distance * sin(angle)).toFloat() - size / 2

            particle.animate()
                .x(targetX)
                .y(targetY)
                .alpha(0f)
                .scaleX(0.2f)
                .scaleY(0.2f)
                .setDuration(860L)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    rootView.removeView(particle)
                    particleViews.remove(particle)
                }
                .start()
        }

        triggerRippleEffect(delayMs = 0L, startScale = 0.8f, strokeColor = successColor)
        triggerRippleEffect(delayMs = 120L, startScale = 1f, strokeColor = armedColor)
        triggerRippleEffect(delayMs = 240L, startScale = 1.15f, strokeColor = Color.WHITE)
    }

    private fun triggerRippleEffect(delayMs: Long, startScale: Float, strokeColor: Int) {
        val (centerX, centerY) = currentActionClusterCenter() ?: return

        val ripple = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), strokeColor)
            }
        }
        val size = ((actionClusterView?.width ?: dp(220)) * 0.92f).toInt()
        ripple.layoutParams = FrameLayout.LayoutParams(size, size)
        ripple.x = centerX - size / 2f
        ripple.y = centerY - size / 2f
        ripple.scaleX = startScale
        ripple.scaleY = startScale

        val rootView = findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(ripple)
        particleViews.add(ripple)

        ripple.animate()
            .scaleX(3f)
            .scaleY(3f)
            .alpha(0f)
            .setStartDelay(delayMs)
            .setDuration(1_050L)
            .setInterpolator(android.view.animation.LinearInterpolator())
            .withEndAction {
                rootView.removeView(ripple)
                particleViews.remove(ripple)
            }
            .start()
    }

    private fun dismissReminder() {
        mainHandler.removeCallbacksAndMessages(null)
        completionAnimator?.cancel()

        // Cancel notification
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(currentNotificationId)

        // Update database
        if (currentLogId > 0) {
            val logId = currentLogId
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val db = AppDatabase.getInstance(applicationContext)
                    db.reminderLogDao()
                        .acknowledge(logId, System.currentTimeMillis(), DISMISS_METHOD_ACTIVITY)
                    if (currentTaskId.isNotBlank()) {
                        val task = db.taskDao().getTaskByTaskId(currentTaskId)
                        if (task?.status == TaskStatus.ALERTING) {
                            db.taskDao().updateStatus(currentTaskId, TaskStatus.COMPLETED)
                        }
                    }
                }.onFailure {
                    AppLogger.error("FullScreenAlarm", "Failed to acknowledge dismissal", it)
                }
            }
        } else if (currentTaskId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val db = AppDatabase.getInstance(applicationContext)
                    val task = db.taskDao().getTaskByTaskId(currentTaskId)
                    if (task?.status == TaskStatus.ALERTING) {
                        db.taskDao().updateStatus(currentTaskId, TaskStatus.COMPLETED)
                    }
                }
            }
        }

        finish()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val DISMISS_METHOD_ACTIVITY = "activity"
        private const val HOLD_DURATION_MS = 3_000L
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_EVENT = "event"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun start(context: Context, taskId: String, event: String, description: String, logId: Long, notificationId: Int) {
            val intent = Intent(context, FullScreenAlarmActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_EVENT, event)
                putExtra(EXTRA_DESCRIPTION, description)
                putExtra(EXTRA_LOG_ID, logId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
    }
}
