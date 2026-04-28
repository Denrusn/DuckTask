# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DuckTask is an Android reminder/task application with Chinese natural language time parsing. Users can create reminders using expressions like "明天下午3点提醒我开会" or "每周一上午10点提醒我".

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "com.ducktask.app.parser.TimeParserTest"

# Clean and build
./gradlew clean assembleDebug
```

## Architecture

### Domain Layer (`domain/model/`)
- **Task** - Core entity with fields: `taskId`, `event`, `description`, `reminderTime`, `repeat`, `reminderMode`, `triggerType`, `status`, `nextRunTime`
- **RepeatRule** - JSON-serialized repeat configuration (years/months/weeks/days/hours/minutes/seconds)
- **ReminderExecutionLog** / **AppRuntimeLog** - Audit/logging entities

### Data Layer
- **TaskRepository** - Central hub for task operations; coordinates between DAOs and ReminderScheduler
- **Room DAOs** - TaskDao, ReminderLogDao, AppRuntimeLogDao
- **ReminderScheduler** - Wraps AlarmManager; schedules exact alarms via AlarmReceiver

### Parser (`parser/`)
- **TimeParser** - Entry point, delegates to RuleParser
- **RuleParser** - State machine parsing Chinese datetime expressions
- **ChineseNumberNormalizer** - Converts Chinese numerals (一二三, 壹贰叁, 二十) to digits

Parser parses in phases: repeat rules → relative duration → relative date → absolute date → weekday → day period → clock time.

### UI Layer (`ui/`)
- **MainScreen** - Compose UI with task list, creation dialog, and edit functionality
- **MainViewModel** - State management using Kotlin Flow
- Theme files follow standard Material3 Compose theming

### Reminder System
1. **ReminderScheduler** sets exact alarms via AlarmManager
2. **AlarmReceiver** triggers on alarm, shows notification via **DuckTaskNotifications**
3. For STRONG reminders: **StrongReminderOverlayService** displays a full-screen overlay (requires SYSTEM_ALERT_WINDOW permission)
4. **ReminderActionReceiver** handles user acknowledgment (notification action button)
5. Boot receiver reschedules all pending tasks after device restart

**Note:** Strong reminders use overlay only (no in-app Activity fallback). The overlay shows a circular hold-to-dismiss button with ring progress animation and particle burst effects on completion.

### Key Constants
- `DEFAULT_USER_ID = "local"` - Single-user app, all tasks belong to this ID
- `TRIGGER_TYPE_DATE` / `CALENDAR` / `INTERVAL` - Distinguishes one-time, calendar-based repeat, and interval-based repeat
- `ReminderMode.NORMAL (0)` / `STRONG (1)` - Normal uses notifications only; Strong adds overlay

## Database Schema

- `reminder_tasks` - Main task table with unique index on `taskId`
- `reminder_execution_logs` - Log of each triggered reminder with acknowledge status
- `app_runtime_logs` - Application-level logging for debugging

## Dependencies

- Jetpack Compose with Material3
- Room Database (KSP)
- Kotlin Coroutines & Flow
- AndroidX Lifecycle components
