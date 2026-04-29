# 权限状态实时检测修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复电池优化和自启动权限在授予后返回 APP 无法第一时间识别的问题

**Architecture:**
1. 电池优化：添加延迟重新检测机制（500ms 延迟）
2. 自启动：在 UI 层面提供"已完成"确认按钮，用户确认后标记为已授权

**Tech Stack:** Android PowerManager API, SharedPreferences

---

## Task 1: 修复电池优化权限延迟检测

**Files:**
- Modify: `app/src/main/java/com/ducktask/MainActivity.kt`

- [ ] **Step 1: 添加延迟重新检测逻辑**

在 `onResume()` 中，授予设置页面后立即返回时添加延迟检测：

```kotlin
private var pendingBatteryCheck = false

override fun onResume() {
    super.onResume()
    refreshPermissionIssues()

    // 如果之前有电池优化问题，增加延迟重新检测
    if (pendingBatteryCheck) {
        pendingBatteryCheck = false
        Handler(Looper.getMainLooper()).postDelayed({
            refreshPermissionIssues()
        }, 500)
    }
}

private fun refreshPermissionIssues() {
    val issues = PermissionUtils.findPermissionIssues(this)
    val hadBatteryIssue = permissionIssues.any { it.type == AppPermissionType.BATTERY_OPTIMIZATION }
    val hasBatteryIssueNow = issues.any { it.type == AppPermissionType.BATTERY_OPTIMIZATION }

    permissionIssues = issues

    // 如果刚刚授予了电池优化权限但状态还没更新，设置待检测标记
    if (hadBatteryIssue && !hasBatteryIssueNow) {
        pendingBatteryCheck = true
    }
}
```

- [ ] **Step 2: 在 resolvePermission 中标记待检测**

修改 `resolvePermission` 方法，在跳转到电池优化设置前标记：

```kotlin
AppPermissionType.BATTERY_OPTIMIZATION -> {
    pendingBatteryCheck = true
    launchSettings(type)
}
```

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/com/ducktask/MainActivity.kt
git commit -m "fix: add delayed battery optimization permission check"
```

---

## Task 2: 自启动权限检测优化（UI 优化）

**Files:**
- Modify: `app/src/main/java/com/ducktask/util/PermissionUtils.kt`
- Modify: `app/src/main/java/com/ducktask/MainActivity.kt`

- [ ] **Step 1: 更新自启动权限说明**

修改 `shouldShowAutoStartGuide` 的描述，让用户清楚需要手动确认：

```kotlin
if (shouldShowAutoStartGuide(context)) {
    issues += AppPermissionIssue(
        type = AppPermissionType.AUTO_START,
        title = "建议开启自启动",
        description = "部分厂商会限制应用后台拉起。请在系统自启动管理页允许 DuckTask 自启动。",
        actionLabel = "前往设置"
    )
}
```

- [ ] **Step 2: 在 MainActivity 中添加自启动确认反馈**

修改 `acknowledgePermission` 方法的描述，让用户知道点击后表示已完成：

```kotlin
// 保持现有逻辑，但注释说明这是用户手动确认机制
AppPermissionType.AUTO_START -> {
    // 用户点击"忽略"表示已授权完成
    PermissionGuideStore.acknowledgeAutoStart(this)
    refreshPermissionIssues()
}
```

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/com/ducktask/util/PermissionUtils.kt app/src/main/java/com/ducktask/MainActivity.kt
git commit -m "docs: clarify auto-start permission is user-acknowledged"
```

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
   - [x] 电池优化权限延迟检测（500ms）
   - [x] 自启动权限说明优化

2. **Placeholder scan:** 无占位符

3. **代码一致性:**
   - [x] MainActivity 中 pendingBatteryCheck 变量名一致
   - [x] Handler 正确导入

---

## 已知限制说明

**自启动权限无法实时检测的根本原因：**
- Android 标准 API 不提供自启动状态查询
- 各厂商（小米、华为、OPPO、Vivo 等）实现各异
- 没有统一的系统 API 可以检测

**当前解决方案：**
- UI 引导用户前往设置页面
- 用户手动确认授权后，点击"忽略"按钮标记为已授权
- 下次打开 APP 时不会再显示该提示

---

## 备选方案（可选实现）

如果需要更可靠的方案，可以考虑：
1. 使用 ROOT 检测（不推荐，需要 root 权限）
2. 应用内周期性检测自启动服务是否存活（间接检测）
3. 依赖第三方库（如 AppAlias 或自启动检测库）
