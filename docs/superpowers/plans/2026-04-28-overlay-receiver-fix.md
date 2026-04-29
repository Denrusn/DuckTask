# 修复 OverlayUnlockReceiver 动态注册

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复 OverlayUnlockReceiver 无法接收解锁事件的问题

**Architecture:** 将 OverlayUnlockReceiver 从静态注册改为动态注册（在 DuckTaskApp 中注册/注销）

**Tech Stack:** BroadcastReceiver, Application.onCreate()

---

## 问题分析

### 静态注册的 BroadcastReceiver 无法接收 ACTION_USER_PRESENT

在 Android 8.0 (API 26) 及以上版本：
- 静态注册的 BroadcastReceiver 无法接收大多数隐式广播
- `ACTION_USER_PRESENT` 不是系统广播，是框架广播
- `android:exported="false"` 的 receiver 默认不接收外部广播

这就是为什么锁屏触发的强提醒在解锁后无法弹出悬浮窗。

---

## Task 1: 修改 DuckTaskApp 实现动态注册

**Files:**
- Modify: `app/src/main/java/com/ducktask/DuckTaskApp.kt`
- Modify: `app/src/main/AndroidManifest.xml` (移除静态注册)

- [ ] **Step 1: 创建 DuckTaskApp.kt 动态注册代码**

```kotlin
package com.ducktask.app

import android.app.Application
import android.content.IntentFilter
import com.ducktask.app.util.AppLogger

class DuckTaskApp : Application() {
    private var overlayUnlockReceiver: OverlayUnlockReceiver? = null

    override fun onCreate() {
        super.onCreate()
        registerOverlayReceiver()
        AppLogger.info("DuckTaskApp", "Application created, overlay receiver registered")
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterOverlayReceiver()
    }

    private fun registerOverlayReceiver() {
        if (overlayUnlockReceiver != null) return
        overlayUnlockReceiver = OverlayUnlockReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            registerReceiver(overlayUnlockReceiver, filter, RECEIVE_NOT_EXPORTED_PERMISSION)
        }.onFailure {
            AppLogger.error("DuckTaskApp", "Failed to register OverlayUnlockReceiver", it)
        }
    }

    private fun unregisterOverlayReceiver() {
        overlayUnlockReceiver?.let {
            runCatching { unregisterReceiver(it) }
            overlayUnlockReceiver = null
        }
    }
}
```

注意：使用 `RECEIVE_NOT_EXPORTED_PERMISSION` (API 33+) 来注册非导出的 receiver。

- [ ] **Step 2: 从 AndroidManifest.xml 移除静态注册**

在 `<application>` 中找到并删除以下代码：

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
git add app/src/main/java/com/ducktask/DuckTaskApp.kt app/src/main/AndroidManifest.xml
git commit -m "fix: register OverlayUnlockReceiver dynamically to receive ACTION_USER_PRESENT

Static receivers cannot receive implicit broadcasts like ACTION_USER_PRESENT
on Android 8.0+. Now registering it dynamically in DuckTaskApp.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] 动态注册 OverlayUnlockReceiver
   - [x] 移除静态注册
   - [x] 正确处理注册/注销

2. **Placeholder scan:** 无占位符

3. **API 兼容性:**
   - [x] `RECEIVE_NOT_EXPORTED_PERMISSION` 需要 minSdk 33
   - [x] 考虑 fallback 到其他方式注册