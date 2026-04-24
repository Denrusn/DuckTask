package com.ducktask.app.util

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class AppPermissionType {
    NOTIFICATION,
    EXACT_ALARM,
    OVERLAY,
    FULL_SCREEN,
    BATTERY_OPTIMIZATION,
    AUTO_START
}

data class AppPermissionIssue(
    val type: AppPermissionType,
    val title: String,
    val description: String,
    val actionLabel: String
)

object PermissionUtils {
    fun findPermissionIssues(context: Context): List<AppPermissionIssue> {
        val issues = mutableListOf<AppPermissionIssue>()
        val notificationManager = NotificationManagerCompat.from(context)
        val notificationsGranted = notificationManager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (!notificationsGranted) {
            issues += AppPermissionIssue(
                type = AppPermissionType.NOTIFICATION,
                title = "通知权限未开启",
                description = "提醒需要通知权限，否则普通提醒和强提醒都不会弹出。",
                actionLabel = "开启通知"
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                issues += AppPermissionIssue(
                    type = AppPermissionType.EXACT_ALARM,
                    title = "精确定时权限未开启",
                    description = "DuckTask 需要精确定时权限，才能尽量按时触发提醒。",
                    actionLabel = "开启精确定时"
                )
            }
        }

        if (!canDrawOverlay(context)) {
            issues += AppPermissionIssue(
                type = AppPermissionType.OVERLAY,
                title = "悬浮窗权限未开启",
                description = "强提醒在 App 退到后台时，需要悬浮窗权限才能直接弹出覆盖提醒。",
                actionLabel = "开启悬浮窗"
            )
        }

        if (Build.VERSION.SDK_INT >= 34) {
            val notificationService = context.getSystemService(NotificationManager::class.java)
            if (!notificationService.canUseFullScreenIntent()) {
                issues += AppPermissionIssue(
                    type = AppPermissionType.FULL_SCREEN,
                    title = "强提醒弹窗权限未开启",
                    description = "强提醒需要全屏提醒权限，才能在触发时弹出强制确认窗口。",
                    actionLabel = "开启强提醒"
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                issues += AppPermissionIssue(
                    type = AppPermissionType.BATTERY_OPTIMIZATION,
                    title = "电池优化白名单未开启",
                    description = "建议把 DuckTask 加入电池优化白名单，降低系统长时间后台运行时误杀提醒服务的概率。",
                    actionLabel = "加入白名单"
                )
            }
        }

        if (shouldShowAutoStartGuide(context)) {
            issues += AppPermissionIssue(
                type = AppPermissionType.AUTO_START,
                title = "建议开启自启动",
                description = "部分厂商会限制应用后台拉起。请在系统自启动管理页允许 DuckTask 自启动；系统无法直接确认是否已开启，如已开启可忽略。",
                actionLabel = "自启动设置"
            )
        }

        return issues
    }

    fun buildSettingsIntents(context: Context, type: AppPermissionType): List<Intent> {
        val packageUri = Uri.parse("package:${context.packageName}")
        return when (type) {
            AppPermissionType.NOTIFICATION -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                },
                appDetailsIntent(packageUri)
            )
            AppPermissionType.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri),
                        appDetailsIntent(packageUri)
                    )
                } else {
                    listOf(appDetailsIntent(packageUri))
                }
            }
            AppPermissionType.OVERLAY -> listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
                appDetailsIntent(packageUri)
            )
            AppPermissionType.FULL_SCREEN -> {
                if (Build.VERSION.SDK_INT >= 34) {
                    listOf(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri),
                        appDetailsIntent(packageUri)
                    )
                } else {
                    listOf(appDetailsIntent(packageUri))
                }
            }
            AppPermissionType.BATTERY_OPTIMIZATION -> buildBatteryOptimizationIntents(packageUri)
            AppPermissionType.AUTO_START -> buildAutoStartIntents(packageUri)
        }
    }

    private fun buildBatteryOptimizationIntents(packageUri: Uri): List<Intent> {
        val intents = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intents += Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = packageUri
            }
            intents += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        intents += appDetailsIntent(packageUri)
        return intents
    }

    private fun buildAutoStartIntents(packageUri: Uri): List<Intent> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                intents += explicitIntent(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                intents += explicitIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                intents += explicitIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
                intents += explicitIntent(
                    "com.oplus.startupapp",
                    "com.oplus.startupapp.view.StartupAppListActivity"
                )
                intents += explicitIntent(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.startupapp.view.StartupAppListActivity"
                )
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                intents += explicitIntent(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
                intents += explicitIntent(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intents += explicitIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                intents += explicitIntent(
                    "com.honor.systemmanager",
                    "com.honor.systemmanager.optimize.process.ProtectActivity"
                )
            }
            manufacturer.contains("meizu") -> {
                intents += explicitIntent(
                    "com.meizu.safe",
                    "com.meizu.safe.permission.PermissionMainActivity"
                )
            }
        }

        intents += appDetailsIntent(packageUri)
        return intents
    }

    private fun explicitIntent(packageName: String, className: String): Intent {
        return Intent().apply {
            component = ComponentName(packageName, className)
        }
    }

    private fun appDetailsIntent(packageUri: Uri): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

    fun canDrawOverlay(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun isDeviceLocked(context: Context): Boolean {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        return keyguardManager?.isKeyguardLocked == true
    }

    private fun shouldShowAutoStartGuide(context: Context): Boolean {
        if (PermissionGuideStore.isAutoStartAcknowledged(context)) return false
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("oppo") ||
            manufacturer.contains("realme") ||
            manufacturer.contains("oneplus") ||
            manufacturer.contains("vivo") ||
            manufacturer.contains("iqoo") ||
            manufacturer.contains("huawei") ||
            manufacturer.contains("honor") ||
            manufacturer.contains("meizu")
    }
}
