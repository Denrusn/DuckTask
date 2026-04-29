# DuckTask GitHub Actions CI/CD 部署计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将代码提交到 GitHub 并配置 GitHub Actions 自动构建 APK

**Architecture:** 使用 GitHub Actions 的 ubuntu-latest 环境，搭配 Android SDK 26 和 Gradle 8.x 构建 APK

**Tech Stack:** GitHub Actions, Android Gradle Plugin, Gradle Wrapper

---

## Task 1: 创建 GitHub Actions 工作流

**Files:**
- Create: `.github/workflows/android.yml`

- [ ] **Step 1: 创建工作流目录和文件**

```bash
mkdir -p .github/workflows
touch .github/workflows/android.yml
```

- [ ] **Step 2: 写入 GitHub Actions 配置**

```yaml
name: Android CI

on:
  push:
    branches: [ "master" ]
  pull_request:
    branches: [ "master" ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build with Gradle
        run: ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: 提交工作流文件**

```bash
git add .github/workflows/android.yml
git commit -m "ci: add GitHub Actions workflow for Android build"
```

---

## Task 2: 提交所有更改到 GitHub

**Files:**
- Modify: `app/src/main/java/com/ducktask/util/PendingOverlayManager.kt` (new)
- Modify: `app/src/main/java/com/ducktask/OverlayUnlockReceiver.kt` (new)
- Modify: `app/src/main/java/com/ducktask/AlarmReceiver.kt`
- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `.github/workflows/android.yml`

- [ ] **Step 1: 添加所有文件**

```bash
git add app/src/main/java/com/ducktask/util/PendingOverlayManager.kt
git add app/src/main/java/com/ducktask/OverlayUnlockReceiver.kt
git add app/src/main/java/com/ducktask/AlarmReceiver.kt
git add app/src/main/java/com/ducktask/StrongReminderOverlayService.kt
git add app/src/main/AndroidManifest.xml
git add .github/workflows/android.yml
```

- [ ] **Step 2: 提交代码**

```bash
git commit -m "feat: add lock screen overlay handling for periodic reminders

- Add PendingOverlayManager to save pending overlay state when device is locked
- Add OverlayUnlockReceiver to show overlay on device unlock (ACTION_USER_PRESENT)
- Fix AlarmReceiver periodic reminder branch to handle lock screen case
- Clear pending state when overlay is shown

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

- [ ] **Step 3: 推送到 GitHub**

```bash
git push origin master
```

---

## Task 3: 验证 GitHub Actions 构建

- [ ] **Step 1: 查看 Actions 状态**

打开 https://github.com/denrusn/DuckTask/actions 查看构建状态

- [ ] **Step 2: 下载构建产物**

构建成功后，从 Artifacts 下载 `app-debug.apk`

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] 代码已实现锁屏处理逻辑
   - [x] GitHub Actions 工作流配置
   - [x] 提交所有文件到 GitHub

2. **Placeholder scan:** 无占位符

3. **配置检查:**
   - [x] Java 17 与 Gradle 8.x 兼容
   - [x] APK 输出路径正确
   - [x] Artifact 命名正确

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-04-28-github-ci-deploy.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**