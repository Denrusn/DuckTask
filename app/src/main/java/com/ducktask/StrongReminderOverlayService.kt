package com.ducktask.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.Task
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.notification.DuckTaskNotifications
import com.ducktask.app.util.AppLogger
import com.ducktask.app.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StrongReminderOverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var progressBar: ProgressBar? = null
    private var countdownText: TextView? = null
    private var hintView: TextView? = null
    private var holdRunnable: Runnable? = null
    private var dismissing = false
    private var currentTaskId: String = ""
    private var currentLogId: Long = -1L
    private var currentNotificationId: Int = -1

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
        currentLogId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        currentNotificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, event.hashCode())
        dismissing = false

        ServiceCompat.startForeground(
            this,
            currentNotificationId,
            buildForegroundNotification(event, description),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        )
        showOverlay(event, description)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(event: String, description: String) {
        removeOverlay()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#B8140B06"))
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30).toFloat()
                setColor(Color.parseColor("#FFF7F2"))
                setStroke(dp(1), Color.parseColor("#33FF6B35"))
            }
            elevation = dp(10).toFloat()
        }

        // Header pill
        card.addView(
            pillText(
                text = "强提醒进行中",
                backgroundColor = Color.parseColor("#1FF44336"),
                textColor = Color.parseColor("#FFF44336")
            )
        )

        // Large countdown display
        countdownText = TextView(this).apply {
            text = "3"
            setTextColor(Color.parseColor("#FFFF6B35"))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 80f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(16), 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }.also(card::addView)

        // Event title
        card.addView(
            textView(
                text = event,
                textSizeSp = 26f,
                textColor = Color.parseColor("#FF181310"),
                isBold = true,
                gravity = Gravity.CENTER_HORIZONTAL,
                topMarginDp = 8
            )
        )

        // Description if available
        if (description.isNotBlank()) {
            card.addView(
                capsuleText(
                    text = description,
                    backgroundColor = Color.parseColor("#14FFD60A"),
                    textColor = Color.parseColor("#FF2F251B"),
                    topMarginDp = 16
                )
            )
        }

        // Instruction text
        card.addView(
            capsuleText(
                text = "长按下方按钮 3 秒后才可解除，松手会重新计时。",
                backgroundColor = Color.parseColor("#14FFB703"),
                textColor = Color.parseColor("#FF5F3A0B"),
                topMarginDp = 16
            )
        )

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = HOLD_DURATION_MS.toInt()
            progress = 0
            progressDrawable.setTint(Color.parseColor("#FFFF6B35"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(10)
            ).apply {
                topMargin = dp(16)
            }
        }.also(card::addView)

        // Hint text
        hintView = textView(
            text = "长按 3 秒解除提醒",
            textSizeSp = 18f,
            textColor = Color.parseColor("#FF1E1712"),
            isBold = true,
            gravity = Gravity.CENTER_HORIZONTAL,
            topMarginDp = 12
        ).also(card::addView)

        // Dismiss button
        val button = TextView(this).apply {
            text = "按住解除"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 17f
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                colors = intArrayOf(Color.parseColor("#FFFF6B35"), Color.parseColor("#FFFFA62B"))
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
            setOnTouchListener(::handleHoldTouch)
        }
        card.addView(button)

        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                leftMargin = dp(20)
                rightMargin = dp(20)
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        runCatching {
            windowManager.addView(root, params)
            overlayView = root
        }.onFailure {
            AppLogger.error("StrongReminderOverlay", "Failed to add overlay view", it)
            stopSelf()
        }
    }

    private fun handleHoldTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startHold()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelHold()
        }
        return true
    }

    private fun startHold() {
        dismissing = false
        hintView?.text = "保持按住，3 秒后解除"
        progressBar?.progress = 0
        mainHandler.removeCallbacksAndMessages(null)
        val startedAt = SystemClock.elapsedRealtime()
        holdRunnable = object : Runnable {
            override fun run() {
                val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(HOLD_DURATION_MS)
                progressBar?.progress = elapsed.toInt()
                // Update countdown display (3, 2, 1)
                val remaining = ((HOLD_DURATION_MS - elapsed) / 1000).toInt() + 1
                countdownText?.text = when {
                    remaining >= 3 -> "3"
                    remaining >= 2 -> "2"
                    remaining >= 1 -> "1"
                    else -> "0"
                }
                if (elapsed >= HOLD_DURATION_MS) {
                    dismissing = true
                    hintView?.text = "提醒已解除"
                    countdownText?.text = "0"
                    dismissReminder()
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
        progressBar?.progress = 0
        hintView?.text = "按住时间不足，请继续长按"
        countdownText?.text = "3"
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
                        .acknowledge(logId, System.currentTimeMillis(), StrongReminderActivity.DISMISS_METHOD_POPUP)
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
            overlayView = null
        }
    }

    private fun pillText(
        text: String,
        backgroundColor: Int,
        textColor: Int
    ): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 13f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(backgroundColor)
            }
        }
    }

    private fun capsuleText(
        text: String,
        backgroundColor: Int,
        textColor: Int,
        topMarginDp: Int
    ): TextView {
        return textView(
            text = text,
            textSizeSp = 15f,
            textColor = textColor,
            gravity = Gravity.CENTER_HORIZONTAL,
            topMarginDp = topMarginDp,
            horizontalPaddingDp = 16,
            verticalPaddingDp = 14,
            backgroundColor = backgroundColor,
            cornerRadiusDp = 20
        )
    }

    private fun textView(
        text: String,
        textSizeSp: Float,
        textColor: Int,
        isBold: Boolean = false,
        gravity: Int = Gravity.START,
        topMarginDp: Int = 0,
        horizontalPaddingDp: Int = 0,
        verticalPaddingDp: Int = 0,
        backgroundColor: Int? = null,
        cornerRadiusDp: Int = 0
    ): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = textSizeSp
            this.gravity = gravity
            if (isBold) setTypeface(typeface, Typeface.BOLD)
            if (horizontalPaddingDp > 0 || verticalPaddingDp > 0) {
                setPadding(
                    dp(horizontalPaddingDp),
                    dp(verticalPaddingDp),
                    dp(horizontalPaddingDp),
                    dp(verticalPaddingDp)
                )
            }
            if (backgroundColor != null) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(cornerRadiusDp).toFloat()
                    setColor(backgroundColor)
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(topMarginDp)
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val HOLD_DURATION_MS = 3_000L
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_DESCRIPTION = "description"
        private const val EXTRA_LOG_ID = "log_id"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun startIfPossible(context: android.content.Context, task: Task, logId: Long): Boolean {
            if (!PermissionUtils.canDrawOverlay(context) || PermissionUtils.isDeviceLocked(context)) {
                return false
            }
            val notificationId = task.taskId.hashCode()
            val intent = Intent(context, StrongReminderOverlayService::class.java)
                .putExtra(EXTRA_TASK_ID, task.taskId)
                .putExtra(EXTRA_EVENT, task.event)
                .putExtra(EXTRA_DESCRIPTION, task.description)
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
    }
}
