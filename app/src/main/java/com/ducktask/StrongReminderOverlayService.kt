package com.ducktask.app

import android.animation.ValueAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Paint.Cap
import android.graphics.Paint.Style
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.Task
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.notification.DuckTaskNotifications
import com.ducktask.app.util.AppLogger
import com.ducktask.app.util.PendingOverlayManager
import com.ducktask.app.util.PendingOverlayPayload
import com.ducktask.app.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class StrongReminderOverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private val accentColor = Color.parseColor("#FFFF6B35")
    private val armedColor = Color.parseColor("#FFFFC857")
    private val successColor = Color.parseColor("#FF4CAF50")

    // UI 组件
    private var overlayView: FrameLayout? = null
    private var eventText: TextView? = null
    private var glowView: View? = null
    private var flashView: View? = null
    private var buttonContainer: FrameLayout? = null
    private var ringView: RingProgressView? = null
    private var buttonIcon: ImageView? = null
    private var buttonText: TextView? = null
    private var particleViews = mutableListOf<View>()

    // 状态
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
    private var currentMilestone = 0
    private var ambientPulse = 0f
    private var chargedPulse = 0f
    private var isWaitingForUnlock = false
    private var unlockReceiver: BroadcastReceiver? = null
    private var unlockPollAttemptsRemaining = 0
    private var unlockPollIntervalMs = 500L
    private var orbitAnimator: ValueAnimator? = null
    private var ambientPulseAnimator: ValueAnimator? = null
    private var chargedStateAnimator: ValueAnimator? = null
    private var completionAnimator: ValueAnimator? = null
    private val unlockCheckRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isWaitingForUnlock) return
            val shown = maybeShowDeferredOverlay("unlock_poll")
            if (!shown && unlockPollAttemptsRemaining > 0) {
                unlockPollAttemptsRemaining -= 1
                mainHandler.postDelayed(this, unlockPollIntervalMs)
            }
        }
    }

    enum class HoldState {
        IDLE,
        CHARGING,
        CHARGED_WAITING_RELEASE,
        COMPLETING
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!PermissionUtils.canDrawOverlay(this)) {
            AppLogger.info("StrongReminderOverlay", "Overlay permission missing, service aborted")
            stopSelf()
            return START_NOT_STICKY
        }

        val event = intent.getStringExtra(EXTRA_EVENT).orEmpty().ifBlank { "DuckTask 提醒" }
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        currentTaskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        currentEvent = event
        currentDescription = description
        currentLogId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        currentNotificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, event.hashCode())
        val waitForUnlock = PermissionUtils.isDeviceLocked(this)

        ServiceCompat.startForeground(
            this,
            currentNotificationId,
            buildForegroundNotification(event, description),
            foregroundServiceType(waitForUnlock)
        )

        if (waitForUnlock) {
            armDeferredOverlay(event)
        } else {
            isWaitingForUnlock = false
            unregisterUnlockReceiverIfNeeded()
            showOverlay(event, description)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        unregisterUnlockReceiverIfNeeded()
        cancelVisualAnimators()
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(event: String, description: String) {
        removeOverlay()
        cancelVisualAnimators()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E60D0D0D"))
            isClickable = true
            isFocusable = true
        }

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

        eventText = TextView(this).apply {
            text = event.ifBlank { "DuckTask 提醒" }
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

        buttonContainer = FrameLayout(this)
        root.addView(buttonContainer, FrameLayout.LayoutParams(dp(240), dp(240)).apply {
            gravity = Gravity.CENTER
            topMargin = dp(60)
        })

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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        runCatching {
            windowManager.addView(root, params)
            overlayView = root

            val cleared = PendingOverlayManager.clearPendingIfMatches(this, currentTaskId, currentLogId)
            if (cleared) {
                AppLogger.info("StrongReminderOverlay", "Cleared matching pending overlay for: $event")
            }
        }.onFailure {
            AppLogger.error("StrongReminderOverlay", "Failed to add overlay view", it)
            stopSelf()
        }

        createRingButton()
        enterIdleState()
    }

    private fun armDeferredOverlay(event: String) {
        removeOverlay()
        isWaitingForUnlock = true
        registerUnlockReceiverIfNeeded()
        startUnlockPolling(attempts = 6, intervalMs = 750L)
        AppLogger.info("StrongReminderOverlay", "Device locked, waiting for unlock for: $event")
    }

    private fun maybeShowDeferredOverlay(source: String): Boolean {
        if (!isWaitingForUnlock) return false
        if (!PermissionUtils.canDrawOverlay(this)) {
            AppLogger.info("StrongReminderOverlay", "Overlay permission missing while waiting for unlock")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return false
        }
        if (PermissionUtils.isDeviceLocked(this)) {
            if (source != "unlock_poll") {
                AppLogger.info(
                    "StrongReminderOverlay",
                    "Received $source but device is still locked for: $currentEvent"
                )
            }
            return false
        }

        isWaitingForUnlock = false
        unregisterUnlockReceiverIfNeeded()
        AppLogger.info("StrongReminderOverlay", "Unlock detected via $source, showing overlay for: $currentEvent")
        showOverlay(currentEvent, currentDescription)
        return true
    }

    private fun startUnlockPolling(attempts: Int, intervalMs: Long) {
        unlockPollAttemptsRemaining = attempts
        unlockPollIntervalMs = intervalMs
        mainHandler.removeCallbacks(unlockCheckRunnable)
        mainHandler.postDelayed(unlockCheckRunnable, intervalMs)
    }

    private fun registerUnlockReceiverIfNeeded() {
        if (unlockReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> maybeShowDeferredOverlay("user_present")
                    Intent.ACTION_SCREEN_ON -> startUnlockPolling(attempts = 20, intervalMs = 300L)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        unlockReceiver = receiver
    }

    private fun unregisterUnlockReceiverIfNeeded() {
        mainHandler.removeCallbacks(unlockCheckRunnable)
        unlockPollAttemptsRemaining = 0
        unlockReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        unlockReceiver = null
    }

    private fun createRingButton() {
        val container = buttonContainer ?: return
        container.removeAllViews()

        ringView = RingProgressView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(ringView)

        val centerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        buttonIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_lock_idle_alarm)
            setColorFilter(Color.WHITE)
            alpha = 0.95f
        }
        centerLayout.addView(buttonIcon, LinearLayout.LayoutParams(dp(52), dp(52)))

        buttonText = TextView(this).apply {
            text = "长按确认"
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 18f
            gravity = Gravity.CENTER
            letterSpacing = 0.06f
            setShadowLayer(dp(10).toFloat(), 0f, 0f, Color.parseColor("#66FFFFFF"))
        }
        centerLayout.addView(buttonText)
        container.addView(centerLayout)

        container.setOnTouchListener { _, event ->
            handleButtonTouch(event)
            true
        }
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
        holdState = HoldState.CHARGING
        holdProgress = 0f
        currentMilestone = 0
        chargedPulse = 0f
        buttonText?.text = "保持按住"
        ringView?.setHoldState(HoldState.CHARGING)
        ringView?.setProgress(0f)
        ringView?.setAccentColor(accentColor)
        ringView?.setChargedPulse(0f)
        ringView?.setBurstProgress(0f)
        applyChargingVisuals(0f)
        animateButtonScale(0.94f, 120L)
        startOrbitAnimator(1_450L)
        stopChargedStateAnimator()
        vibrateTick(20L, 40)
        val startedAt = SystemClock.elapsedRealtime()
        holdRunnable = object : Runnable {
            override fun run() {
                val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(HOLD_DURATION_MS)
                val progress = elapsed.toFloat() / HOLD_DURATION_MS.toFloat()
                holdProgress = progress
                ringView?.setProgress(progress)
                applyChargingVisuals(progress)
                emitChargeMilestone(progress)

                if (elapsed >= HOLD_DURATION_MS) {
                    enterChargedWaitingRelease()
                } else {
                    mainHandler.postDelayed(this, 16)
                }
            }
        }
        mainHandler.post(holdRunnable!!)
    }

    private fun releaseHold() {
        isPointerDown = false
        when (holdState) {
            HoldState.CHARGING -> enterIdleState()
            HoldState.CHARGED_WAITING_RELEASE -> triggerCompletionSequence()
            HoldState.COMPLETING, HoldState.IDLE -> Unit
        }
    }

    private fun enterIdleState() {
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable = null
        holdState = HoldState.IDLE
        holdProgress = 0f
        currentMilestone = 0
        chargedPulse = 0f
        ringView?.setHoldState(HoldState.IDLE)
        ringView?.setProgress(0f)
        ringView?.setAccentColor(accentColor)
        ringView?.setChargedPulse(0f)
        ringView?.setBurstProgress(0f)
        buttonText?.text = "长按确认"
        buttonIcon?.setColorFilter(Color.WHITE)
        startOrbitAnimator(4_200L)
        stopChargedStateAnimator()
        animateButtonScale(1f, 160L)
        applyIdleVisuals()
    }

    private fun enterChargedWaitingRelease() {
        if (holdState == HoldState.CHARGED_WAITING_RELEASE || holdState == HoldState.COMPLETING) return
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable = null
        holdState = HoldState.CHARGED_WAITING_RELEASE
        holdProgress = 1f
        ringView?.setHoldState(HoldState.CHARGED_WAITING_RELEASE)
        ringView?.setProgress(1f)
        ringView?.setAccentColor(armedColor)
        buttonText?.text = "松手确认"
        buttonIcon?.setColorFilter(Color.parseColor("#FFFFF3D6"))
        startOrbitAnimator(900L)
        startChargedStateAnimator()
        animateButtonScale(0.98f, 180L)
        applyChargedWaitingVisuals(0f)
        vibrateTick(40L, 180)
        if (!isPointerDown) {
            triggerCompletionSequence()
        }
    }

    private fun triggerCompletionSequence() {
        if (holdState == HoldState.COMPLETING) return
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable = null
        mainHandler.removeCallbacks(dismissRunnable)
        holdState = HoldState.COMPLETING
        buttonText?.text = "已完成"
        buttonIcon?.setColorFilter(Color.WHITE)
        ringView?.setHoldState(HoldState.COMPLETING)
        ringView?.setAccentColor(successColor)
        stopChargedStateAnimator()
        startOrbitAnimator(700L)
        animateButtonScale(1.1f, 220L)
        vibrateTick(80L, 220)
        completionAnimator?.cancel()
        completionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                ringView?.setBurstProgress(progress)
                applyCompletionVisuals(progress)
            }
            start()
        }
        triggerParticleEffect()
        mainHandler.postDelayed(dismissRunnable, 920L)
    }

    private fun animateButtonScale(targetScale: Float, durationMs: Long) {
        buttonContainer?.animate()
            ?.scaleX(targetScale)
            ?.scaleY(targetScale)
            ?.setDuration(durationMs)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
    }

    private fun applyIdleVisuals() {
        val pulse = ambientPulse
        glowView?.apply {
            scaleX = 1f + pulse * 0.08f
            scaleY = 1f + pulse * 0.08f
            alpha = 0.56f + pulse * 0.14f
        }
        eventText?.apply {
            scaleX = 1f + pulse * 0.01f
            scaleY = 1f + pulse * 0.01f
            alpha = 0.96f
            letterSpacing = 0.03f
        }
        buttonIcon?.apply {
            scaleX = 1f + pulse * 0.03f
            scaleY = 1f + pulse * 0.03f
            alpha = 0.95f
        }
        flashView?.alpha = 0f
    }

    private fun applyChargingVisuals(progress: Float) {
        val pulse = ambientPulse
        glowView?.apply {
            scaleX = 1.04f + progress * 0.34f + pulse * 0.08f
            scaleY = 1.04f + progress * 0.34f + pulse * 0.08f
            alpha = 0.58f + progress * 0.22f + pulse * 0.08f
        }
        eventText?.apply {
            scaleX = 1f + progress * 0.045f
            scaleY = 1f + progress * 0.045f
            alpha = 0.95f + progress * 0.05f
            letterSpacing = 0.03f + progress * 0.03f
        }
        buttonIcon?.apply {
            scaleX = 1f + progress * 0.12f
            scaleY = 1f + progress * 0.12f
            alpha = 0.94f + progress * 0.06f
        }
    }

    private fun applyChargedWaitingVisuals(pulse: Float) {
        chargedPulse = pulse
        ringView?.setChargedPulse(pulse)
        glowView?.apply {
            scaleX = 1.34f + pulse * 0.16f
            scaleY = 1.34f + pulse * 0.16f
            alpha = 0.78f + pulse * 0.16f
        }
        eventText?.apply {
            scaleX = 1.05f + pulse * 0.02f
            scaleY = 1.05f + pulse * 0.02f
            alpha = 1f
            letterSpacing = 0.07f + pulse * 0.02f
        }
        buttonIcon?.apply {
            scaleX = 1.1f + pulse * 0.08f
            scaleY = 1.1f + pulse * 0.08f
            alpha = 1f
        }
    }

    private fun applyCompletionVisuals(progress: Float) {
        ringView?.setChargedPulse(1f - progress * 0.45f)
        glowView?.apply {
            scaleX = 1.46f + progress * 0.48f
            scaleY = 1.46f + progress * 0.48f
            alpha = 0.92f - progress * 0.42f
        }
        eventText?.apply {
            scaleX = 1.07f + progress * 0.06f
            scaleY = 1.07f + progress * 0.06f
            alpha = 1f - progress * 0.28f
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
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val rotation = animator.animatedValue as Float
                ringView?.setOrbitRotation(rotation)
                buttonIcon?.rotation = rotation * 0.32f
            }
            start()
        }
        if (ambientPulseAnimator == null) {
            ambientPulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1_200L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    ambientPulse = animator.animatedValue as Float
                    ringView?.setAmbientPulse(ambientPulse)
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
            interpolator = LinearInterpolator()
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
        ringView?.setChargedPulse(0f)
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
        val root = overlayView ?: return
        val centerX = root.width / 2f
        val centerY = root.height / 2f
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
            root.addView(particle)
            particleViews.add(particle)

            // 动画
            val targetX = centerX + (distance * Math.cos(angle)).toFloat() - size / 2
            val targetY = centerY + (distance * Math.sin(angle)).toFloat() - size / 2

            particle.animate()
                .x(targetX)
                .y(targetY)
                .alpha(0f)
                .scaleX(0.2f)
                .scaleY(0.2f)
                .setDuration(860L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    root.removeView(particle)
                    particleViews.remove(particle)
                }
                .start()
        }

        triggerRippleEffect(delayMs = 0L, startScale = 0.8f, strokeColor = successColor)
        triggerRippleEffect(delayMs = 120L, startScale = 1f, strokeColor = armedColor)
        triggerRippleEffect(delayMs = 240L, startScale = 1.15f, strokeColor = Color.WHITE)
    }

    private fun triggerRippleEffect(delayMs: Long, startScale: Float, strokeColor: Int) {
        val root = overlayView ?: return

        val ripple = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), strokeColor)
            }
        }
        val size = dp(220)
        ripple.layoutParams = FrameLayout.LayoutParams(size, size)
        ripple.x = root.width / 2f - size / 2
        ripple.y = root.height / 2f - size / 2
        ripple.scaleX = startScale
        ripple.scaleY = startScale
        root.addView(ripple)
        particleViews.add(ripple)

        ripple.animate()
            .scaleX(3f)
            .scaleY(3f)
            .alpha(0f)
            .setStartDelay(delayMs)
            .setDuration(1_050L)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                root.removeView(ripple)
                particleViews.remove(ripple)
            }
            .start()
    }

    private fun dismissReminder() {
        mainHandler.removeCallbacksAndMessages(null)
        completionAnimator?.cancel()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(currentNotificationId)
        if (currentLogId > 0) {
            val logId = currentLogId
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val db = AppDatabase.getInstance(applicationContext)
                    db.reminderLogDao()
                        .acknowledge(logId, System.currentTimeMillis(), DISMISS_METHOD_OVERLAY)
                    if (currentTaskId.isNotBlank()) {
                        val task = db.taskDao().getTaskByTaskId(currentTaskId)
                        if (task?.status == TaskStatus.ALERTING) {
                            db.taskDao().updateStatus(currentTaskId, TaskStatus.COMPLETED)
                        }
                    }
                }.onFailure {
                    AppLogger.error("StrongReminderOverlay", "Failed to acknowledge overlay dismissal", it)
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildForegroundNotification(event: String, description: String) =
        NotificationCompat.Builder(this, DuckTaskNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("DuckTask：$event")
            .setContentText(description.ifBlank { "强提醒进行中" })
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    currentNotificationId,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        particleViews.forEach { view ->
            runCatching { (view.parent as? ViewGroup)?.removeView(view) }
        }
        particleViews.clear()
        eventText = null
        glowView = null
        flashView = null
        buttonContainer = null
        ringView = null
        buttonIcon = null
        buttonText = null
        holdRunnable = null
        holdState = HoldState.IDLE
        isPointerDown = false
        holdProgress = 0f
        currentMilestone = 0
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // 环形进度自定义视图
    inner class RingProgressView(context: Context) : View(context) {
        private var progress: Float = 0f
        private var ringColor: Int = accentColor
        private var orbitRotation: Float = 0f
        private var ambientPulse: Float = 0f
        private var chargedPulse: Float = 0f
        private var burstProgress: Float = 0f
        private var holdState: HoldState = HoldState.IDLE

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.STROKE
            strokeWidth = dp(10f)
            color = Color.argb(42, Color.red(ringColor), Color.green(ringColor), Color.blue(ringColor))
            strokeCap = Cap.ROUND
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.STROKE
            strokeWidth = dp(12f)
            color = ringColor
            strokeCap = Cap.ROUND
        }
        private val trailingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.STROKE
            strokeWidth = dp(6f)
            color = Color.WHITE
            strokeCap = Cap.ROUND
        }
        private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.STROKE
            strokeWidth = dp(2f)
            color = Color.argb(110, 255, 255, 255)
            strokeCap = Cap.ROUND
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.FILL
            color = Color.argb(38, Color.red(ringColor), Color.green(ringColor), Color.blue(ringColor))
        }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.FILL
            color = Color.argb(180, Color.red(ringColor), Color.green(ringColor), Color.blue(ringColor))
        }
        private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.FILL
            color = Color.WHITE
        }
        private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.STROKE
            strokeWidth = dp(4f)
            color = Color.WHITE
            strokeCap = Cap.ROUND
        }

        fun setProgress(value: Float) {
            progress = value.coerceIn(0f, 1f)
            invalidate()
        }

        fun setAccentColor(color: Int) {
            ringColor = color
            progressPaint.color = color
            backgroundPaint.color = Color.argb(42, Color.red(color), Color.green(color), Color.blue(color))
            trailingPaint.color = Color.argb(185, 255, 255, 255)
            glowPaint.color = Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))
            corePaint.color = Color.argb(180, Color.red(color), Color.green(color), Color.blue(color))
            invalidate()
        }

        fun setOrbitRotation(value: Float) {
            orbitRotation = value
            invalidate()
        }

        fun setAmbientPulse(value: Float) {
            ambientPulse = value.coerceIn(0f, 1f)
            invalidate()
        }

        fun setChargedPulse(value: Float) {
            chargedPulse = value.coerceIn(0f, 1f)
            invalidate()
        }

        fun setBurstProgress(value: Float) {
            burstProgress = value.coerceIn(0f, 1f)
            invalidate()
        }

        fun setHoldState(value: HoldState) {
            holdState = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = (minOf(width, height) - progressPaint.strokeWidth) / 2f - dp(6f)
            val glowRadius = radius + dp(24f) + chargedPulse * dp(14f)

            glowPaint.alpha = (42 + ambientPulse * 22 + chargedPulse * 66).toInt().coerceAtMost(180)
            canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)
            canvas.drawCircle(centerX, centerY, radius + dp(10f), backgroundPaint)
            drawTickMarks(canvas, centerX, centerY, radius + dp(15f))

            val rect = RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            )
            canvas.drawArc(rect, -90f, 360f * progress, false, progressPaint)
            drawTrailingArc(canvas, rect)
            drawOrbitSparks(canvas, centerX, centerY, radius + dp(6f))
            drawCore(canvas, centerX, centerY, radius * 0.48f)
            drawBurst(canvas, centerX, centerY, radius)
        }

        private fun drawTickMarks(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
            val totalMarks = 48
            for (i in 0 until totalMarks) {
                val markProgress = i / totalMarks.toFloat()
                val isActive = progress >= markProgress
                val alpha = when {
                    holdState == HoldState.CHARGED_WAITING_RELEASE -> (120 + chargedPulse * 110).toInt()
                    holdState == HoldState.COMPLETING -> (200 - burstProgress * 120).toInt()
                    isActive -> 170
                    else -> 42
                }.coerceIn(24, 220)
                glyphPaint.color = Color.argb(alpha, 255, 255, 255)
                val angle = Math.toRadians((i * (360.0 / totalMarks) - 90.0) + orbitRotation * 0.04)
                val inner = radius - if (i % 4 == 0) dp(12f) else dp(7f)
                val outer = radius + if (i % 4 == 0) dp(2f) else dp(0.5f)
                val startX = centerX + cos(angle).toFloat() * inner
                val startY = centerY + sin(angle).toFloat() * inner
                val endX = centerX + cos(angle).toFloat() * outer
                val endY = centerY + sin(angle).toFloat() * outer
                canvas.drawLine(startX, startY, endX, endY, glyphPaint)
            }
        }

        private fun drawTrailingArc(canvas: Canvas, rect: RectF) {
            if (progress <= 0f) return
            val sweep = 48f + progress * 38f
            trailingPaint.alpha = (80 + progress * 110).toInt().coerceAtMost(190)
            canvas.drawArc(rect, -90f + (360f * progress) - sweep, sweep, false, trailingPaint)
            if (holdState == HoldState.CHARGED_WAITING_RELEASE) {
                trailingPaint.alpha = (120 + chargedPulse * 90).toInt().coerceAtMost(220)
                canvas.drawArc(rect, orbitRotation - 110f, 76f, false, trailingPaint)
                canvas.drawArc(rect, -orbitRotation - 40f, 44f, false, trailingPaint)
            }
        }

        private fun drawOrbitSparks(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
            val sparkCount = if (holdState == HoldState.CHARGED_WAITING_RELEASE) 8 else 5
            for (i in 0 until sparkCount) {
                val angleDegrees = orbitRotation + i * (360f / sparkCount)
                val angle = Math.toRadians(angleDegrees.toDouble())
                val sparkRadius = radius + if (i % 2 == 0) dp(6f) else dp(13f)
                val x = centerX + cos(angle).toFloat() * sparkRadius
                val y = centerY + sin(angle).toFloat() * sparkRadius
                val size = if (holdState == HoldState.CHARGED_WAITING_RELEASE) dp(4f + chargedPulse * 3f) else dp(3f + progress * 2f)
                val alpha = when (holdState) {
                    HoldState.CHARGING -> (90 + progress * 120).toInt()
                    HoldState.CHARGED_WAITING_RELEASE -> (150 + chargedPulse * 90).toInt()
                    HoldState.COMPLETING -> (220 - burstProgress * 140).toInt()
                    HoldState.IDLE -> (70 + ambientPulse * 60).toInt()
                }.coerceIn(50, 230)
                sparkPaint.color = Color.argb(alpha, 255, 255, 255)
                canvas.drawCircle(x, y, size, sparkPaint)
            }
        }

        private fun drawCore(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
            val pulseScale = when (holdState) {
                HoldState.IDLE -> 0.88f + ambientPulse * 0.08f
                HoldState.CHARGING -> 0.9f + progress * 0.15f + ambientPulse * 0.05f
                HoldState.CHARGED_WAITING_RELEASE -> 1f + chargedPulse * 0.12f
                HoldState.COMPLETING -> 1.08f + burstProgress * 0.22f
            }
            corePaint.alpha = when (holdState) {
                HoldState.IDLE -> 110
                HoldState.CHARGING -> (130 + progress * 70).toInt()
                HoldState.CHARGED_WAITING_RELEASE -> (170 + chargedPulse * 60).toInt()
                HoldState.COMPLETING -> (210 - burstProgress * 110).toInt()
            }.coerceIn(96, 220)
            canvas.drawCircle(centerX, centerY, radius * pulseScale, corePaint)

            sparkPaint.color = Color.argb(
                if (holdState == HoldState.COMPLETING) 255 else 200,
                255,
                255,
                255
            )
            canvas.drawCircle(centerX, centerY, radius * 0.35f * (0.96f + ambientPulse * 0.04f), sparkPaint)
        }

        private fun drawBurst(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
            if (burstProgress <= 0f) return
            burstPaint.alpha = (220 - burstProgress * 180).toInt().coerceAtLeast(0)
            val firstRadius = radius + dp(10f) + burstProgress * dp(90f)
            val secondRadius = radius + dp(2f) + burstProgress * dp(150f)
            canvas.drawCircle(centerX, centerY, firstRadius, burstPaint)
            burstPaint.alpha = (160 - burstProgress * 140).toInt().coerceAtLeast(0)
            canvas.drawCircle(centerX, centerY, secondRadius, burstPaint)
        }

        private fun dp(value: Float): Float {
            return value * resources.displayMetrics.density
        }
    }

    companion object {
        const val DISMISS_METHOD_OVERLAY = "overlay"
        private const val HOLD_DURATION_MS = 3_000L
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_EVENT = "event"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun startIfPossible(context: Context, task: Task, logId: Long): Boolean {
            return startService(
                context = context,
                taskId = task.taskId,
                event = task.event,
                description = task.description,
                logId = logId,
                notificationId = task.taskId.hashCode()
            )
        }

        fun startPendingIfPossible(context: Context, pending: PendingOverlayPayload): Boolean {
            return startService(
                context = context,
                taskId = pending.taskId,
                event = pending.event,
                description = pending.description,
                logId = pending.logId,
                notificationId = pending.notificationId
            )
        }

        private fun startService(
            context: Context,
            taskId: String,
            event: String,
            description: String,
            logId: Long,
            notificationId: Int
        ): Boolean {
            if (!PermissionUtils.canDrawOverlay(context)) {
                return false
            }
            val intent = Intent(context, StrongReminderOverlayService::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_EVENT, event)
                .putExtra(EXTRA_DESCRIPTION, description)
                .putExtra(EXTRA_LOG_ID, logId)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return runCatching {
                DuckTaskNotifications.ensureChannel(context)
                ContextCompat.startForegroundService(context, intent)
                true
            }.onFailure {
                AppLogger.error("StrongReminderOverlay", "Failed to start overlay service", it)
            }.getOrDefault(false)
        }

        private fun foregroundServiceType(waitForUnlock: Boolean): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (waitForUnlock) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                }
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            }
        }
    }
}
