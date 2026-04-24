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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.ui.theme.DuckTaskTheme
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
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        val event = intent.getStringExtra(EXTRA_EVENT).orEmpty()
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        setContent {
            DuckTaskTheme(dynamicColor = false) {
                StrongReminderScreen(
                    event = event,
                    description = description,
                    onDismiss = {
                        acknowledgeAndFinish(logId, notificationId)
                    }
                )
            }
        }
    }

    private fun acknowledgeAndFinish(logId: Long, notificationId: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(notificationId)

        if (logId > 0) {
            val appContext = applicationContext
            val db = AppDatabase.getInstance(appContext)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                db.reminderLogDao().acknowledge(logId, System.currentTimeMillis(), DISMISS_METHOD_POPUP)
            }
        }
        finish()
    }

    companion object {
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
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "强提醒",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = event.ifBlank { "DuckTask 提醒" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        hint = "保持按住，3 秒后解除"
                        progressJob?.cancel()
                        progressJob = scope.launch {
                            val steps = 30
                            repeat(steps) { index ->
                                delay(100)
                                progress = (index + 1) / steps.toFloat()
                            }
                            onDismiss()
                        }
                        val released = tryAwaitRelease()
                        if (released) {
                            progressJob?.cancel()
                            progress = 0f
                            hint = "按住时间不足，请继续长按"
                        }
                    }
                )
            },
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = hint,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )
        }
    }
}
