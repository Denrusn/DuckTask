package com.ducktask.app

import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.ui.theme.DuckOrange
import com.ducktask.app.ui.theme.DuckTaskTheme

class StrongReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        val event = intent.getStringExtra(EXTRA_EVENT).orEmpty()
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        setContent {
            DuckTaskTheme(dynamicColor = false) {
                StrongReminderScreen(
                    event = event,
                    description = description,
                    onDismiss = {
                        acknowledgeAndFinish(taskId, logId, notificationId)
                    }
                )
            }
        }
    }

    private fun acknowledgeAndFinish(taskId: String, logId: Long, notificationId: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(notificationId)

        val appContext = applicationContext
        val db = AppDatabase.getInstance(appContext)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            if (logId > 0) {
                db.reminderLogDao().acknowledge(logId, System.currentTimeMillis(), DISMISS_METHOD_POPUP)
            }
            if (taskId.isNotBlank()) {
                val task = db.taskDao().getTaskByTaskId(taskId)
                if (task?.status == TaskStatus.ALERTING) {
                    db.taskDao().updateStatus(taskId, TaskStatus.COMPLETED)
                }
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_EVENT = "event"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val DISMISS_METHOD_POPUP = "popup"
    }
}

@Composable
private fun StrongReminderScreen(
    event: String,
    description: String,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { }

    var showRipple by remember { mutableStateOf(false) }
    val rippleColor = DuckOrange
    val successGreen = Color(0xFF4CAF50)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D),
                        Color(0xFF1A1A1A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 发光背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(rippleColor.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        // 脉冲波纹层
        if (showRipple) {
            PulseRippleEffect(
                isActive = showRipple,
                color = rippleColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            // 事件名称 - 大字体居中
            Text(
                text = event.ifBlank { "DuckTask 提醒" },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            // 环形填充按钮
            RingFillDismissButton(
                onDismiss = {
                    showRipple = true
                    kotlinx.coroutines.delay(500)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun RingFillDismissButton(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressJob by remember { mutableStateOf<Job?>(null) }

    // 环形进度动画
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 50, easing = LinearEasing),
        label = "ringProgress"
    )

    // 按钮缩放动画
    val buttonScale by animateFloatAsState(
        targetValue = when {
            isCompleted -> 1.08f
            isHolding -> 0.94f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 120),
        label = "buttonScale"
    )

    // 成功绿色
    val successGreen = Color(0xFF4CAF50)
    val ringColor = if (isCompleted) successGreen else DuckOrange

    Box(
        modifier = Modifier
            .size(220.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        isCompleted = false
                        progressJob?.cancel()

                        // 开始环形填充
                        progressJob = scope.launch {
                            repeat(30) { step ->
                                delay(100)
                                progress = (step + 1) / 30f
                            }
                            // 完成！
                            isCompleted = true
                            delay(350)
                            onDismiss()
                        }

                        val released = tryAwaitRelease()
                        isHolding = false
                        if (released && !isCompleted) {
                            progressJob?.cancel()
                            progress = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 背景光晕
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ringColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 环形进度（Canvas 绘制）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = this.center

            // 背景圆环
            drawCircle(
                color = ringColor.copy(alpha = 0.2f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // 进度圆弧
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    center.x - radius,
                    center.y - radius
                ),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }

        // 中心内容
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.TouchApp,
                contentDescription = null,
                tint = if (isCompleted) successGreen else ringColor,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = when {
                    isCompleted -> "完成"
                    isHolding -> "保持"
                    else -> "长按解锁"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun PulseRippleEffect(
    isActive: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    var rippleScale by remember { mutableFloatStateOf(0f) }
    var rippleAlpha by remember { mutableFloatStateOf(0f) }

    val animatedScale by animateFloatAsState(
        targetValue = rippleScale,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "rippleScale"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = rippleAlpha,
        animationSpec = tween(durationMillis = 1000),
        label = "rippleAlpha"
    )

    LaunchedEffect(isActive) {
        if (isActive) {
            rippleScale = 0f
            rippleAlpha = 1f
            // 快速扩散
            rippleScale = 1f
            rippleAlpha = 0f
        }
    }

    if (animatedAlpha > 0.01f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2 * animatedScale

            // 外圈波纹
            drawCircle(
                color = color.copy(alpha = animatedAlpha * 0.3f),
                radius = maxRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // 内圈波纹（延迟效果）
            if (animatedScale > 0.3f) {
                drawCircle(
                    color = color.copy(alpha = animatedAlpha * 0.2f),
                    radius = maxRadius * 0.7f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
