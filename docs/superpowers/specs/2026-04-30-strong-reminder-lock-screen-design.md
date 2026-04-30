# 强提醒锁屏全屏显示设计

## 概述

实现强提醒在手机锁屏时主动亮屏并以全屏 Activity 形式弹出提醒，参考微信语音通话的接听页面效果。

## 用户需求

- **场景**：设置强提醒 → 手机锁屏 → 提醒触发 → 手机亮屏 → 全屏提醒弹出
- **锁屏状态**：绕过锁屏 PIN/密码，直接显示全屏提醒
- **未锁屏状态**：统一使用全屏 Activity（与锁屏行为一致）
- **交互**：长按确认按钮完成提醒

## 技术方案

### 核心实现

创建 `FullScreenAlarmActivity` 替代 `StrongReminderOverlayService`，实现类似微信来电的全屏弹出效果。

### Window Flags

Activity 的 WindowManager.LayoutParams 使用以下 flags：

| Flag | 作用 |
|------|------|
| `FLAG_SHOW_WHEN_LOCKED` | 在锁屏上显示 |
| `FLAG_TURN_SCREEN_ON` | 点亮屏幕 |
| `FLAG_DISMISS_KEYGUARD` | 解除锁屏（部分设备需要系统授权） |
| `FLAG_LAYOUT_IN_SCREEN` | 扩展到全屏 |
| `FLAG_LAYOUT_NO_LIMITS` | 不受状态栏/导航栏限制 |

### Activity Theme

```xml
<style name="Theme.DuckTask.FullScreen">
    <item name="android:windowShowWhenLocked">true</item>
    <item name="android:windowTurnScreenOn">true</item>
    <item name="android:windowFullscreen">true</item>
</style>
```

### 权限清单

| 权限 | 状态 | 用途 |
|------|------|------|
| `USE_FULL_SCREEN_INTENT` | ✅ 已有 | 锁屏全屏 Intent |
| `SYSTEM_ALERT_WINDOW` | ✅ 已有 | 备用方案 |
| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | ✅ 已有 | 后台服务 |
| `WAKE_LOCK` | ⚠️ 新增 | 确保屏幕唤醒稳定 |

## 实现步骤

### 1. 新增 FullScreenAlarmActivity

- 继承 Activity（或 AppCompatActivity）
- 实现全屏提醒 UI
- 复用现有 `StrongReminderOverlayService` 的视觉设计：
  - 动态环形按钮动画
  - 粒子效果
  - 长按交互逻辑
- 处理完成/取消回调

### 2. 更新 AndroidManifest

- 注册 `FullScreenAlarmActivity`
- 添加 theme 属性
- 添加 `android:showWhenLocked="true"`
- 添加 `android:turnScreenOn="true"`

### 3. 修改 AlarmReceiver

- 检测强提醒模式时，优先启动 `FullScreenAlarmActivity`
- 不再使用 `PendingOverlayManager` 等待解锁
- 简化逻辑：闹钟触发 → 直接启动 Activity

### 4. 处理 Activity 生命周期

- `onCreate()`: 设置 Window flags，显示 UI
- `onDestroy()`: 清理资源，更新任务状态
- 防止重复启动（使用 Intent.FLAG_ACTIVITY_SINGLE_TOP）

### 5. 权限引导

- 更新权限检查逻辑
- 提示用户开启"显示在其他应用上层"权限
- Android 14+ 可能需要额外引导

## UI 复用策略

保留 `StrongReminderOverlayService` 的视觉代码：

1. 将 UI 渲染逻辑提取为独立函数或 View 类
2. `FullScreenAlarmActivity` 和 `StrongReminderOverlayService` 共用同一套 UI 组件
3. 减少代码重复

## 兼容处理

### Android 10+

- `FLAG_DISMISS_KEYGUARD` 在有锁屏密码时可能无效
- 需要在系统设置中授予"在锁屏上层显示"权限
- 添加适当的错误处理和降级方案

### 不同厂商 ROM

- 小米/华为等需要额外引导开启权限
- 复用现有的 `PermissionUtils` 权限引导逻辑

## 状态变化

### Before

```
闹钟触发 → AlarmReceiver
         → 检测锁屏
         → 保存 PendingOverlay
         → 启动 StrongReminderOverlayService
         → 等待解锁...
         → 解锁后显示悬浮窗
```

### After

```
闹钟触发 → AlarmReceiver
         → 启动 FullScreenAlarmActivity
         → Activity 主动亮屏 + 绕过锁屏
         → 显示全屏提醒
```

## 待确定

- [ ] 是否需要保留 `StrongReminderOverlayService` 作为备用？
- [ ] 是否需要添加用户设置选项选择提醒样式？
