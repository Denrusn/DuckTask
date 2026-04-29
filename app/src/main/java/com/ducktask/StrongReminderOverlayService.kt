package com.ducktask.app

import android.animation.ObjectAnimator
import android.view.animation.AccelerateInterpolator
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

class StrongReminderOverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager

    // UI 组件
    private var overlayView: FrameLayout? = null
    private var eventText: TextView? = null
    private var glowView: View? = null
    private var buttonContainer: FrameLayout? = null
    private var ringView: RingProgressView? = null
    private var buttonText: TextView? = null
    private var particleViews = mutableListOf<View>()

    // 状态
    private var holdRunnable: Runnable? = null
    private var dismissing = false
    private var currentTaskId: String = ""
    private var currentEvent: String = ""
    private var currentDescription: String = ""
    private var currentLogId: Long = -1L
    private var currentNotificationId: Int = -1
    private var isWaitingForUnlock = false
    private var unlockReceiver: BroadcastReceiver? = null
    private var unlockPollAttemptsRemaining = 0
    private var unlockPollIntervalMs = 500L
    private val unlockCheckRunnable = Runnable {
        if (!isWaitingForUnlock) return@Runnable
        val shown = maybeShowDeferredOverlay("unlock_poll")
        if (!shown && unlockPollAttemptsRemaining > 0) {
            unlockPollAttemptsRemaining -= 1
            mainHandler.postDelayed(unlockCheckRunnable, unlockPollIntervalMs)
        }
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
        dismissing = false
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
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(event: String, description: String) {
        removeOverlay()

        // 根布局 - 深色背景
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E60D0D0D"))
            isClickable = true
            isFocusable = true
        }

        // 发光背景
        glowView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.parseColor("#26FF6B35"), Color.TRANSPARENT)
            }
        }
        root.addView(glowView, FrameLayout.LayoutParams(dp(400), dp(400)).apply {
            gravity = Gravity.CENTER
        })

        // 事件名称
        eventText = TextView(this).apply {
            text = event.ifBlank { "DuckTask 提醒" }
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 32f
            gravity = Gravity.CENTER
        }
        root.addView(eventText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            topMargin = -dp(180)
        })

        // 环形按钮容器
        buttonContainer = FrameLayout(this)
        root.addView(buttonContainer, FrameLayout.LayoutParams(dp(220), dp(220)).apply {
            gravity = Gravity.CENTER
            topMargin = dp(60)
        })

        // 绑定窗口
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

        // 创建环形按钮
        createRingButton()
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

        // 环形进度视图
        ringView = RingProgressView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(ringView)

        // 中心内容
        val centerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        // 图标
        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setColorFilter(Color.WHITE)
        }
        centerLayout.addView(iconView, LinearLayout.LayoutParams(dp(48), dp(48)))

        // 文字
        buttonText = TextView(this).apply {
            text = "长按解锁"
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 18f
            gravity = Gravity.CENTER
        }
        centerLayout.addView(buttonText)
        container.addView(centerLayout)

        // 触摸监听
        container.setOnTouchListener { _, event ->
            handleButtonTouch(event)
            true
        }
    }

    private fun handleButtonTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startHold()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelHold()
        }
    }

    private fun startHold() {
        dismissing = false
        buttonText?.text = "保持"
        ringView?.setProgress(0f)
        ringView?.setColor(Color.parseColor("#FFFF6B35"))
        mainHandler.removeCallbacksAndMessages(null)

        // 按钮缩放动画
        buttonContainer?.let { container ->
            ObjectAnimator.ofFloat(container, "scaleX", 1f, 0.94f).apply {
                duration = 120
                start()
            }
            ObjectAnimator.ofFloat(container, "scaleY", 1f, 0.94f).apply {
                duration = 120
                start()
            }
        }

        val startedAt = SystemClock.elapsedRealtime()
        holdRunnable = object : Runnable {
            override fun run() {
                val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(HOLD_DURATION_MS)
                val progress = elapsed.toFloat() / HOLD_DURATION_MS.toFloat()
                ringView?.setProgress(progress)

                if (elapsed >= HOLD_DURATION_MS) {
                    dismissing = true
                    onComplete()
                } else {
                    mainHandler.postDelayed(this, 16)
                }
            }
        }
        mainHandler.post(holdRunnable!!)
    }

    private fun cancelHold() {
        if (dismissing) return
        mainHandler.removeCallbacksAndMessages(null)
        ringView?.setProgress(0f)
        buttonText?.text = "长按解锁"

        // 恢复按钮大小
        buttonContainer?.let { container ->
            ObjectAnimator.ofFloat(container, "scaleX", container.scaleX, 1f).apply {
                duration = 120
                start()
            }
            ObjectAnimator.ofFloat(container, "scaleY", container.scaleY, 1f).apply {
                duration = 120
                start()
            }
        }
    }

    private fun onComplete() {
        // 变绿
        ringView?.setColor(Color.parseColor("#FF4CAF50"))
        buttonText?.text = "完成"

        // 放大按钮
        buttonContainer?.let { container ->
            ObjectAnimator.ofFloat(container, "scaleX", 0.94f, 1.08f).apply {
                duration = 200
                start()
            }
            ObjectAnimator.ofFloat(container, "scaleY", 0.94f, 1.08f).apply {
                duration = 200
                start()
            }
        }

        // 触发粒子效果
        triggerParticleEffect()

        // 延迟关闭
        mainHandler.postDelayed({
            dismissReminder()
        }, 600)
    }

    private fun triggerParticleEffect() {
        val root = overlayView ?: return
        val centerX = root.width / 2f
        val centerY = root.height / 2f
        val particleCount = 16

        for (i in 0 until particleCount) {
            val angle = (i * 22.5f - 90f) * Math.PI / 180.0
            val distance = (100 + Math.random() * 80).toFloat()
            val size = (12 + Math.random() * 16).toFloat()

            val particle = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FF4CAF50"))
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
                .scaleX(0.3f)
                .scaleY(0.3f)
                .setDuration(700L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    root.removeView(particle)
                    particleViews.remove(particle)
                }
                .start()
        }

        // 触发波纹效果
        triggerRippleEffect()
    }

    private fun triggerRippleEffect() {
        val root = overlayView ?: return

        val ripple = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), Color.parseColor("#80FF6B35"))
            }
        }
        val size = dp(220)
        ripple.layoutParams = FrameLayout.LayoutParams(size, size)
        ripple.x = root.width / 2f - size / 2
        ripple.y = root.height / 2f - size / 2
        root.addView(ripple)
        particleViews.add(ripple)

        ripple.animate()
            .scaleX(3f)
            .scaleY(3f)
            .alpha(0f)
            .setDuration(1000L)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                root.removeView(ripple)
                particleViews.remove(ripple)
            }
            .start()
    }

    private fun dismissReminder() {
        mainHandler.removeCallbacksAndMessages(null)

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
        buttonContainer = null
        ringView = null
        buttonText = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // 环形进度自定义视图
    inner class RingProgressView(context: Context) : View(context) {
        private var progress: Float = 0f
        private var ringColor: Int = Color.parseColor("#FFFF6B35")

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.STROKE
            strokeWidth = dp(10f)
            color = Color.argb(51, Color.red(ringColor), Color.green(ringColor), Color.blue(ringColor))
            strokeCap = Cap.ROUND
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.STROKE
            strokeWidth = dp(10f)
            color = ringColor
            strokeCap = Cap.ROUND
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Style.FILL
            color = Color.argb(38, Color.red(ringColor), Color.green(ringColor), Color.blue(ringColor))
        }

        fun setProgress(value: Float) {
            progress = value.coerceIn(0f, 1f)
            invalidate()
        }

        fun setColor(color: Int) {
            ringColor = color
            progressPaint.color = color
            backgroundPaint.color = Color.argb(51, Color.red(color), Color.green(color), Color.blue(color))
            glowPaint.color = Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = (minOf(width, height) - backgroundPaint.strokeWidth) / 2f

            // 背景光晕
            canvas.drawCircle(centerX, centerY, radius + dp(20f), glowPaint)

            // 背景圆环
            canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

            // 进度圆弧
            val rect = RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            )
            canvas.drawArc(rect, -90f, 360f * progress, false, progressPaint)
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
