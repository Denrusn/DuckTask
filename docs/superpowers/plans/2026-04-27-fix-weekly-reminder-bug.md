# 修复每周重复提醒 Bug 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复"每周X早上Y点Z分"类型输入在时间已过时的错误计算问题

**Architecture:** 修改 `nextWeekday` 函数，在判断"今天是否已过"时，正确使用用户指定的小时和分钟（而非默认值）

**Tech Stack:** Kotlin, Java Time API

---

## Bug 根因分析

**用户场景：**
- 当前时间：2026年4月27日（周一）08:02
- 输入："每周一早上8点30提醒我打卡"
- 预期：应该从今天 08:30 开始提醒
- 实际：设置了 5月4日（下周一）08:30

**问题位置：** `nextWeekday` 函数 (Parser.kt:594-604)

**问题原因：**
1. 解析顺序：repeat → weekday → dayPeriod → clockTime
2. 当 `parseWeekday` 调用 `nextWeekday` 时，`state.hour` 还是 null，`state.minute` = 0
3. `nextWeekday` 使用 `defaultHour = 8, minute = 0` 计算 `sameDayTime = 今天08:00`
4. 因为 08:00 < 08:02，所以函数返回了下周一
5. 但用户实际说的是 08:30（还没到！）

---

## File Structure

- Modify: `app/src/main/java/com/ducktask/parser/Parser.kt`
- Test: `app/src/test/java/com/ducktask/app/parser/TimeParserTest.kt`

---

## Task 1: 添加单元测试复现 Bug

**Files:**
- Modify: `app/src/test/java/com/ducktask/app/parser/TimeParserTest.kt`

- [ ] **Step 1: 添加测试用例复现 Bug**

在 TimeParserTest.kt 中添加测试：

```kotlin
@Test
fun parsesWeeklyReminderOnSameDayMorning() {
    // 2026年4月27日（周一）08:02 设置"每周一早上8点30"
    TimeParser.setClock(Clock.fixed(
        LocalDateTime.of(2026, 4, 27, 8, 2).atZone(zone).toInstant(),
        zone
    ))
    val parsed = TimeParser.parse("每周一早上8点30提醒我打卡", LocalDateTime.of(2026, 4, 27, 8, 2))

    // 08:30 还没到（当前是08:02），所以应该返回今天08:30
    assertEquals(27, parsed.time.dayOfMonth)
    assertEquals(8, parsed.time.hour)
    assertEquals(30, parsed.time.minute)
    assertEquals(1, parsed.repeat?.weeks)
    assertEquals("打卡", parsed.event)
}

@Test
fun parsesWeeklyReminderOnSameDayAfternoon() {
    // 2026年4月27日（周一）09:00 设置"每周一早上8点30"
    TimeParser.setClock(Clock.fixed(
        LocalDateTime.of(2026, 4, 27, 9, 0).atZone(zone).toInstant(),
        zone
    ))
    val parsed = TimeParser.parse("每周一早上8点30提醒我打卡", LocalDateTime.of(2026, 4, 27, 9, 0))

    // 08:30 已经过了（当前是09:00），所以应该返回下周一
    assertEquals(4, parsed.time.monthValue)
    assertEquals(4, parsed.time.dayOfMonth)
    assertEquals(8, parsed.time.hour)
    assertEquals(30, parsed.time.minute)
    assertEquals(1, parsed.repeat?.weeks)
}
```

- [ ] **Step 2: 运行测试验证 Bug**

```bash
./gradlew testDebugUnitTest --tests "TimeParserTest.parsesWeeklyReminderOnSameDayMorning" --console=plain
```

Expected: 测试应该失败（复现 Bug）

- [ ] **Step 3: 提交**

```bash
git add app/src/test/java/com/ducktask/app/parser/TimeParserTest.kt
git commit -m "test: add test cases to reproduce weekly reminder bug"
```

---

## Task 2: 修复 nextWeekday 函数

**Files:**
- Modify: `app/src/main/java/com/ducktask/parser/Parser.kt`

- [ ] **Step 1: 修改 nextWeekday 函数签名**

在 `nextWeekday` 函数中添加参数来接收已解析的时间：

```kotlin
// 修改前
private fun nextWeekday(targetWeekday: Int, allowToday: Boolean): LocalDate {
    val current = now.dayOfWeek.value
    var delta = (targetWeekday - current + 7) % 7
    if (delta == 0 && !allowToday) delta = 7
    if (delta == 0 && allowToday) {
        val defaultHour = state.hour ?: 8
        val sameDayTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(defaultHour, state.minute, state.second))
        if (!sameDayTime.isAfter(now)) delta = 7
    }
    return now.toLocalDate().plusDays(delta.toLong())
}

// 修改后
private fun nextWeekday(targetWeekday: Int, allowToday: Boolean): LocalDate {
    val current = now.dayOfWeek.value
    var delta = (targetWeekday - current + 7) % 7
    if (delta == 0 && !allowToday) delta = 7
    if (delta == 0 && allowToday) {
        // 使用 state.hour 和 state.minute（如果已解析的话）来判断
        val targetHour = state.hour ?: 8
        val targetMinute = state.minute  // 已经是0或用户指定的值
        val sameDayTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(targetHour, targetMinute))
        if (!sameDayTime.isAfter(now)) delta = 7
    }
    return now.toLocalDate().plusDays(delta.toLong())
}
```

- [ ] **Step 2: 运行测试验证修复**

```bash
./gradlew testDebugUnitTest --tests "TimeParserTest.parsesWeeklyReminder*" --console=plain
```

Expected: 所有测试应该通过

- [ ] **Step 3: 运行完整测试确保没有回归**

```bash
./gradlew testDebugUnitTest --console=plain
```

Expected: 所有测试应该通过

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/ducktask/parser/Parser.kt
git commit -m "fix(parser): use parsed hour/minute in nextWeekday to correctly handle same-day reminders"
```

---

## Task 3: 最终验证并推送

- [ ] **Step 1: 推送代码**

```bash
git push origin HEAD
```

- [ ] **Step 2: 等待 GitHub Actions 构建**

```bash
gh run list --limit 3
```

- [ ] **Step 3: 获取 APK 下载链接**

---

## Self-Review Checklist

1. **Spec coverage:**
   - ✅ Bug 复现测试 - Task 1
   - ✅ Bug 修复 - Task 2
   - ✅ 回归测试 - Task 2

2. **Placeholder scan:** 无占位符

3. **Type consistency:** 函数参数和返回值保持一致

---

## Execution Options

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
