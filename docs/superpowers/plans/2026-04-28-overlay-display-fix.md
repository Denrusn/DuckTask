# 修复解锁后悬浮窗显示问题

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复锁屏状态下周期性任务解锁后无法显示悬浮窗的问题

**Architecture:** 修复 OverlayUnlockReceiver 使用 startForegroundService，确保 StrongReminderOverlayService 正确启动

**Tech Stack:** Android foreground service, BroadcastReceiver

---

## 问题根因

1. `OverlayUnlockReceiver` 使用 `context.startService()` 而不是 `startForegroundService()`
2. Android 8.0+ 要求从 BroadcastReceiver 启动 Service 必须使用 `startForegroundService()`

---

## Task 1: 修复 OverlayUnlockReceiver 使用 startForegroundService

**Files:**
- Modify: `app/src/main/java/com/ducktask/OverlayUnlockReceiver.kt`

- [ ] **Step 1: 修改 OverlayUnlockReceiver 使用 startForegroundService**

将 `context.startService(overlayIntent)` 改为 `ContextCompat.startForegroundService(context, overlayIntent)`

```kotlin
package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ducktask.app.notification.DuckTaskNotifications
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
            DuckTaskNotifications.ensureChannel(context)
            // 必须使用 startForegroundService，Android 8.0+ 要求从 BroadcastReceiver 启动 Service
            ContextCompat.startForegroundService(context, overlayIntent)
        }
    }
}
```

- [ ] **Step 2: 提交代码**

```bash
git add app/src/main/java/com/ducktask/OverlayUnlockReceiver.kt
git commit -m "fix: use startForegroundService in OverlayUnlockReceiver for Android 8.0+ compatibility"
```

---

## Task 2: 验证本地编译

- [ ] **Step 1: 运行构建**

```bash
./gradlew assembleDebug
```

- [ ] **Step 2: 确认构建成功**

---

## Task 3: 提交并推送到 GitHub

- [ ] **Step 1: 添加所有更改**

```bash
git add app/src/main/java/com/ducktask/OverlayUnlockReceiver.kt
```

- [ ] **Step 2: 提交**

```bash
git commit -m "fix: use startForegroundService in OverlayUnlockReceiver

- Android 8.0+ requires startForegroundService() instead of startService()
- Service must call startForeground() within 5 seconds

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

- [ ] **Step 3: 推送**

```bash
git push origin master
```

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] OverlayUnlockReceiver 使用 startForegroundService
   - [x] 编译通过
   - [x] 推送到 GitHub

2. **Placeholder scan:** 无占位符

3. **修复验证:**
   - [x] 从 `context.startService()` 改为 `ContextCompat.startForegroundService()`
   - [x] 添加必要的 import
