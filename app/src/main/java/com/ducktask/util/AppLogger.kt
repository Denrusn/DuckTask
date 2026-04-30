package com.ducktask.app.util

import android.content.Context
import android.util.Log
import com.ducktask.app.data.local.AppDatabase
import com.ducktask.app.domain.model.AppRuntimeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppLogger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun info(tag: String, message: String, details: String? = null) {
        Log.i(tag, message)
        write("INFO", tag, message, details)
    }

    fun warn(tag: String, message: String, details: String? = null) {
        Log.w(tag, message)
        write("WARN", tag, message, details)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        write(
            level = "ERROR",
            tag = tag,
            message = message,
            details = throwable?.stackTraceToString()
        )
    }

    private fun write(level: String, tag: String, message: String, details: String?) {
        val context = appContext ?: return
        scope.launch {
            runCatching {
                AppDatabase.getInstance(context).appRuntimeLogDao().insert(
                    AppRuntimeLog(
                        level = level,
                        tag = tag,
                        message = message,
                        details = details
                    )
                )
            }
        }
    }
}
