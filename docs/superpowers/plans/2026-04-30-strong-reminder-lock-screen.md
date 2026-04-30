# 强提醒锁屏全屏显示实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现强提醒在锁屏时主动亮屏并以全屏 Activity 形式弹出，类似微信语音通话效果

**Architecture:**
- 创建 `FullScreenAlarmActivity` 作为全屏提醒的载体
- 复用现有的 `OverlayActionClusterView` 自定义 View
- 修改 `AlarmReceiver` 直接启动 Activity 而不是 Service
- 保留 `StrongReminderOverlayService` 作为备用方案

**Tech Stack:** Android SDK, Kotlin, WindowManager, Activity

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `ui/views/OverlayActionClusterView.kt` | 提取自 StrongReminderOverlayService | 环形按钮动画 View |
| `FullScreenAlarmActivity.kt` | 新建 | 全屏提醒 Activity |
| `res/values/themes.xml` | 修改 | 添加全屏主题 |
| `AndroidManifest.xml` | 修改 | 注册 Activity |
| `AlarmReceiver.kt` | 修改 | 启动 Activity |
| `StrongReminderOverlayService.kt` | 保留 | 备用 Service（不再主动使用） |

---

## Task 1: 提取 OverlayActionClusterView 到独立文件

**目标:** 将 `StrongReminderOverlayService` 内部的 `OverlayActionClusterView` 内部类提取为独立文件，使其可被 `FullScreenAlarmActivity` 复用

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt` - 删除内部类，改为委托
- Create: `app/src/main/java/com/ducktask/ui/views/OverlayActionClusterView.kt` - 独立 View 文件
- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt` - 添加委托引用

**步骤:**

- [ ] **Step 1: 创建 OverlayActionClusterView.kt 文件**

从 `StrongReminderOverlayService.kt` 第 1132-1600 行提取 `OverlayActionClusterView` 内部类，保存到新文件：

```kotlin
package com.ducktask.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * 强提醒环形按钮动画 View
 * 复用自 StrongReminderOverlayService
 */
class OverlayActionClusterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ... 完整的 OverlayActionClusterView 实现
    // (从 StrongReminderOverlayService.kt 1132-1600 行提取)
}
```

- [ ] **Step 2: 修改 StrongReminderOverlayService.kt**

在文件顶部添加 import：
```kotlin
import com.ducktask.app.ui.views.OverlayActionClusterView
```

删除文件中的 `inner class OverlayActionClusterView` 定义（约 470 行）

将 `OverlayActionClusterView(context)` 改为 `OverlayActionClusterView(this)`

- [ ] **Step 3: 验证编译**

Run: `./gradlew assembleDebug`
Expected: 编译成功，APK 生成

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/ducktask/ui/views/OverlayActionClusterView.kt
git add app/src/main/java/com/ducktask/StrongReminderOverlayService.kt
git commit -m "refactor: extract OverlayActionClusterView to separate file"

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

---

## Task 2: 创建 FullScreenAlarmActivity

**目标:** 创建全屏 Activity 实现强提醒界面，复用 OverlayActionClusterView

**Files:**
- Create: `app/src/main/java/com/ducktask/FullScreenAlarmActivity.kt` - 全屏 Activity
- Create: `res/layout/activity_full_screen_alarm.xml` - 布局文件（可选，用于快速创建 View）

**步骤:**

- [ ] **Step 1: 创建 FullScreenAlarmActivity.kt**

```kotlin
package com.ducktask.app

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.ui.views.OverlayActionClusterView
import com.ducktask.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FullScreenAlarmActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val accentColor = Color.parseColor("#FFFF6B35")
    private val armedColor = Color.parseColor("#FFFFC857")
    private val successColor = Color.parseColor("#FF4CAF50")

    private var overlayContainer: FrameLayout? = null
    private var eventText: TextView? = null
    private var statusText: TextView? = null
    private var actionClusterView: OverlayActionClusterView? = null

    private val holdController = OverlayHoldController()
    private var holdRunnable: Runnable? = null
    private var dismissRunnable = Runnable { dismissReminder() }
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

    enum class HoldState {
        IDLE, CHARGING, CHARGED_WAITING_RELEASE, COMPLETING
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowFlags()
        setupUI()
        loadIntentExtras()
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // 全屏设置
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupUI() {
        overlayContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E60D0D0D"))
            isClickable = true
            isFocusable = true
        }

        eventText = TextView(this).apply {
            text = "DuckTask 提醒"
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 34f
            gravity = android.view.Gravity.CENTER
        }
        overlayContainer?.addView(eventText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
            topMargin = dp(200)
        })

        statusText = TextView(this).apply {
            text = "长按确认"
            setTextColor(Color.parseColor("#F6FFD7B0"))
            textSize = 15f
            gravity = android.view.Gravity.CENTER
        }
        overlayContainer?.addView(statusText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(100)
        })

        actionClusterView = OverlayActionClusterView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(240), dp(240)).apply {
                gravity = android.view.Gravity.CENTER
            }
            setOnTouchListener { _, event ->
                handleTouch(event)
                true
            }
        }
        overlayContainer?.addView(actionClusterView)

        setContentView(overlayContainer)
        syncHoldTransition(holdController.forceIdle())
        applyActionClusterBase(accentColor)
        enterIdleState()
    }

    private fun loadIntentExtras() {
        currentTaskId = intent.getStringExtra(EXTRA_TASK_ID) ?: ""
        currentEvent = intent.getStringExtra(EXTRA_EVENT) ?: "DuckTask 提醒"
        currentDescription = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        currentLogId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        currentNotificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, currentEvent.hashCode())
        eventText?.text = currentEvent
    }

    private fun handleTouch(event: android.view.MotionEvent) {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> startHold()
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> releaseHold()
        }
    }

    private fun startHold() {
        if (holdState == HoldState.COMPLETING) return
        mainHandler.removeCallbacks(dismissRunnable)
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        isPointerDown = true
        completionProgress = 0f
        currentMilestone = 0
        syncHoldTransition(holdController.press())
        updateStatusText("保持按住  能量校准中", Color.parseColor("#FFF9D8AF"))
        applyActionClusterBase(accentColor)

        val startedAt = SystemClock.elapsedRealtime()
        holdRunnable = object : Runnable {
            override fun run() {
                val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(HOLD_DURATION_MS)
                val progress = elapsed.toFloat() / HOLD_DURATION_MS.toFloat()
                val transition = holdController.updateProgress(progress)
                syncHoldTransition(transition)

                if (transition.event == OverlayHoldController.TransitionEvent.ENTERED_CHARGED || elapsed >= HOLD_DURATION_MS) {
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
        val transition = holdController.release()
        syncHoldTransition(transition)
        when (transition.event) {
            OverlayHoldController.TransitionEvent.RESET_TO_IDLE -> enterIdleState()
            OverlayHoldController.TransitionEvent.ENTERED_COMPLETING -> triggerCompletionSequence()
            else -> Unit
        }
    }

    private fun enterIdleState() {
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable = null
        syncHoldTransition(holdController.forceIdle())
        currentMilestone = 0
        completionProgress = 0f
        updateStatusText("长按确认", Color.parseColor("#F6FFD7B0"))
        applyActionClusterBase(accentColor)
    }

    private fun enterChargedWaitingRelease() {
        if (holdState == HoldState.COMPLETING) return
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable = null
        syncHoldTransition(holdController.updateProgress(1f))
        if (holdState != HoldState.CHARGED_WAITING_RELEASE) return
        completionProgress = 0f
        updateStatusText("已锁定  松手立即完成", Color.parseColor("#FFFFF3D6"))
        applyActionClusterBase(armedColor)
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
        actionClusterView?.setAmbientPulse(0f)
        actionClusterView?.setChargedPulse(0f)
        actionClusterView?.setCompletionProgress(0f)
    }

    private fun updateStatusText(text: String, color: Int) {
        statusText?.text = text
        statusText?.setTextColor(color)
    }

    private fun dismissReminder() {
        mainHandler.removeCallbacksAndMessages(null)

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val db = AppDatabase.getInstance(applicationContext)
                if (currentLogId > 0) {
                    db.reminderLogDao().acknowledge(currentLogId, System.currentTimeMillis(), DISMISS_METHOD_ACTIVITY)
                }
                if (currentTaskId.isNotBlank()) {
                    val task = db.taskDao().getTaskByTaskId(currentTaskId)
                    if (task?.status == TaskStatus.ALERTING) {
                        db.taskDao().updateStatus(currentTaskId, TaskStatus.COMPLETED)
                    }
                }
            }.onFailure {
                AppLogger.error("FullScreenAlarm", "Failed to dismiss reminder", it)
            }
        }

        finish()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBackPressed() {
        // 防止用户按返回键关闭
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_EVENT = "event"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val DISMISS_METHOD_ACTIVITY = "activity"
        private const val HOLD_DURATION_MS = 3_000L

        fun createIntent(
            context: android.content.Context,
            taskId: String,
            event: String,
            description: String,
            logId: Long,
            notificationId: Int
        ): android.content.Intent {
            return android.content.Intent(context, FullScreenAlarmActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_EVENT, event)
                putExtra(EXTRA_DESCRIPTION, description)
                putExtra(EXTRA_LOG_ID, logId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    // 复制 OverlayHoldController 内部类 (从 StrongReminderOverlayService.kt)
    internal class OverlayHoldController {
        enum class TransitionEvent {
            NONE, ENTERED_CHARGING, RESET_TO_IDLE, ENTERED_CHARGED, ENTERED_COMPLETING
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
            state = HoldState.CHARGING
            progress = 0f
            return snapshot(TransitionEvent.ENTERED_CHARGING, true)
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
}
```

- [ ] **Step 2: 添加 WAKE_LOCK 权限到 AndroidManifest**

在 `AndroidManifest.xml` 的 `<uses-permission>` 部分添加：
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

- [ ] **Step 3: 注册 FullScreenAlarmActivity**

在 `AndroidManifest.xml` 的 `<application>` 部分添加：
```xml
<activity
    android:name=".FullScreenAlarmActivity"
    android:exported="false"
    android:showOnLockScreen="true"
    android:turnScreenOn="true"
    android:launchMode="singleTop"
    android:theme="@style/Theme.DuckTask.FullScreen" />
```

- [ ] **Step 4: 添加 FullScreen 主题**

在 `res/values/themes.xml` 添加：
```xml
<style name="Theme.DuckTask.FullScreen" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowShowWhenLocked">true</item>
    <item name="android:windowTurnScreenOn">true</item>
    <item name="android:windowFullscreen">true</item>
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
</style>
```

- [ ] **Step 5: 验证编译**

Run: `./gradlew assembleDebug`
Expected: 编译成功

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/ducktask/FullScreenAlarmActivity.kt
git add app/src/main/AndroidManifest.xml
git add app/src/main/res/values/themes.xml
git commit -m "feat: add FullScreenAlarmActivity for lock screen reminders"

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

---

## Task 3: 修改 AlarmReceiver 使用 FullScreenAlarmActivity

**目标:** 修改 AlarmReceiver，在强提醒模式下启动 FullScreenAlarmActivity 而不是 StrongReminderOverlayService

**Files:**
- Modify: `app/src/main/java/com/ducktask/AlarmReceiver.kt` - 改为启动 Activity

**步骤:**

- [ ] **Step 1: 修改 AlarmReceiver.kt**

将强提醒逻辑从启动 `StrongReminderOverlayService` 改为启动 `FullScreenAlarmActivity`：

在 `AlarmReceiver.kt` 中找到以下代码块（约第 92-116 行）：

```kotlin
// STRONG 模式：仅通过悬浮窗提醒
if (task.reminderMode == ReminderMode.STRONG) {
    if (!PermissionUtils.canDrawOverlay(context)) {
        AppLogger.info("AlarmReceiver", "Overlay permission missing, skip strong overlay for: ${task.event}")
    } else {
        val deviceLocked = PermissionUtils.isDeviceLocked(appContext)
        if (deviceLocked) {
            // 设备锁屏，保存待显示状态，并由前台服务守候到解锁
            PendingOverlayManager.savePending(...)
        }
        val started = StrongReminderOverlayService.startIfPossible(appContext, task, logId)
        // ...
    }
}
```

替换为：

```kotlin
// STRONG 模式：启动全屏提醒 Activity
if (task.reminderMode == ReminderMode.STRONG) {
    try {
        val intent = FullScreenAlarmActivity.createIntent(
            context = appContext,
            taskId = task.taskId,
            event = task.event,
            description = task.description,
            logId = logId,
            notificationId = task.taskId.hashCode()
        )
        appContext.startActivity(intent)
        AppLogger.info("AlarmReceiver", "Started FullScreenAlarmActivity for: ${task.event}")
    } catch (e: Exception) {
        AppLogger.error("AlarmReceiver", "Failed to start FullScreenAlarmActivity", e)
        // 备用：启动 StrongReminderOverlayService
        if (PermissionUtils.canDrawOverlay(appContext)) {
            StrongReminderOverlayService.startIfPossible(appContext, task, logId)
        }
    }
}
```

同样修改重复任务部分（约第 53-77 行）。

- [ ] **Step 2: 添加 import**

在 `AlarmReceiver.kt` 顶部添加：
```kotlin
import com.ducktask.app.FullScreenAlarmActivity
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew assembleDebug`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/ducktask/AlarmReceiver.kt
git commit -m "feat: switch to FullScreenAlarmActivity for strong reminders"

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

---

## Task 4: 简化 StrongReminderOverlayService（可选）

**目标:** 清理 `StrongReminderOverlayService` 中不再使用的锁屏等待逻辑，保留但不再主动使用

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt` - 简化代码

**步骤:**

- [ ] **Step 1: 简化 StrongReminderOverlayService**

删除以下不再需要的逻辑：
1. `PendingOverlayManager` 相关调用（不再保存待显示状态）
2. `isWaitingForUnlock` 相关逻辑（不再需要等待解锁）
3. `registerUnlockReceiverIfNeeded()` / `unregisterUnlockReceiverIfNeeded()`
4. `unlockPollAttemptsRemaining` / `startUnlockPolling()`
5. `armDeferredOverlay()` / `maybeShowDeferredOverlay()`

简化的 `onStartCommand` 应该直接调用 `showOverlay()`：
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null) {
        stopSelf()
        return START_NOT_STICKY
    }

    // ... 加载 intent 数据 ...

    ServiceCompat.startForeground(
        this,
        currentNotificationId,
        buildForegroundNotification(event, description),
        foregroundServiceType(false)
    )

    showOverlay(event, description)
    return START_NOT_STICKY
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew assembleDebug`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/ducktask/StrongReminderOverlayService.kt
git commit -m "refactor: simplify StrongReminderOverlayService (backup mode only)"

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

---

## Task 5: 更新权限引导

**目标:** 更新 `PermissionUtils`，确保用户了解需要开启哪些权限

**Files:**
- Modify: `app/src/main/java/com/ducktask/util/PermissionUtils.kt` - 更新权限检查

**步骤:**

- [ ] **Step 1: 检查 FULL_SCREEN 权限检查**

确认 `AppPermissionType.FULL_SCREEN` 的权限检查逻辑正确：
- Android 14+ (API 34+): 使用 `notificationService.canUseFullScreenIntent()`
- 这应该已经在现有代码中实现了

- [ ] **Step 2: 验证编译**

Run: `./gradlew assembleDebug`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/ducktask/util/PermissionUtils.kt
git commit -m "chore: ensure full screen permission check is correct"

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

---

## Task 6: 端到端测试

**目标:** 构建 APK 并验证功能

**步骤:**

- [ ] **Step 1: 构建 APK**

Run: `./gradlew assembleDebug`
Expected: 编译成功，APK 生成

- [ ] **Step 2: 运行测试**

确保单元测试通过：
Run: `./gradlew testDebugUnitTest`
Expected: 所有测试通过

- [ ] **Step 3: 提交所有更改**

```bash
git add -A
git commit -m "feat: implement lock screen full screen alarm for strong reminders

- Add FullScreenAlarmActivity for lock screen reminders
- Extract OverlayActionClusterView for reuse
- Update AlarmReceiver to use Activity instead of Service
- Add WAKE_LOCK permission"

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

---

## 验证清单

- [ ] FullScreenAlarmActivity 在锁屏时能正常启动
- [ ] 屏幕被点亮
- [ ] 锁屏被绕过（部分设备需要系统设置）
- [ ] 全屏提醒 UI 正确显示
- [ ] 长按交互正常工作
- [ ] 完成后任务状态正确更新
- [ ] 权限引导正确提示用户
