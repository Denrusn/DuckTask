package com.ducktask.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ducktask.app.ui.screens.MainScreen
import com.ducktask.app.ui.screens.MainViewModel
import com.ducktask.app.ui.theme.DuckTaskTheme
import com.ducktask.app.util.AppPermissionIssue
import com.ducktask.app.util.AppPermissionType
import com.ducktask.app.util.PermissionGuideStore
import com.ducktask.app.util.PermissionUtils

class MainActivity : ComponentActivity() {
    private var permissionIssues by mutableStateOf<List<AppPermissionIssue>>(emptyList())

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermissionIssues()
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissionIssues()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissionIssues()

        val app = application as DuckTaskApp

        setContent {
            DuckTaskTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModel.Factory(app.repository)
                    )
                    val tasks by viewModel.pendingTasks.collectAsState()
                    val executionLogs by viewModel.executionLogs.collectAsState()
                    val runtimeLogs by viewModel.runtimeLogs.collectAsState()

                    MainScreen(
                        viewModel = viewModel,
                        tasks = tasks,
                        executionLogs = executionLogs,
                        runtimeLogs = runtimeLogs,
                        permissionIssues = permissionIssues,
                        onResolvePermission = ::resolvePermission,
                        onAcknowledgePermission = ::acknowledgePermission
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionIssues()
    }

    private fun refreshPermissionIssues() {
        permissionIssues = PermissionUtils.findPermissionIssues(this)
    }

    private fun resolvePermission(type: AppPermissionType) {
        when (type) {
            AppPermissionType.NOTIFICATION -> {
                val canRequestRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (canRequestRuntimePermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    launchSettings(type)
                }
            }
            AppPermissionType.EXACT_ALARM,
            AppPermissionType.OVERLAY,
            AppPermissionType.FULL_SCREEN,
            AppPermissionType.BATTERY_OPTIMIZATION,
            AppPermissionType.AUTO_START -> {
                launchSettings(type)
            }
        }
    }

    private fun acknowledgePermission(type: AppPermissionType) {
        when (type) {
            AppPermissionType.AUTO_START -> {
                PermissionGuideStore.acknowledgeAutoStart(this)
                refreshPermissionIssues()
            }
            else -> Unit
        }
    }

    private fun launchSettings(type: AppPermissionType) {
        for (intent in PermissionUtils.buildSettingsIntents(this, type)) {
            val launched = runCatching {
                settingsLauncher.launch(intent)
                true
            }.getOrElse { false }
            if (launched) return
        }

        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching {
            settingsLauncher.launch(fallback)
        }.onFailure {
            permissionIssues = permissionIssues.mapNotNull { issue ->
                if (issue.type == type) {
                    issue.copy(description = "${issue.description} 当前机型未找到直达设置入口，请在系统应用详情页手动开启。")
                } else {
                    issue
                }
            }
        }
    }
}
