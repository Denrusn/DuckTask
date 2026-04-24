package com.ducktask.app.util

import android.content.Context

object PermissionGuideStore {
    private const val PREFS_NAME = "ducktask_permission_guides"
    private const val KEY_AUTO_START_ACKNOWLEDGED = "auto_start_acknowledged"

    fun isAutoStartAcknowledged(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_START_ACKNOWLEDGED, false)
    }

    fun acknowledgeAutoStart(context: Context) {
        prefs(context).edit().putBoolean(KEY_AUTO_START_ACKNOWLEDGED, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
