# 强提醒弹窗美化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 简化强提醒弹窗内容，添加环形填充进度动画和脉冲波纹完成效果，让用户有"爽"的交互体验

**Architecture:**
- 使用 Jetpack Compose 重构强提醒界面
- 环形填充动画替代线性进度条
- 脉冲波纹从按钮中心向外扩散，完成时触发
- 粒子爆发效果增强完成反馈
- 弹窗保持极简：仅显示事件名称和环形交互按钮

**Tech Stack:** Jetpack Compose, Canvas 动画, Animation APIs

---

## 设计决策

### 用户确认的需求
1. **简化内容** - 删除进度条、标签、操作说明等冗余元素
2. **环形填充动画 (C)** - 按钮周围有环形进度条，用户长按时从 0 填充到 100%
3. **脉冲波纹效果 (D)** - 完成时从按钮中心向外扩散脉冲波纹
4. **粒子爆发** - 完成时添加粒子向四周扩散，增强完成感

### 设计原则
- **极简主义** - 只保留必要信息：事件名称 + 环形按钮
- **聚焦交互** - 所有视觉元素都服务于"长按完成"这个核心动作
- **即时反馈** - 每个状态变化都有动画，让用户感知进度

---

## 文件结构

- Modify: `app/src/main/java/com/ducktask/StrongReminderActivity.kt` - 重构整个 Compose UI

---

## Task 1: 简化弹窗 UI

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderActivity.kt`

- [ ] **Step 1: 读取当前文件确认结构**

确认 `StrongReminderScreen` 和 `LongPressDismissButton` 的位置和内容。

- [ ] **Step 2: 重写 StrongReminderScreen 组件**

删除冗余元素，仅保留事件名称和环形按钮：

```kotlin
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D),
                        Color(0xFF1A1A1A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 发光背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(DuckOrange.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            // 事件名称 - 大字体居中
            Text(
                text = event.ifBlank { "DuckTask 提醒" },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            // 环形填充按钮
            RingFillDismissButton(onDismiss = onDismiss)
        }
    }
}
```

- [ ] **Step 3: 删除 LongPressDismissButton 并创建新组件**

删除原来的 `LongPressDismissButton` 函数，准备替换为新的 `RingFillDismissButton`。

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/ducktask/StrongReminderActivity.kt
git commit -m "refactor(reminder): simplify strong reminder popup - remove progress bar and labels"
```

---

## Task 2: 实现环形填充动画

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderActivity.kt`

- [ ] **Step 1: 添加必要的导入**

确保以下导入存在：

```kotlin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
```

- [ ] **Step 2: 创建 RingFillDismissButton 组件**

在 `StrongReminderScreen` 函数之后添加：

```kotlin
@Composable
private fun RingFillDismissButton(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressJob by remember { mutableStateOf<Job?>(null) }

    // 环形进度动画
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 50, easing = LinearEasing),
        label = "ringProgress"
    )

    // 按钮缩放动画
    val buttonScale by animateFloatAsState(
        targetValue = when {
            isCompleted -> 1.08f
            isHolding -> 0.94f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 120),
        label = "buttonScale"
    )

    // 成功绿色
    val successGreen = Color(0xFF4CAF50)
    val ringColor = if (isCompleted) successGreen else DuckOrange

    Box(
        modifier = Modifier
            .size(220.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        isCompleted = false
                        progressJob?.cancel()

                        // 开始环形填充
                        progressJob = scope.launch {
                            repeat(30) { step ->
                                delay(100)
                                progress = (step + 1) / 30f
                            }
                            // 完成！
                            isCompleted = true
                            delay(350)
                            onDismiss()
                        }

                        val released = tryAwaitRelease()
                        isHolding = false
                        if (released && !isCompleted) {
                            progressJob?.cancel()
                            progress = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 背景光晕
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ringColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 环形进度（Canvas 绘制）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = this.center

            // 背景圆环
            drawCircle(
                color = ringColor.copy(alpha = 0.2f),
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )

            // 进度圆弧
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    center.x - radius,
                    center.y - radius
                ),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }

        // 中心内容
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.TouchApp,
                contentDescription = null,
                tint = if (isCompleted) successGreen else ringColor,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = when {
                    isCompleted -> "完成"
                    isHolding -> "保持"
                    else -> "长按解锁"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
```

- [ ] **Step 3: 确保导入完整**

验证所有需要的导入都已添加，特别是：
- `androidx.compose.ui.graphics.StrokeCap`
- `androidx.compose.ui.graphics.drawscope.Stroke`
- `androidx.compose.ui.geometry.Offset`
- `androidx.compose.ui.geometry.Size`

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/ducktask/StrongReminderActivity.kt
git commit -m "feat(reminder): add ring fill animation for dismiss button"
```

---

## Task 3: 实现脉冲波纹效果

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderActivity.kt`

- [ ] **Step 1: 创建 PulseRippleEffect 组件**

在 `RingFillDismissButton` 之后添加：

```kotlin
@Composable
private fun PulseRippleEffect(
    isActive: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    var rippleScale by remember { mutableFloatStateOf(0f) }
    var rippleAlpha by remember { mutableFloatStateOf(0f) }

    val animatedScale by animateFloatAsState(
        targetValue = rippleScale,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "rippleScale"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = rippleAlpha,
        animationSpec = tween(durationMillis = 1000),
        label = "rippleAlpha"
    )

    LaunchedEffect(isActive) {
        if (isActive) {
            rippleScale = 0f
            rippleAlpha = 1f
            // 快速扩散
            rippleScale = 1f
            rippleAlpha = 0f
        }
    }

    if (animatedAlpha > 0.01f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2 * animatedScale

            // 外圈波纹
            drawCircle(
                color = color.copy(alpha = animatedAlpha * 0.3f),
                radius = maxRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )

            // 内圈波纹（延迟效果）
            if (animatedScale > 0.3f) {
                drawCircle(
                    color = color.copy(alpha = animatedAlpha * 0.2f),
                    radius = maxRadius * 0.7f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
```

- [ ] **Step 2: 在弹窗中集成脉冲波纹**

修改 `StrongReminderScreen`：

```kotlin
@Composable
private fun StrongReminderScreen(
    event: String,
    description: String,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { }

    var showRipple by remember { mutableStateOf(false) }
    val rippleColor = DuckOrange

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D),
                        Color(0xFF1A1A1A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 发光背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(rippleColor.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        // 脉冲波纹层
        if (showRipple) {
            PulseRippleEffect(
                isActive = showRipple,
                color = rippleColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            Text(
                text = event.ifBlank { "DuckTask 提醒" },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            RingFillDismissButton(
                onDismiss = {
                    showRipple = true
                    kotlinx.coroutines.delay(500)
                    onDismiss()
                }
            )
        }
    }
}
```

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/com/ducktask/StrongReminderActivity.kt
git commit -m "feat(reminder): add pulse ripple effect on completion"
```

---

## Task 4: 添加粒子爆发效果

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderActivity.kt`

- [ ] **Step 1: 创建 ParticleBurst 组件**

在 `PulseRippleEffect` 之后添加：

```kotlin
@Composable
private fun ParticleBurst(
    isActive: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    var particles by remember { mutableStateOf<List<ParticleState>>(emptyList()) }

    data class ParticleState(
        val angle: Float,
        val distance: Float,
        val size: Float
    )

    val progress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "particleProgress"
    )

    LaunchedEffect(isActive) {
        if (isActive) {
            // 生成 16 个粒子
            particles = (0 until 16).map { i ->
                ParticleState(
                    angle = i * 22.5f - 90f, // 均匀分布
                    distance = 0.35f + kotlin.random.Random.nextFloat() * 0.15f,
                    size = 6f + kotlin.random.Random.nextFloat() * 8f
                )
            }
            delay(700)
            particles = emptyList()
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = this.center
        val maxDistance = size.minDimension / 2

        particles.forEach { particle ->
            val currentDistance = maxDistance * particle.distance * progress
            val radians = Math.toRadians(particle.angle.toDouble())
            val x = center.x + (currentDistance * kotlin.math.cos(radians)).toFloat()
            val y = center.y + (currentDistance * kotlin.math.sin(radians)).toFloat()

            // 粒子渐隐
            val alpha = (1f - progress) * 0.9f
            val currentSize = particle.size * (1f - progress * 0.5f)

            drawCircle(
                color = color.copy(alpha = alpha),
                radius = currentSize,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}
```

- [ ] **Step 2: 在完成时同时触发粒子和波纹**

修改 `StrongReminderScreen` 中的波纹部分：

```kotlin
// 脉冲波纹层
if (showRipple) {
    PulseRippleEffect(
        isActive = showRipple,
        color = rippleColor
    )
    ParticleBurst(
        isActive = showRipple,
        color = successGreen // 完成时用绿色
    )
}
```

- [ ] **Step 3: 调整延时**

粒子动画 700ms + 波纹动画 1000ms，修改 onDismiss 触发：

```kotlin
RingFillDismissButton(
    onDismiss = {
        showRipple = true
        kotlinx.coroutines.delay(600) // 等待粒子动画
        onDismiss()
    }
)
```

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/ducktask/StrongReminderActivity.kt
git commit -m "feat(reminder): add particle burst effect on completion"
```

---

## Task 5: 最终验证并推送

- [ ] **Step 1: 验证代码完整性**

确保所有组件都已正确实现：
- [ ] `StrongReminderScreen` - 简化后的主界面
- [ ] `RingFillDismissButton` - 环形填充按钮
- [ ] `PulseRippleEffect` - 脉冲波纹
- [ ] `ParticleBurst` - 粒子爆发

- [ ] **Step 2: 推送代码**

```bash
git push origin HEAD
```

- [ ] **Step 3: 等待构建**

```bash
gh run list --limit 3
```

- [ ] **Step 4: 获取 APK 链接**

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] 简化弹窗内容 - Task 1
   - [x] 环形填充动画 - Task 2
   - [x] 脉冲波纹效果 - Task 3
   - [x] 粒子爆发效果 - Task 4

2. **Placeholder scan:** 无占位符，所有代码均为完整实现

3. **Type consistency:** 所有函数签名和参数保持一致

4. **动画流程:**
   - 初始：空心圆环 + "长按解锁"
   - 长按中：圆环逐渐填充 + "保持"
   - 完成：绿色圆环 + 勾号 + 绿色粒子爆发 + 橙色波纹扩散
   - 600ms 后弹窗关闭

---

## 动画流程图

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  1. 初始状态                                                  │
│     ┌───────────────────────────────┐                      │
│     │                               │                      │
│     │       事件名称                 │                      │
│     │                               │                      │
│     │         ╭─────╮              │                      │
│     │        ╱       ╲             │                      │
│     │       │  📱     │            │  ← 空心圆环            │
│     │       │ 长按解锁 │            │  ← 圆形按钮             │
│     │        ╲       ╱             │                      │
│     │         ╰─────╯              │                      │
│     │                               │                      │
│     └───────────────────────────────┘                      │
│                                                             │
│  2. 长按进行中                                                │
│     ┌───────────────────────────────┐                      │
│     │         ╭─────╮              │                      │
│     │        ╱███████╲             │  ← 环形填充中          │
│     │       │  保持    │            │  ← 图标和文字变化      │
│     │       │█████████│            │                      │
│     │        ╲███████╱             │                      │
│     └───────────────────────────────┘                      │
│                                                             │
│  3. 完成瞬间 (0-600ms)                                       │
│     ┌───────────────────────────────┐                      │
│     │    ○                           │  ← 波纹向外扩散        │
│     │  ○       ○                    │  ← 粒子四散           │
│     │       ╭─────╮                │                      │
│     │      ╱ ✓ 完成 ╲               │  ← 绿色勾号           │
│     │     │█████████│              │  ← 绿色填充           │
│     │      ╲█████████╱              │                      │
│     │    ○                           │                      │
│     │       ○                        │                      │
│     └───────────────────────────────┘                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
