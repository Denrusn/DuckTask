# 强提醒悬浮窗解锁后显示修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 所有强提醒任务在锁屏触发时，解锁后立即弹出悬浮窗进行提醒

**Architecture:** 锁屏时保存待显示提醒状态 + 监听设备解锁事件 + 解锁后显示悬浮窗

**Tech Stack:** SharedPreferences + BroadcastReceiver (ACTION_USER_PRESENT)

---

## 问题分析

### 当前行为
- `AlarmReceiver` 触发时调用 `StrongReminderOverlayService.startIfPossible()`
- `startIfPossible()` 检查 `isDeviceLocked()`，如果锁屏则返回 `false`，不显示悬浮窗
- 解锁后没有任何后续处理，提醒就此"丢失"

### 期望行为
1. 锁屏时闹钟触发 → 悬浮窗不显示，但保存待显示状态
2. 解锁后 → 立即弹出悬浮窗
3. 用户确认 → 清除待显示状态

---

## Task 1: 创建 PendingOverlayManager 管理待显示提醒

**Files:**
- Create: `app/src/main/java/com/ducktask/util/PendingOverlayManager.kt`

- [ ] **Step 1: 创建 PendingOverlayManager**

```kotlin
package com.ducktask.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 管理待显示的强提醒悬浮窗
 * 当设备锁屏时，闹钟触发后提醒会存入这里
 * 解锁后，OverlayUnlockReceiver 会读取并显示
 */
object PendingOverlayManager {
    private const val PREFS_NAME = "pending_overlay"
    private const val KEY_TASK_ID = "task_id"
    private const val KEY_EVENT = "event"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_LOG_ID = "log_id"
    private const val KEY_NOTIFICATION_ID = "notification_id"

    fun savePending(context: Context, taskId: String, event: String, description: String, logId: Long, notificationId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_EVENT, event)
            .putString(KEY_DESCRIPTION, description)
            .putLong(KEY_LOG_ID, logId)
            .putInt(KEY_NOTIFICATION_ID, notificationId)
            .apply()
    }

    fun hasPending(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_TASK_ID)
    }

    fun getPending(context: Context): PendingOverlay? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_TASK_ID, null) ?: return null
        return PendingOverlay(
            taskId = taskId,
            event = prefs.getString(KEY_EVENT, "") ?: "",
            description = prefs.getString(KEY_DESCRIPTION, "") ?: "",
            logId = prefs.getLong(KEY_LOG_ID, -1L),
            notificationId = prefs.getInt(KEY_NOTIFICATION_ID, taskId.hashCode())
        )
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    data class PendingOverlay(
        val taskId: String,
        val event: String,
        val description: String,
        val logId: Long,
        val notificationId: Int
    )
}
```

- [ ] **Step 2: 提交代码**

```bash
git add app/src/main/java/com/ducktask/util/PendingOverlayManager.kt
git commit -m "feat: add PendingOverlayManager to save pending reminders when device is locked"
```

---

## Task 2: 创建 OverlayUnlockReceiver 监听解锁事件

**Files:**
- Create: `app/src/main/java/com/ducktask/OverlayUnlockReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建 OverlayUnlockReceiver**

```kotlin
package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.util.AppLogger
import com.ducktask.app.util.PendingOverlayManager

/**
 * 监听设备解锁事件
 * 当设备解锁时，检查是否有待显示的强提醒，有则立即显示悬浮窗
 */
class OverlayUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val pending = PendingOverlayManager.getPending(context)
        if (pending != null) {
            AppLogger.info("OverlayUnlockReceiver", "Device unlocked, showing pending overlay for: ${pending.event}")
            // 启动悬浮窗
            val overlayIntent = Intent(context, StrongReminderOverlayService::class.java)
                .putExtra(StrongReminderOverlayService.EXTRA_TASK_ID, pending.taskId)
                .putExtra(StrongReminderOverlayService.EXTRA_EVENT, pending.event)
                .putExtra(StrongReminderOverlayService.EXTRA_DESCRIPTION, pending.description)
                .putExtra(StrongReminderOverlayService.EXTRA_LOG_ID, pending.logId)
                .putExtra(StrongReminderOverlayService.EXTRA_NOTIFICATION_ID, pending.notificationId)
            context.startService(overlayIntent)
            // 不在这里清除 PendingOverlay，让悬浮窗完成后再清除
        }
    }
}
```

- [ ] **Step 2: 注册 BroadcastReceiver 到 AndroidManifest**

在 `<application>` 内添加：

```xml
<receiver
    android:name=".OverlayUnlockReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.USER_PRESENT" />
    </intent-filter>
</receiver>
```

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/com/ducktask/OverlayUnlockReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add OverlayUnlockReceiver to show overlay on device unlock"
```

---

## Task 3: 修改 AlarmReceiver 处理锁屏情况

**Files:**
- Modify: `app/src/main/java/com/ducktask/AlarmReceiver.kt`

- [ ] **Step 1: 修改 STRONG 模式的提醒逻辑**

```kotlin
// STRONG 模式：仅通过悬浮窗提醒
if (task.reminderMode == ReminderMode.STRONG) {
    if (PermissionUtils.canDrawOverlay(this) && !PermissionUtils.isDeviceLocked(this)) {
        // 设备未锁屏，直接显示悬浮窗
        StrongReminderOverlayService.startIfPossible(this, task, logId)
    } else {
        // 设备锁屏，保存待显示状态，等解锁后显示
        val notificationId = task.taskId.hashCode()
        PendingOverlayManager.savePending(
            this,
            task.taskId,
            task.event,
            task.description,
            logId,
            notificationId
        )
        AppLogger.info("AlarmReceiver", "Device locked, saved pending overlay for: ${task.event}")
    }
}
```

在文件顶部添加 import：

```kotlin
import com.ducktask.app.util.PendingOverlayManager
import com.ducktask.app.util.PermissionUtils
```

- [ ] **Step 2: 提交代码**

```bash
git add app/src/main/java/com/ducktask/AlarmReceiver.kt
git commit -m "fix: save pending overlay when device is locked, show on unlock"
```

---

## Task 4: 修改 StrongReminderOverlayService 显示时清除待显示状态

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt`

- [ ] **Step 1: 在 showOverlay 成功后清除待显示状态**

在 `showOverlay` 方法最后添加：

```kotlin
// 如果有待显示状态，清除它
PendingOverlayManager.clearPending(this)
```

在文件顶部添加 import：

```kotlin
import com.ducktask.app.util.PendingOverlayManager
```

- [ ] **Step 2: 提交代码**

```bash
git add app/src/main/java/com/ducktask/StrongReminderOverlayService.kt
git commit -m "fix: clear pending overlay state when overlay is shown"
```

---

## Task 5: 验证编译并推送

- [ ] **Step 1: 推送所有更改**

```bash
git push origin HEAD
```

- [ ] **Step 2: 等待 CI 构建**

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] 锁屏时闹钟触发 → 保存待显示状态
   - [x] 监听设备解锁事件
   - [x] 解锁后立即显示悬浮窗
   - [x] 悬浮窗显示后清除待显示状态

2. **Placeholder scan:** 无占位符

3. **流程检查:**
   - 闹钟触发 → isDeviceLocked → true → PendingOverlayManager.savePending()
   - 解锁 → OverlayUnlockReceiver.onReceive() → getPending() → startService()
   - 悬浮窗显示 → showOverlay() → PendingOverlayManager.clearPending()
