package com.ducktask.app

import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.ui.theme.DuckOrange
import com.ducktask.app.ui.theme.DuckTaskTheme
import com.ducktask.app.ui.theme.DuckYellow
import com.ducktask.app.ui.theme.Error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B0E09),
                        Color(0xFF2A1208),
                        Color(0xFF090909)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(DuckOrange.copy(alpha = 0.34f), Color.Transparent)
                    )
                )
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
            border = BorderStroke(1.dp, DuckOrange.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Error.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Error)
                        Text(
                            text = "强提醒进行中",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Error
                        )
                    }
                }
                Text(
                    text = "请立即处理",
                    style = MaterialTheme.typography.titleMedium,
                    color = DuckOrange,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.ifBlank { "DuckTask 提醒" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                if (description.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = description,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DuckYellow.copy(alpha = 0.12f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "操作说明",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = DuckOrange
                        )
                        Text(
                            text = "长按下方按钮 3 秒后才可解除，松手会重新计时。",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                        )
                    }
                }
                LongPressDismissButton(onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun LongPressDismissButton(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var hint by remember { mutableStateOf("长按 3 秒解除提醒") }
    var progressJob by remember { mutableStateOf<Job?>(null) }
    var completed by remember { mutableStateOf(false) }
    var isHolding by remember { mutableStateOf(false) }

    // Scale animation for button press effect
    val buttonScale by animateFloatAsState(
        targetValue = if (isHolding) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "buttonScale"
    )

    // Success green color
    val successGreen = Color(0xFF4CAF50)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        completed = false
                        hint = "保持按住，3 秒后解除"
                        progressJob?.cancel()
                        progressJob = scope.launch {
                            val steps = 30
                            repeat(steps) { index ->
                                delay(100)
                                progress = (index + 1) / steps.toFloat()
                            }
                            completed = true
                            hint = "提醒即将解除"
                            onDismiss()
                        }
                        val released = tryAwaitRelease()
                        isHolding = false
                        if (released && !completed) {
                            progressJob?.cancel()
                            progress = 0f
                            hint = "按住时间不足，请继续长按"
                        }
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = if (completed) 2.dp else 1.dp,
            color = if (completed) successGreen else DuckOrange.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = if (completed) {
                            listOf(successGreen.copy(alpha = 0.24f), successGreen.copy(alpha = 0.16f))
                        } else {
                            listOf(
                                DuckOrange.copy(alpha = 0.16f),
                                DuckYellow.copy(alpha = 0.16f)
                            )
                        }
                    )
                )
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (completed) Icons.Default.Check else Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = if (completed) successGreen else DuckOrange,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (completed) successGreen else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (completed) "已完成" else "松手会中断进度并重新开始",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = if (completed) successGreen else DuckOrange
            )
        }
    }
}
