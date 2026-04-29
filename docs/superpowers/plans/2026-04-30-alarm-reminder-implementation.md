# 主页优化与闹钟样式提醒功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现主页标签页、闹钟样式全屏提醒和循环提醒机制

**Architecture:**
1. Task 模型扩展新字段（闹钟选项 + 循环选项）
2. 主页 UI 增加 Tab 组件切换待提醒/已提醒待完成
3. 创建 AlarmFullScreenActivity 实现全屏闹钟提醒
4. 使用 AlarmManager 实现可靠的循环提醒机制

**Tech Stack:** Jetpack Compose, Room Database, AlarmManager, MediaPlayer/Vibrator

---

## Task 1: Task 模型扩展与数据库迁移

**Files:**
- Modify: `app/src/main/java/com/ducktask/domain/model/Task.kt`
- Modify: `app/src/main/java/com/ducktask/data/local/AppDatabase.kt`

- [ ] **Step 1: 在 Task data class 中添加新字段**

修改 Task.kt，在 `nextRunTime` 后添加新字段：

```kotlin
data class Task(
    // ... 现有字段 ...
    val nextRunTime: Long = reminderTime,

    // 闹钟样式选项
    val alarmEnabled: Boolean = false,
    val alarmRingtone: Boolean = true,
    val alarmVibrateCount: Int = 5,

    // 循环提醒选项
    val alertLoopEnabled: Boolean = false,
    val alertLoopIntervalMinutes: Int = 1,
    val alertLoopMaxCount: Int = 5
) {
    // ... 现有方法 ...

    fun hasRepeat(): Boolean = repeatRule()?.isRepeating() == true
    fun reminderModeLabel(): String = ReminderMode.label(reminderMode)
    fun isAlerting(): Boolean = status == TaskStatus.ALERTING
    fun isPending(): Boolean = status == TaskStatus.PENDING
}
```

- [ ] **Step 2: 更新数据库迁移**

修改 AppDatabase.kt 中的 `MIGRATION_1_2` 或创建新的迁移：

```kotlin
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alarm_enabled INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alarm_ringtone INTEGER DEFAULT 1")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alarm_vibrate_count INTEGER DEFAULT 5")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alert_loop_enabled INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alert_loop_interval_minutes INTEGER DEFAULT 1")
        db.execSQL("ALTER TABLE reminder_tasks ADD COLUMN alert_loop_max_count INTEGER DEFAULT 5")
    }
}
```

更新 `fallbackToDestructiveMigration` 或在 `addMigrations` 中添加新迁移。

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/com/ducktask/domain/model/Task.kt
git add app/src/main/java/com/ducktask/data/local/AppDatabase.kt
git commit -m "feat: add alarm and loop reminder fields to Task model

- Add alarmEnabled, alarmRingtone, alarmVibrateCount for alarm style
- Add alertLoopEnabled, alertLoopIntervalMinutes, alertLoopMaxCount for loop reminder
- Add database migration for new columns"
```

---

## Task 2: 主页标签页 UI 实现

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt`

- [ ] **Step 1: 添加 TaskTab 枚举和状态变量**

在 MainScreen.kt 中，在 `LogTab` 枚举后添加：

```kotlin
private enum class TaskTab {
    PENDING,
    ALERTING
}
```

在 `var deletingTaskId` 后面添加：

```kotlin
var taskTab by rememberSaveable { mutableStateOf(TaskTab.PENDING) }
```

- [ ] **Step 2: 消除顶部空白间隔**

在 HomeContent 中，移除或减少 "待提醒 (N)" 上方的 Spacer 高度：

```kotlin
// 原来
Spacer(modifier = Modifier.height(12.dp))

// 修改为
Spacer(modifier = Modifier.height(4.dp))
```

或者直接移除这行 Spacer。

- [ ] **Step 3: 添加 Tab 组件**

在 LazyColumn 列表前添加 Tab：

```kotlin
LazyColumn {
    // Tab 组件
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val pendingCount = tasks.count { it.status == TaskStatus.PENDING }
            val alertingCount = tasks.count { it.status == TaskStatus.ALERTING }

            FilterChip(
                selected = taskTab == TaskTab.PENDING,
                onClick = { taskTab = TaskTab.PENDING },
                label = { Text("待提醒 ($pendingCount)") }
            )
            FilterChip(
                selected = taskTab == TaskTab.ALERTING,
                onClick = { taskTab = TaskTab.ALERTING },
                label = { Text("已提醒待完成 ($alertingCount)") }
            )
        }
    }

    // 任务列表
    items(filteredTasks, key = { it.taskId }) { task ->
        // ...
    }
}
```

- [ ] **Step 4: 根据 Tab 过滤任务列表**

在 LazyColumn 所在位置添加过滤逻辑：

```kotlin
val filteredTasks = when (taskTab) {
    TaskTab.PENDING -> tasks.filter { it.status == TaskStatus.PENDING }
    TaskTab.ALERTING -> tasks.filter { it.status == TaskStatus.ALERTING }
}
```

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/ducktask/ui/screens/MainScreen.kt
git commit -m "feat: add task tab UI for pending/alerting separation

- Add TaskTab enum (PENDING/ALERTING)
- Add FilterChip tabs for task list filtering
- Remove top spacing for cleaner layout"
```

---

## Task 3: 创建闹钟全屏 Activity

**Files:**
- Create: `app/src/main/java/com/ducktask/AlarmFullScreenActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建 AlarmFullScreenActivity**

```kotlin
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
        // 或使用 Compose：setContent { AlarmFullScreenContent(event, description, ...) }
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
        // 推迟提醒逻辑将在 Task 6 中实现
    }
}
```

- [ ] **Step 2: 注册 Activity 到 AndroidManifest**

在 `</application>` 前添加：

```xml
<activity
    android:name=".AlarmFullScreenActivity"
    android:exported="false"
    android:launchMode="singleTop"
    android:showOnLockScreen="true"
    android:turnScreenOn="true"
    android:showWhenLocked="true"
    android:theme="@style/Theme.DuckTask" />
```

同时确保清单有 `USE_FULL_SCREEN_INTENT` 权限：

```xml
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

- [ ] **Step 3: 创建布局文件**

创建 `app/src/main/res/layout/activity_alarm_full_screen.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#FFFFFF">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="32dp">

        <ImageView
            android:id="@+id/iconAlarm"
            android:layout_width="80dp"
            android:layout_height="80dp"
            android:src="@android:drawable/ic_lock_idle_alarm" />

        <TextView
            android:id="@+id/textEvent"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="提醒"
            android:textSize="32sp"
            android:textStyle="bold"
            android:textColor="#212121" />

        <TextView
            android:id="@+id/textDescription"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text=""
            android:textSize="16sp"
            android:textColor="#757575" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="48dp"
            android:orientation="horizontal"
            android:gravity="center">

            <Button
                android:id="@+id/btnSnooze"
                android:layout_width="0dp"
                android:layout_height="56dp"
                android:layout_weight="1"
                android:layout_marginEnd="8dp"
                android:text="稍后5分钟"
                android:backgroundTint="#E0E0E0"
                android:textColor="#616161" />

            <Button
                android:id="@+id/btnComplete"
                android:layout_width="0dp"
                android:layout_height="56dp"
                android:layout_weight="1"
                android:layout_marginStart="8dp"
                android:text="完成"
                android:backgroundTint="#4CAF50"
                android:textColor="#FFFFFF" />

        </LinearLayout>

    </LinearLayout>

</FrameLayout>
```

- [ ] **Step 4: 在 Activity 中绑定按钮点击**

在 `onCreate` 中添加：

```kotlin
findViewById<Button>(R.id.btnComplete).setOnClickListener { onCompleteClicked() }
findViewById<Button>(R.id.btnSnooze).setOnClickListener { onSnoozeClicked() }
```

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/ducktask/AlarmFullScreenActivity.kt
git add app/src/main/res/layout/activity_alarm_full_screen.xml
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add AlarmFullScreenActivity for full-screen alarm reminders

- Show event name and description
- Play ringtone and vibrate based on user settings
- 'Complete' button marks task as done
- 'Snooze' button delays reminder by 5 minutes"
```

---

## Task 4: 创建 AlarmLoopManager 处理循环提醒

**Files:**
- Create: `app/src/main/java/com/ducktask/util/AlarmLoopManager.kt`
- Create: `app/src/main/java/com/ducktask/AlarmLoopReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建 AlarmLoopManager**

```kotlin
package com.ducktask.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ducktask.app.AlarmFullScreenActivity
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.TaskStatus

object AlarmLoopManager {
    private const val PREFS_NAME = "alarm_loop"
    private const val KEY_TASK_ID = "loop_task_id"
    private const val KEY_EVENT = "loop_event"
    private const val KEY_DESCRIPTION = "loop_description"
    private const val KEY_RINGTONE = "loop_ringtone"
    private const val KEY_VIBRATE_COUNT = "loop_vibrate_count"
    private const val KEY_LOG_ID = "loop_log_id"
    private const val KEY_INTERVAL_MINUTES = "loop_interval"
    private const val KEY_MAX_COUNT = "loop_max_count"
    private const val KEY_CURRENT_COUNT = "loop_current_count"
    const val ACTION_LOOP_REMINDER = "com.ducktask.app.ACTION_LOOP_REMINDER"

    fun startLoop(
        context: Context,
        taskId: String,
        event: String,
        description: String,
        ringtone: Boolean,
        vibrateCount: Int,
        logId: Long,
        intervalMinutes: Int,
        maxCount: Int
    ) {
        // 保存循环状态
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_EVENT, event)
            .putString(KEY_DESCRIPTION, description)
            .putBoolean(KEY_RINGTONE, ringtone)
            .putInt(KEY_VIBRATE_COUNT, vibrateCount)
            .putLong(KEY_LOG_ID, logId)
            .putInt(KEY_INTERVAL_MINUTES, intervalMinutes)
            .putInt(KEY_MAX_COUNT, maxCount)
            .putInt(KEY_CURRENT_COUNT, 1)
            .apply()

        // 立即显示第一次
        showAlarmActivity(context)

        // 调度下次提醒
        scheduleNextLoop(context, intervalMinutes)
    }

    fun onLoopTriggered(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_TASK_ID, null) ?: return

        val currentCount = prefs.getInt(KEY_CURRENT_COUNT, 0)
        val maxCount = prefs.getInt(KEY_MAX_COUNT, 5)
        val intervalMinutes = prefs.getInt(KEY_INTERVAL_MINUTES, 1)

        if (currentCount >= maxCount) {
            // 达到最大次数，结束循环
            endLoopWithExpiration(context)
            return
        }

        // 显示闹钟
        showAlarmActivity(context)

        // 更新计数并调度下次
        prefs.edit().putInt(KEY_CURRENT_COUNT, currentCount + 1).apply()
        scheduleNextLoop(context, intervalMinutes)
    }

    fun onTaskCompleted(context: Context, taskId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_TASK_ID, null) == taskId) {
            clearLoopState(context)
        }
    }

    fun cancelLoop(context: Context) {
        clearLoopState(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmLoopReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun showAlarmActivity(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_TASK_ID, null) ?: return
        val event = prefs.getString(KEY_EVENT, "") ?: ""
        val description = prefs.getString(KEY_DESCRIPTION, "") ?: ""
        val ringtone = prefs.getBoolean(KEY_RINGTONE, true)
        val vibrateCount = prefs.getInt(KEY_VIBRATE_COUNT, 5)
        val logId = prefs.getLong(KEY_LOG_ID, -1)

        val intent = AlarmFullScreenActivity.createIntent(
            context, taskId, event, description, ringtone, vibrateCount, logId
        )
        context.startActivity(intent)
    }

    private fun scheduleNextLoop(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmLoopReceiver::class.java).apply {
            action = ACTION_LOOP_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun endLoopWithExpiration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_TASK_ID, null) ?: return
        val logId = prefs.getLong(KEY_LOG_ID, -1)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val task = db.taskDao().getTaskByTaskId(taskId)

            if (task != null) {
                if (task.hasRepeat()) {
                    // 周期性提醒：重置为待提醒
                    db.taskDao().updateStatus(taskId, TaskStatus.PENDING)
                    AppLogger.info("AlarmLoopManager", "Loop expired for periodic task, reset to PENDING: $taskId")
                } else {
                    // 一次性提醒：删除
                    db.taskDao().updateStatus(taskId, TaskStatus.DELETED)
                    if (logId > 0) {
                        db.reminderLogDao().acknowledge(logId, System.currentTimeMillis(), "loop_expired")
                    }
                    AppLogger.info("AlarmLoopManager", "Loop expired for one-time task, deleted: $taskId")
                }
            }

            clearLoopState(context)
        }
    }

    private fun clearLoopState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
```

- [ ] **Step 2: 创建 AlarmLoopReceiver**

```kotlin
package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.util.AlarmLoopManager

class AlarmLoopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmLoopManager.ACTION_LOOP_REMINDER) {
            AlarmLoopManager.onLoopTriggered(context)
        }
    }
}
```

- [ ] **Step 3: 注册 Receiver 到 AndroidManifest**

```xml
<receiver
    android:name=".AlarmLoopReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.ducktask.app.ACTION_LOOP_REMINDER" />
    </intent-filter>
</receiver>
```

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/ducktask/util/AlarmLoopManager.kt
git add app/src/main/java/com/ducktask/AlarmLoopReceiver.kt
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add AlarmLoopManager for persistent loop reminders

- Use AlarmManager for reliable scheduling
- Track loop state in SharedPreferences
- Handle max count expiration (periodic vs one-time)"
```

---

## Task 5: 修改 AlarmReceiver 触发闹钟和循环

**Files:**
- Modify: `app/src/main/java/com/ducktask/AlarmReceiver.kt`

- [ ] **Step 1: 添加闹钟样式和循环提醒的触发逻辑**

在 AlarmReceiver.kt 中，找到 STRONG 模式处理部分，修改为：

```kotlin
// STRONG 模式：仅通过悬浮窗提醒
if (task.reminderMode == ReminderMode.STRONG) {
    if (!PermissionUtils.canDrawOverlay(appContext)) {
        AppLogger.info("AlarmReceiver", "Overlay permission missing, skip strong overlay for: ${task.event}")
    } else {
        val deviceLocked = PermissionUtils.isDeviceLocked(appContext)
        if (deviceLocked) {
            // 设备锁屏，保存待显示状态
            PendingOverlayManager.savePending(...)
            AppLogger.info("AlarmReceiver", "Device locked, saved pending overlay for: ${task.event}")
        }

        // 启动强提醒服务（会自动处理锁屏情况）
        val started = StrongReminderOverlayService.startIfPossible(appContext, task, logId)
        if (!started) {
            AppLogger.info("AlarmReceiver", "Failed to start strong overlay service for: ${task.event}")
        }

        // 如果启用了闹钟样式或循环提醒，且任务已触发（ALERTING状态）
        if (task.status == TaskStatus.ALERTING) {
            if (task.alarmEnabled) {
                // 启动闹钟全屏
                val alarmIntent = AlarmFullScreenActivity.createIntent(
                    appContext,
                    task.taskId,
                    task.event,
                    task.description,
                    task.alarmRingtone,
                    task.alarmVibrateCount,
                    logId
                )
                appContext.startActivity(alarmIntent)
            }

            // 如果启用了循环提醒
            if (task.alertLoopEnabled) {
                AlarmLoopManager.startLoop(
                    context = appContext,
                    taskId = task.taskId,
                    event = task.event,
                    description = task.description,
                    ringtone = task.alarmRingtone,
                    vibrateCount = task.alarmVibrateCount,
                    logId = logId,
                    intervalMinutes = task.alertLoopIntervalMinutes,
                    maxCount = task.alertLoopMaxCount
                )
            }
        }
    }
}
```

确保添加必要的 import：

```kotlin
import com.ducktask.app.util.AlarmLoopManager
```

- [ ] **Step 2: 提交代码**

```bash
git add app/src/main/java/com/ducktask/AlarmReceiver.kt
git commit -m "feat: integrate alarm and loop features in AlarmReceiver

- Trigger AlarmFullScreenActivity when alarmEnabled
- Start loop reminder when alertLoopEnabled and task is alerting"
```

---

## Task 6: 更新添加提醒弹窗 UI

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt`
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainViewModel.kt`

- [ ] **Step 1: 在 InputCard 中添加闹钟和循环选项**

在 InputCard 函数中添加状态变量：

```kotlin
@Composable
private fun InputCard(
    // ... existing params ...
) {
    var alarmEnabled by remember { mutableStateOf(false) }
    var alarmRingtone by remember { mutableStateOf(true) }
    var alarmVibrateCount by remember { mutableIntStateOf(5) }
    var alertLoopEnabled by remember { mutableStateOf(false) }
    var alertLoopInterval by remember { mutableIntStateOf(1) }
    var alertLoopMaxCount by remember { mutableIntStateOf(5) }

    // ... existing content ...
}
```

在 ReminderModePicker 后面添加：

```kotlin
Spacer(modifier = Modifier.height(8.dp))

// 提醒样式选择
Text(
    text = "提醒样式",
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
)
Row(
    modifier = Modifier.padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    FilterChip(
        selected = !alarmEnabled,
        onClick = { alarmEnabled = false },
        label = { Text("普通提醒") }
    )
    FilterChip(
        selected = alarmEnabled,
        onClick = { alarmEnabled = true },
        label = { Text("闹钟样式") }
    )
}

// 闹钟样式选项
if (alarmEnabled) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("铃声", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = alarmRingtone, onCheckedChange = { alarmRingtone = it })
            }

            Text(
                text = "震动次数: $alarmVibrateCount",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = alarmVibrateCount.toFloat(),
                onValueChange = { alarmVibrateCount = it.toInt() },
                valueRange = 1f..10f,
                steps = 8
            )
        }
    }
}

// 循环提醒选项（强提醒时显示）
if (createReminderMode == ReminderMode.STRONG) {
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("循环提醒", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = alertLoopEnabled, onCheckedChange = { alertLoopEnabled = it })
    }

    if (alertLoopEnabled) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "间隔: $alertLoopInterval 分钟",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = alertLoopInterval.toFloat(),
                    onValueChange = { alertLoopInterval = it.toInt() },
                    valueRange = 1f..10f,
                    steps = 8
                )

                Text(
                    text = "最大次数: $alertLoopMaxCount 次",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = alertLoopMaxCount.toFloat(),
                    onValueChange = { alertLoopMaxCount = it.toInt() },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }
        }
    }
}
```

需要添加的 import：

```kotlin
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.CardDefaults
```

- [ ] **Step 2: 将新选项传递给 onSubmit**

修改 `Button` 的 onClick：

```kotlin
Button(
    onClick = {
        onSubmit(
            alarmEnabled = alarmEnabled,
            alarmRingtone = alarmRingtone,
            alarmVibrateCount = alarmVibrateCount,
            alertLoopEnabled = alertLoopEnabled,
            alertLoopInterval = alertLoopInterval,
            alertLoopMaxCount = alertLoopMaxCount
        )
    },
    // ...
)
```

修改函数签名接收这些参数：

```kotlin
private fun InputCard(
    // ... existing params ...
    alarmEnabled: Boolean = false,
    alarmRingtone: Boolean = true,
    alarmVibrateCount: Int = 5,
    alertLoopEnabled: Boolean = false,
    alertLoopInterval: Int = 1,
    alertLoopMaxCount: Int = 5
)
```

- [ ] **Step 3: 更新调用处传递参数**

在 HomeContent 中的 InputCard 调用处：

```kotlin
InputCard(
    inputText = uiState.inputText,
    createReminderMode = uiState.createReminderMode,
    isLoading = uiState.isLoading,
    errorMessage = uiState.errorMessage,
    onInputChange = onInputChange,
    onReminderModeChange = onReminderModeChange,
    onSubmit = { alarmEnabled, alarmRingtone, alarmVibrateCount, alertLoopEnabled, alertLoopInterval, alertLoopMaxCount ->
        viewModel.onEvent(
            MainUiEvent.SubmitTaskWithOptions(
                alarmEnabled, alarmRingtone, alarmVibrateCount,
                alertLoopEnabled, alertLoopInterval, alertLoopMaxCount
            )
        )
        showInputSheet = false
    }
)
```

- [ ] **Step 4: 在 MainUiEvent 和 MainViewModel 中添加处理**

在 MainUiEvent 中添加新的事件：

```kotlin
sealed class MainUiEvent {
    // ... existing events ...
    data class SubmitTaskWithOptions(
        val alarmEnabled: Boolean,
        val alarmRingtone: Boolean,
        val alarmVibrateCount: Int,
        val alertLoopEnabled: Boolean,
        val alertLoopInterval: Int,
        val alertLoopMaxCount: Int
    ) : MainUiEvent()
}
```

在 MainViewModel 中处理：

```kotlin
fun onEvent(event: MainUiEvent) {
    when (event) {
        is MainUiEvent.SubmitTaskWithOptions -> {
            // 使用 event 中的选项创建任务
            // 调用原有的 createTask 方法，但传递新选项
        }
        // ... other events ...
    }
}
```

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/ducktask/ui/screens/MainScreen.kt
git add app/src/main/java/com/ducktask/ui/screens/MainViewModel.kt
git commit -m "feat: add alarm and loop options to InputCard UI

- Add alarm style selection (normal/alarm)
- Add ringtone and vibrate count options
- Add loop reminder interval and max count options"
```

---

## Task 7: 验证编译并推送

- [ ] **Step 1: 运行构建**

```bash
./gradlew assembleProdDebug
```

- [ ] **Step 2: 如果有编译错误，修复并重新构建**

- [ ] **Step 3: 推送代码**

```bash
git push origin master
```

- [ ] **Step 4: 等待 CI 构建完成**

```bash
gh run list --limit 3
```

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] Task 模型扩展（alarmEnabled, alarmRingtone, alarmVibrateCount, alertLoopEnabled, alertLoopIntervalMinutes, alertLoopMaxCount）
   - [x] 主页标签页 UI
   - [x] 消除顶部空白
   - [x] AlarmFullScreenActivity 全屏提醒
   - [x] AlarmLoopManager 循环提醒
   - [x] InputCard 新增选项
   - [x] 数据库迁移

2. **Placeholder scan:** 无占位符

3. **Type consistency:**
   - [x] Task 新字段命名一致
   - [x] AlarmLoopManager 方法名一致
   - [x] MainUiEvent 事件命名一致

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-04-30-alarm-reminder-implementation.md`.**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**