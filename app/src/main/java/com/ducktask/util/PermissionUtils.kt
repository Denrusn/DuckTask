package com.ducktask.app.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class AppPermissionType {
    NOTIFICATION,
    EXACT_ALARM,
    FULL_SCREEN
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

        return issues
    }

    fun buildSettingsIntent(context: Context, type: AppPermissionType): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        return when (type) {
            AppPermissionType.NOTIFICATION -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            AppPermissionType.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                }
            }
            AppPermissionType.FULL_SCREEN -> {
                if (Build.VERSION.SDK_INT >= 34) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                }
            }
        }
    }
}
