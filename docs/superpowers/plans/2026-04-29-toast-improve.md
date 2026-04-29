# 优化错误提示和成功提示

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复错误提示消失问题和美化成功提示样式

**Architecture:**
1. 错误提示：改为显示在屏幕顶部，与成功提示相同位置
2. 成功提示：美化设计，添加渐变背景和动画效果

---

## Task 1: 修复错误提示位置

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt`

- [ ] **Step 1: 添加错误提示状态和展示**

在 `MainScreen` 函数中添加错误提示状态：

```kotlin
var showError by remember { mutableStateOf(false) }
```

- [ ] **Step 2: 添加错误提示的 LaunchedEffect**

在现有的 `LaunchedEffect(uiState.successMessage)` 后面添加：

```kotlin
LaunchedEffect(uiState.errorMessage) {
    if (uiState.errorMessage != null) {
        showError = true
        delay(3000)
        showError = false
    }
}
```

- [ ] **Step 3: 添加错误提示 UI 组件**

在成功提示 `AnimatedVisibility` 组件后面添加：

```kotlin
AnimatedVisibility(
    visible = showError,
    enter = fadeIn() + slideInVertically { -it },
    exit = fadeOut() + slideOutVertically { -it },
    modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Error,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = uiState.errorMessage ?: "",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

- [ ] **Step 4: 修改 InputCard 提交逻辑**

在 `HomeContent` 中，修改 `onSubmit` 回调，不直接关闭弹窗：

```kotlin
onSubmit = {
    onSubmit()
    // 不在这里关闭弹窗，让 ViewModel 处理成功/失败后再关闭
}
```

- [ ] **Step 5: 在 ViewModel 中处理弹窗关闭**

查看 `MainViewModel` 中 `SubmitTask` 的处理逻辑，添加错误时不关闭弹窗的逻辑。

---

## Task 2: 美化成功提示样式

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt`

- [ ] **Step 1: 更新成功提示样式**

替换原有的成功提示组件为更精美的设计：

```kotlin
AnimatedVisibility(
    visible = showSuccess,
    enter = fadeIn() + scaleIn(initialScale = 0.8f) + slideInVertically { -it / 2 },
    exit = fadeOut() + scaleOut(targetScale = 0.8f) + slideOutVertically { -it / 2 },
    modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Success,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 渐变背景装饰
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = uiState.successMessage ?: "",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 0.3.sp
            )
        }
    }
}
```

- [ ] **Step 2: 提交代码**

```bash
git add app/src/main/java/com/ducktask/ui/screens/MainScreen.kt
git commit -m "feat: improve error and success toast styles

- Move error message to top of screen (same position as success)
- Add slide-in animation for error toast
- Enhance success toast with gradient background decoration
- Add proper timing for error auto-dismiss (3 seconds)"

---

## Task 3: 验证编译

- [ ] **Step 1: 运行构建**

```bash
./gradlew assembleDebug
```

- [ ] **Step 2: 确认构建成功**

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] 错误提示显示在屏幕顶部
   - [x] 成功提示样式美化
   - [x] 错误提示有适当的自动消失时间

2. **Placeholder scan:** 无占位符

3. **动画检查:**
   - [x] 成功提示：fade + scale + slide 组合动画
   - [x] 错误提示：fade + slide 垂直进入/退出

---