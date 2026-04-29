# 主页优化与闹钟样式提醒功能设计

## 概述

优化主页布局，添加标签页功能，支持闹钟样式的全屏提醒，并实现强提醒的循环提醒机制。

---

## 功能列表

1. **UI 优化** - 消除主页顶部空白间隔
2. **标签页功能** - "待提醒"和"已提醒待完成"两个标签页
3. **闹钟样式提醒** - 全屏 Activity + 铃声/震动 + 亮屏
4. **循环提醒机制** - 强提醒未完成时循环弹出提醒

---

## 1. Task 模型扩展

### 新增字段

```kotlin
data class Task(
    // ... 现有字段 ...

    // 闹钟提醒选项
    val alarmEnabled: Boolean = false,           // 是否启用闹钟样式
    val alarmRingtone: Boolean = true,            // true=铃声, false=静音
    val alarmVibrateCount: Int = 5,              // 震动次数，默认5次，最大10

    // 循环提醒选项
    val alertLoopEnabled: Boolean = false,       // 是否启用循环提醒
    val alertLoopIntervalMinutes: Int = 1,       // 循环间隔，默认1分钟，最大10
    val alertLoopMaxCount: Int = 5                // 最大循环次数，默认5次，最大10
)
```

---

## 2. 主页 UI 优化

### 2.1 消除顶部空白

移除 "待提醒 (N)" 标签上方的 `Spacer(modifier = Modifier.height(12.dp))`

### 2.2 标签页设计

使用 Compose `Tab` 组件：

```kotlin
var taskTab by rememberSaveable { mutableStateOf(TaskTab.PENDING) }

enum class TaskTab {
    PENDING,      // 待提醒
    ALERTING      // 已提醒待完成
}

// 过滤任务列表
val pendingTasks = tasks.filter { it.status == TaskStatus.PENDING }
val alertingTasks = tasks.filter { it.status == TaskStatus.ALERTING }
```

标签页样式：圆角 Pill 风格，点击切换，选中态高亮

---

## 3. 闹钟样式提醒实现

### 3.1 全屏 Activity

创建 `AlarmFullScreenActivity.kt`：

```kotlin
class AlarmFullScreenActivity : AppCompatActivity() {
    // FLAG 配置
    // - FLAG_SHOW_WHEN_LOCKED
    // - FLAG_TURN_SCREEN_ON
    // - FLAG_KEEP_SCREEN_ON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 从 Intent 获取提醒数据
        // event, description, ringtone, vibrateCount

        // 亮屏
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView { /* 简洁卡片 UI */ }
    }
}
```

### 3.2 铃声播放

```kotlin
private fun playRingtone() {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val ringtone = RingtoneManager.getRingtone(context, uri)
    ringtone.play()
}
```

### 3.3 震动模式

```kotlin
private fun startVibration(count: Int) {
    val pattern = longArrayOf(0, 500, 500) // 震动500ms，停止500ms
    // 重复 count 次
}
```

### 3.4 UI 布局

简洁卡片风格：
- 白色背景，圆角卡片居中
- 顶部闹钟图标
- 事件名称（大字）
- 描述文字
- 底部两个按钮："完成" / "稍后5分钟"

---

## 4. 循环提醒机制

### 4.1 触发流程

```
提醒触发（强提醒 + ALERTING状态）
    ↓
alarmLoopEnabled == true?
    ↓ 是
创建 AlarmLoopManager.scheduleLoop()
    ↓
首次显示全屏提醒
    ↓
用户未处理？
    ↓ 是
等待 alertLoopIntervalMinutes（默认1分钟）
    ↓
循环次数 < alertLoopMaxCount（默认5次）?
    ↓ 是
再次显示全屏提醒
    ↓
达到最大次数？
    ↓ 是
    ↓
    ├── 周期性提醒 → 状态重置为 PENDING
    ├── 一次性提醒 → 状态重置为 DELETED + 记录日志
```

### 4.2 AlarmLoopManager

```kotlin
object AlarmLoopManager {
    fun scheduleLoop(context: Context, task: Task, onComplete: () -> Unit) {
        // 存储任务信息到 SharedPreferences
        // 启动 Handler 定时器
        // 每次定时触发时检查是否完成
    }

    fun onOverlayConfirmed(context: Context, taskId: String) {
        // 取消循环
        // 更新任务状态
    }
}
```

### 4.3 循环结束处理

**周期性提醒：**
```kotlin
dao.update(task.copy(status = TaskStatus.PENDING))
```

**一次性提醒：**
```kotlin
dao.update(task.copy(status = TaskStatus.DELETED))
// 同时记录到日志
logDao.insert(ReminderExecutionLog(
    ...
    dismissMethod = "loop_expired"
))
```

---

## 5. 添加提醒弹窗扩展

### 5.1 新增选项

在 InputCard 中添加：

```kotlin
// 提醒样式选择
Row {
    FilterChip(selected = !alarmEnabled, onClick = { alarmEnabled = false }, label = { Text("普通提醒") })
    FilterChip(selected = alarmEnabled, onClick = { alarmEnabled = true }, label = { Text("闹钟样式") })
}

// 如果 alarmEnabled == true，显示子选项
if (alarmEnabled) {
    Row {
        Text("铃声")
        Switch(checked = alarmRingtone, onCheckedChange = { alarmRingtone = it })
    }

    Row {
        Text("震动次数: $alarmVibrateCount")
        Slider(value = alarmVibrateCount, from = 1, to = 10)
    }
}

// 强提醒时显示循环提醒选项
if (reminderMode == ReminderMode.STRONG) {
    Row {
        Text("循环提醒")
        Switch(checked = alertLoopEnabled, onCheckedChange = { alertLoopEnabled = it })
    }

    if (alertLoopEnabled) {
        Text("间隔: $alertLoopIntervalMinutes 分钟")
        Slider(value = alertLoopIntervalMinutes, from = 1, to = 10)

        Text("最大次数: $alertLoopMaxCount 次")
        Slider(value = alertLoopMaxCount, from = 1, to = 10)
    }
}
```

---

## 6. 数据库变更

### 6.1 Task 表新增列

```sql
ALTER TABLE reminder_tasks ADD COLUMN alarm_enabled INTEGER DEFAULT 0;
ALTER TABLE reminder_tasks ADD COLUMN alarm_ringtone INTEGER DEFAULT 1;
ALTER TABLE reminder_tasks ADD COLUMN alarm_vibrate_count INTEGER DEFAULT 5;
ALTER TABLE reminder_tasks ADD COLUMN alert_loop_enabled INTEGER DEFAULT 0;
ALTER TABLE reminder_tasks ADD COLUMN alert_loop_interval_minutes INTEGER DEFAULT 1;
ALTER TABLE reminder_tasks ADD COLUMN alert_loop_max_count INTEGER DEFAULT 5;
```

---

## 7. 待确认事项

- [ ] 闹钟全屏 Activity 的进入/退出动画
- [ ] "稍后5分钟"按钮的行为
- [ ] 循环提醒期间 App 被杀掉怎么办？（使用 AlarmManager 替代 Handler）

---

## 实现顺序建议

1. **Task 模型扩展** + 数据库迁移
2. **主页标签页** UI
3. **闹钟全屏 Activity** 基本框架
4. **铃声/震动** 功能
5. **循环提醒** 机制
6. **添加提醒弹窗** 选项集成
7. **测试与优化**