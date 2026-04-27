# TaskCard UI 美化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 TaskCard 组件，优化待提醒卡片内容布局，提升视觉层次和信息可读性

**Architecture:** 保持现有 Compose 结构，优化 Row/Column 布局和间距统一性，引入视觉层次更好的组件分区

**Tech Stack:** Jetpack Compose, Material3

---

## 当前问题分析

1. **Chips 行过于拥挤** - 提醒模式、状态、重复规则三chip挤在一行
2. **时间信息区块** - 使用 Surface 包裹但视觉上与卡片其他内容脱节
3. **间距不一致** - 使用大量 `Spacer(modifier = Modifier.height(X.dp))`
4. **删除按钮位置** - 放在内容区右上角，与编辑/完成按钮不在同一视觉区域
5. **缺少视觉引导线** - 内容层次不够清晰

---

## File Structure

- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt` (TaskCard 函数，约 150 行)

---

## Task 1: 重构卡片头部区域 (Chips + 事件标题)

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt:638-690`

- [ ] **Step 1: 运行测试确认当前状态**

```bash
./gradlew assembleDebug --console=plain 2>&1 | tail -20
```

Expected: 构建成功

- [ ] **Step 2: 修改 Chips 区域布局，改用 FlowRow 实现换行**

```kotlin
// 替换现有的 Row(horizontalArrangement = Arrangement.spacedBy(8.dp))
// 新增导入
import androidx.compose.foundation.layout.FlowRow

// 在 TaskCard 函数内，替换 chips 行
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.fillMaxWidth()
) {
    TaskMetaChip(text = task.reminderModeLabel(), tone = accent)
    TaskMetaChip(
        text = if (task.isAlerting()) "待处理" else "待提醒",
        tone = statusTone
    )
    if (task.hasRepeat()) {
        TaskMetaChip(
            text = task.repeatRule()?.toHumanText().orEmpty(),
            tone = MaterialTheme.colorScheme.primary
        )
    }
}
```

- [ ] **Step 3: 调整事件标题样式，使用更清晰的视觉层次**

```kotlin
Text(
    text = task.event,
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.padding(top = 4.dp)
)
```

- [ ] **Step 4: 调整描述文字样式**

```kotlin
Text(
    text = task.description,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.padding(top = 2.dp)
)
```

- [ ] **Step 5: 构建验证**

```bash
./gradlew assembleDebug --console=plain 2>&1 | tail -10
```

Expected: 构建成功

---

## Task 2: 重构时间信息区域

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt:700-725`

- [ ] **Step 1: 简化时间信息区块，使用更轻量的视觉样式**

```kotlin
// 替换原有的 Surface 包裹 Row，改用 Row 直接展示
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = accent.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = accent
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (task.isAlerting()) {
                "已触发：${formatAbsoluteTime(task.nextRunTime)}"
            } else {
                "下次执行：${formatAbsoluteTime(task.nextRunTime)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (task.isAlerting()) {
                "等待你手动确认处理"
            } else {
                formatReminderTime(task.nextRunTime)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}
```

- [ ] **Step 2: 添加底部分隔线**

```kotlin
// 在时间信息区域后添加
HorizontalDivider(
    modifier = Modifier.padding(top = 14.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
)
```

- [ ] **Step 3: 添加分隔线导入**

```kotlin
// 在文件顶部已有
import androidx.compose.material3.HorizontalDivider
```

- [ ] **Step 4: 构建验证**

```bash
./gradlew assembleDebug --console=plain 2>&1 | tail -10
```

Expected: 构建成功

---

## Task 3: 重构操作按钮区域

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt:730-750`

- [ ] **Step 1: 将删除按钮移至操作按钮行，统一视觉区域**

```kotlin
// 替换原有的操作按钮 Row
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 10.dp),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(
        onClick = onDelete,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "删除",
            tint = Error.copy(alpha = 0.65f),
            modifier = Modifier.size(20.dp)
        )
    }
    Spacer(modifier = Modifier.weight(1f))
    TextButton(onClick = onEdit) {
        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text("编辑", style = MaterialTheme.typography.labelLarge)
    }
    Spacer(modifier = Modifier.size(8.dp))
    Button(
        onClick = onDone,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text(if (task.isAlerting()) "已处理" else "完成")
    }
}
```

- [ ] **Step 2: 添加 PaddingValues 导入**

```kotlin
// 文件顶部已有
import androidx.compose.foundation.layout.PaddingValues
```

- [ ] **Step 3: 删除原有的 IconButton(onClick = onDelete) 块**

在 Column 内部，删除原有的:
```kotlin
IconButton(onClick = onDelete) {
    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Error.copy(alpha = 0.72f))
}
```

- [ ] **Step 4: 构建验证**

```bash
./gradlew assembleDebug --console=plain 2>&1 | tail -10
```

Expected: 构建成功

---

## Task 4: 优化整体卡片内边距和布局

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt:649-656`

- [ ] **Step 1: 统一卡片内边距，增加底部空间**

```kotlin
// 修改 Column padding
Column(
    modifier = Modifier.padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(0.dp)
) {
```

- [ ] **Step 2: 构建验证**

```bash
./gradlew assembleDebug --console=plain 2>&1 | tail -10
```

Expected: 构建成功

---

## Task 5: 最终验证

- [ ] **Step 1: 运行完整构建**

```bash
./gradlew assembleDebug --console=plain 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 检查 APK 生成**

```bash
ls -la app/build/outputs/apk/debug/
```

Expected: app-debug.apk 存在

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/com/ducktask/ui/screens/MainScreen.kt
git commit -m "feat(ui): beautify TaskCard layout with improved visual hierarchy

- Use FlowRow for chips to allow natural wrapping
- Add icon box for time display instead of Surface
- Add horizontal divider for visual separation
- Move delete button to action row for consistency
- Improve typography hierarchy (titleLarge, bodyMedium)
- Uniform spacing and padding throughout card"

git status
```

---

## Self-Review Checklist

1. **Spec coverage:** 卡片美化涵盖布局优化、视觉层次、间距统一三个维度
2. **Placeholder scan:** 无占位符，所有代码均已完整提供
3. **Type consistency:** 所有函数调用和参数保持与现有代码一致

---

## Execution Options

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
