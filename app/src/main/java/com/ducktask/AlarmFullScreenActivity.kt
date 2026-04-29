package com.ducktask.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmFullScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_EVENT = "event"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_RINGTONE = "ringtone"
        const val EXTRA_VIBRATE_COUNT = "vibrate_count"
        const val EXTRA_LOG_ID = "log_id"

        fun createIntent(
            context: Context,
            taskId: String,
            event: String,
            description: String,
            ringtone: Boolean,
            vibrateCount: Int,
            logId: Long
        ): Intent {
            return Intent(context, AlarmFullScreenActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_EVENT, event)
                putExtra(EXTRA_DESCRIPTION, description)
                putExtra(EXTRA_RINGTONE, ringtone)
                putExtra(EXTRA_VIBRATE_COUNT, vibrateCount)
                putExtra(EXTRA_LOG_ID, logId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    private var taskId: String = ""
    private var logId: Long = -1
    private var ringtoneEnabled: Boolean = true
    private var vibrateCount: Int = 5
    private var ringtone: android.media.Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 提取 Intent 数据
        taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: ""
        val event = intent.getStringExtra(EXTRA_EVENT) ?: "提醒"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        ringtoneEnabled = intent.getBooleanExtra(EXTRA_RINGTONE, true)
        vibrateCount = intent.getIntExtra(EXTRA_VIBRATE_COUNT, 5)
        logId = intent.getLongExtra(EXTRA_LOG_ID, -1)

        // 亮屏配置
        setupWindowFlags()

        // 设置内容视图
        setContentView(R.layout.activity_alarm_full_screen)

        // 绑定按钮点击
        findViewById<android.widget.Button>(R.id.btnComplete).setOnClickListener { onCompleteClicked() }
        findViewById<android.widget.Button>(R.id.btnSnooze).setOnClickListener { onSnoozeClicked() }

        // 设置显示内容
        findViewById<android.widget.TextView>(R.id.textEvent)?.text = event
        findViewById<android.widget.TextView>(R.id.textDescription)?.text = description
    }

    private fun setupWindowFlags() {
        // 亮屏
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // 全屏沉浸
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        startAlarmFeedback()
    }

    override fun onStop() {
        super.onStop()
        stopAlarmFeedback()
    }

    private fun startAlarmFeedback() {
        // 播放铃声
        if (ringtoneEnabled) {
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
                ringtone?.play()
            } catch (e: Exception) {
                AppLogger.error("AlarmFullScreen", "Failed to play ringtone", e)
            }
        }

        // 震动
        startVibration(vibrateCount)
    }

    private fun stopAlarmFeedback() {
        ringtone?.stop()
        ringtone = null

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.cancel()
    }

    private fun startVibration(count: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        val pattern = LongArray(count * 2 + 1) { if (it == 0) 0 else if (it % 2 == 1) 500 else 500 }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    fun onCompleteClicked() {
        stopAlarmFeedback()
        markTaskCompleted()
        finish()
    }

    fun onSnoozeClicked() {
        stopAlarmFeedback()
        snoozeTask(5) // 稍后5分钟
        finish()
    }

    private fun markTaskCompleted() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                db.taskDao().updateStatus(taskId, TaskStatus.COMPLETED)
                if (logId > 0) {
                    db.reminderLogDao().acknowledge(logId, System.currentTimeMillis(), "alarm_complete")
                }
            } catch (e: Exception) {
                AppLogger.error("AlarmFullScreen", "Failed to mark task completed", e)
            }
        }
    }

    private fun snoozeTask(minutes: Int) {
        // 推迟提醒逻辑将在后续实现
        AppLogger.info("AlarmFullScreen", "Snooze requested for $minutes minutes, taskId: $taskId")
    }
}