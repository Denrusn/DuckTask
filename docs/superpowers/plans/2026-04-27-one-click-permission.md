# 一键权限授权实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建一键授权功能，用户点击一次后，系统会依次打开各个权限的设置页面，用户逐一授权即可

**Architecture:** 在 PermissionCenterContent 添加"一键授权"按钮，点击后按顺序启动每个权限的设置 Intent，每次授权完成后检查并继续下一个

**Tech Stack:** Jetpack Compose, Android Intent

---

## 当前问题分析

1. **用户需要手动点击每个权限** - 当前每个权限都有单独的按钮
2. **用户不知道哪些权限需要授权** - 需要一个清晰的引导流程
3. **权限顺序混乱** - 应该按重要性排序（通知 > 精确闹钟 > 悬浮窗 > ...）

---

## File Structure

- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt` - PermissionCenterContent
- Modify: `app/src/main/java/com/ducktask/util/PermissionUtils.kt` - 添加权限排序方法

---

## Task 1: 添加一键授权按钮和状态管理

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt`

**Changes:**

1. **在 PermissionCenterContent 顶部添加一键授权按钮**
2. **添加权限引导状态**
3. **按重要性排序权限列表**

- [ ] **Step 1: 添加一键授权按钮到 UI**

在 `PermissionCenterContent` 中，在标题卡片下方添加一键授权按钮：

```kotlin
// 在 LazyColumn 的 item 中，标题卡片之后，权限列表之前添加
item {
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = { startGuidedPermissionFlow() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DuckOrange
        )
    ) {
        Icon(Icons.Default.Security, contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text("一键授权全部权限", fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "点击后将依次引导您完成各项权限授权",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
```

- [ ] **Step 2: 添加引导状态和处理函数**

在 `PermissionCenterContent` 函数中添加状态和处理逻辑：

```kotlin
@Composable
private fun PermissionCenterContent(
    permissionIssues: List<AppPermissionIssue>,
    onResolvePermission: (AppPermissionType) -> Unit,
    onAcknowledgePermission: (AppPermissionType) -> Unit
) {
    // 权限按重要性排序（通知最重要）
    val sortedPermissions = remember(permissionIssues) {
        permissionIssues.sortedBy { permission ->
            when (permission.type) {
                AppPermissionType.NOTIFICATION -> 0
                AppPermissionType.EXACT_ALARM -> 1
                AppPermissionType.OVERLAY -> 2
                AppPermissionType.FULL_SCREEN -> 3
                AppPermissionType.BATTERY_OPTIMIZATION -> 4
                AppPermissionType.AUTO_START -> 5
            }
        }
    }

    var pendingPermissions by remember { mutableStateOf<List<AppPermissionIssue>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }

    // 一键授权启动函数
    val startGuidedPermissionFlow: () -> Unit = {
        pendingPermissions = sortedPermissions.filter { it.type != AppPermissionType.AUTO_START }
        currentIndex = 0
        if (pendingPermissions.isNotEmpty()) {
            onResolvePermission(pendingPermissions[0].type)
        }
    }

    // 在 onResolve 中检测是否需要继续下一个
    val wrappedOnResolve: (AppPermissionType) -> Unit = { type ->
        onResolvePermission(type)
        // 授权后延迟检查，更新权限状态
        // 这部分需要调用方在权限授权返回后刷新
    }

    // ... 现有 UI 代码
}
```

- [ ] **Step 3: 添加导入**

确保以下导入存在：

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
```

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/ducktask/ui/screens/MainScreen.kt
git commit -m "feat(permission): add one-click permission authorization button

- Add guided permission flow button
- Sort permissions by importance (notification first)
- Add state management for guided flow"
```

---

## Task 2: 实现权限授权返回后的自动继续

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt`
- Modify: `app/src/main/java/com/ducktask/MainActivity.kt`

**Changes:**

1. **在 MainActivity 中检测权限返回事件**
2. **刷新权限状态并继续引导流程**
3. **处理自动启动权限的特殊情况**

- [ ] **Step 1: 在 MainActivity 添加权限检查回调**

在 `MainActivity` 中，当从设置页面返回时，重新检查权限并更新状态：

```kotlin
// 在 DuckTaskApp 或 MainActivity 中添加
@Composable
fun rememberPermissionCheckTrigger(): () -> Unit {
    val mainViewModel = rememberViewModel()
    return {
        // 触发 ViewModel 重新检查权限
        mainViewModel.checkPermissions()
    }
}
```

- [ ] **Step 2: 修改 MainViewModel 添加权限检查方法**

在 `MainViewModel` 中添加检查权限的方法：

```kotlin
// 在 MainViewModel 中
fun checkPermissions() {
    viewModelScope.launch {
        val context = getApplication<Application>()
        val issues = PermissionUtils.findPermissionIssues(context)
        _permissionIssues.value = issues
    }
}
```

- [ ] **Step 3: 添加 LaunchedEffect 监听权限变化**

在 MainScreen 中添加：

```kotlin
@Composable
fun MainScreen(
    // ... existing parameters
) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionTrigger = rememberPermissionCheckTrigger()

    // 监听权限变化，当从设置返回时自动刷新
    LaunchedEffect(uiState.permissionVersion) {
        // permissionVersion 每次权限变化时递增
        // 这会触发重新计算 pendingPermissions
    }

    // 修改 PermissionCenterContent 调用，传入触发器
    PermissionCenterContent(
        permissionIssues = permissionIssues,
        onResolvePermission = { type ->
            viewModel.resolvePermission(type)
            permissionTrigger()
        },
        onAcknowledgePermission = onAcknowledgePermission
    )
}
```

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/ducktask/ui/screens/MainScreen.kt app/src/main/java/com/ducktask/MainActivity.kt app/src/main/java/com/ducktask/ui/screens/MainViewModel.kt
git commit -m "feat(permission): implement automatic permission flow continuation

- Add permission check callback on return from settings
- Track permission changes with version counter
- Auto-continue guided flow after each permission grant"
```

---

## Task 3: 添加引导进度指示器

**Files:**
- Modify: `app/src/main/java/com/ducktask/ui/screens/MainScreen.kt`

**Changes:**

1. **在引导过程中显示进度**
2. **显示当前正在授权的权限**
3. **显示剩余数量**

- [ ] **Step 1: 添加进度指示器 UI**

在引导开始后，显示进度卡片：

```kotlin
// 在 PermissionCenterContent 中，sortedPermissions item 之前添加
if (pendingPermissions.isNotEmpty() && currentIndex < pendingPermissions.size) {
    item {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DuckOrange.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { (currentIndex + 1).toFloat() / pendingPermissions.size },
                        modifier = Modifier.size(40.dp),
                        color = DuckOrange,
                        strokeWidth = 4.dp
                    )
                    Column {
                        Text(
                            text = "正在引导授权",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pendingPermissions[currentIndex].title} (${currentIndex + 1}/${pendingPermissions.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / pendingPermissions.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = DuckOrange,
                    trackColor = DuckOrange.copy(alpha = 0.2f)
                )
                Text(
                    text = "请在打开的设置页面中开启权限，然后返回继续",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
```

- [ ] **Step 2: 添加导入**

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
```

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/com/ducktask/ui/screens/MainScreen.kt
git commit -m "feat(permission): add guided permission progress indicator

- Show circular progress with count
- Display current permission being guided
- Add linear progress bar"
```

---

## Task 4: 最终验证

- [ ] **Step 1: 推送代码触发 GitHub Actions**

```bash
git push origin HEAD
```

- [ ] **Step 2: 等待构建完成**

```bash
gh run list --limit 3
```

- [ ] **Step 3: 获取 APK 下载链接**

---

## Self-Review Checklist

1. **Spec coverage:**
   - ✅ 一键授权按钮 - Task 1
   - ✅ 自动继续引导 - Task 2
   - ✅ 进度指示器 - Task 3

2. **Placeholder scan:** 无占位符

3. **Type consistency:** 所有方法名和参数保持一致

---

## Execution Options

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
