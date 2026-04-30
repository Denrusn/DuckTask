package com.ducktask.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ducktask.app.domain.model.AppRuntimeLog
import com.ducktask.app.domain.model.ReminderExecutionLog
import com.ducktask.app.domain.model.ReminderMode
import com.ducktask.app.domain.model.Task
import com.ducktask.app.domain.model.TaskStatus
import com.ducktask.app.ui.theme.DuckOrange
import com.ducktask.app.ui.theme.Error
import com.ducktask.app.ui.theme.Success
import com.ducktask.app.util.AppPermissionIssue
import com.ducktask.app.util.AppPermissionType
import com.ducktask.app.util.formatEditableDateTime
import com.ducktask.app.util.formatReminderTime
import com.ducktask.app.util.formatAbsoluteTime
import kotlinx.coroutines.delay

private enum class MainDestination {
    HOME,
    EDIT,
    LOGS,
    PERMISSIONS
}

private enum class LogTab {
    EXECUTION,
    RUNTIME
}

private enum class TaskTab {
    PENDING,
    ALERTING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    tasks: List<Task>,
    executionLogs: List<ReminderExecutionLog>,
    runtimeLogs: List<AppRuntimeLog>,
    permissionIssues: List<AppPermissionIssue>,
    onResolvePermission: (AppPermissionType) -> Unit,
    onAcknowledgePermission: (AppPermissionType) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var destination by rememberSaveable { mutableStateOf(MainDestination.HOME.name) }
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var logTab by rememberSaveable { mutableStateOf(LogTab.EXECUTION.name) }
    var deletingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var taskTab by rememberSaveable { mutableStateOf(TaskTab.PENDING) }
    var showSuccess by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val currentDestination = MainDestination.valueOf(destination)
    val currentLogTab = LogTab.valueOf(logTab)
    val editingTask = tasks.firstOrNull { it.taskId == editingTaskId }
    val deletingTask = tasks.firstOrNull { it.taskId == deletingTaskId }
    val openLogTab: (LogTab) -> Unit = {
        logTab = it.name
        destination = MainDestination.LOGS.name
    }

    BackHandler(enabled = currentDestination != MainDestination.HOME) {
        editingTaskId = null
        destination = MainDestination.HOME.name
    }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            showSuccess = true
            delay(2200)
            showSuccess = false
            viewModel.onEvent(MainUiEvent.ClearSuccess)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            showError = true
            delay(3000)
            showError = false
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentDestination) {
                            MainDestination.HOME -> "DuckTask"
                            MainDestination.EDIT -> "编辑提醒"
                            MainDestination.LOGS -> "日志中心"
                            MainDestination.PERMISSIONS -> "运行保障"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (currentDestination != MainDestination.HOME) {
                        IconButton(
                            onClick = {
                                destination = MainDestination.HOME.name
                                editingTaskId = null
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (currentDestination == MainDestination.HOME) {
                        IconButton(onClick = { openLogTab(LogTab.EXECUTION) }) {
                            Icon(Icons.Default.History, contentDescription = "执行记录")
                        }
                        IconButton(onClick = { openLogTab(LogTab.RUNTIME) }) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = "运行日志",
                                tint = if (runtimeLogs.isNotEmpty()) DuckOrange else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { destination = MainDestination.PERMISSIONS.name }) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "运行保障",
                                tint = if (permissionIssues.isNotEmpty()) Error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (currentDestination == MainDestination.LOGS) {
                        IconButton(onClick = { logTab = LogTab.EXECUTION.name }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "执行记录",
                                tint = if (currentLogTab == LogTab.EXECUTION) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { logTab = LogTab.RUNTIME.name }) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = "运行日志",
                                tint = if (currentLogTab == LogTab.RUNTIME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            when (currentDestination) {
                MainDestination.HOME -> HomeContent(
                    uiState = uiState,
                    tasks = tasks,
                    currentTaskTab = taskTab,
                    onTaskTabChange = { taskTab = it },
                    onInputChange = { viewModel.onEvent(MainUiEvent.InputChanged(it)) },
                    onReminderModeChange = { viewModel.onEvent(MainUiEvent.CreateReminderModeChanged(it)) },
                    onSubmit = {
                        focusManager.clearFocus()
                        viewModel.onEvent(MainUiEvent.SubmitTask)
                    },
                    onSubmitWithOptions = { a, b, c, d, e, f ->
                        viewModel.onEvent(MainUiEvent.SubmitTaskWithOptions(a, b, c, d, e, f))
                    },
                    onDelete = { deletingTaskId = it.taskId },
                    onDone = { viewModel.onEvent(MainUiEvent.MarkDone(it)) },
                    onEdit = {
                        editingTaskId = it.taskId
                        destination = MainDestination.EDIT.name
                    }
                )
                MainDestination.EDIT -> {
                    EditTaskContent(
                        task = editingTask,
                        uiState = uiState,
                        onBack = {
                            editingTaskId = null
                            destination = MainDestination.HOME.name
                        },
                        onSave = { task, event, description, dateTime, mode ->
                            viewModel.updateTask(task, event, description, dateTime, mode) { saved ->
                                if (saved) {
                                    editingTaskId = null
                                    destination = MainDestination.HOME.name
                                }
                            }
                        }
                    )
                }
                MainDestination.LOGS -> LogPageContent(
                    executionLogs = executionLogs,
                    runtimeLogs = runtimeLogs,
                    currentLogTab = currentLogTab,
                    onLogTabChange = { logTab = it.name }
                )
                MainDestination.PERMISSIONS -> PermissionCenterContent(
                    permissionIssues = permissionIssues,
                    onResolvePermission = onResolvePermission,
                    onAcknowledgePermission = onAcknowledgePermission
                )
            }

            AnimatedVisibility(
                visible = showSuccess,
                enter = fadeIn() + scaleIn(initialScale = 0.8f) + slideInVertically { -it / 2 },
                exit = fadeOut() + scaleOut(targetScale = 0.8f) + slideOutVertically { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Success,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 渐变背景装饰圆角盒子
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = uiState.successMessage ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showError,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Error,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (deletingTask != null) {
                DeleteConfirmDialog(
                    task = deletingTask,
                    onDismiss = { deletingTaskId = null },
                    onConfirm = {
                        deletingTaskId = null
                        viewModel.onEvent(MainUiEvent.DeleteTask(deletingTask))
                    }
                )
            }
        }
    }
}

@Composable
private fun LogPageContent(
    executionLogs: List<ReminderExecutionLog>,
    runtimeLogs: List<AppRuntimeLog>,
    currentLogTab: LogTab,
    onLogTabChange: (LogTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        when (currentLogTab) {
            LogTab.EXECUTION -> ExecutionLogContent(logs = executionLogs)
            LogTab.RUNTIME -> RuntimeLogContent(logs = runtimeLogs)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    uiState: MainUiState,
    tasks: List<Task>,
    currentTaskTab: TaskTab,
    onTaskTabChange: (TaskTab) -> Unit,
    onInputChange: (String) -> Unit,
    onReminderModeChange: (Int) -> Unit,
    onSubmit: () -> Unit,
    onSubmitWithOptions: (Boolean, Boolean, Int, Boolean, Int, Int) -> Unit,
    onDelete: (Task) -> Unit,
    onDone: (Task) -> Unit,
    onEdit: (Task) -> Unit
) {
    var showInputSheet by remember { mutableStateOf(false) }

    // Filter tasks based on current tab
    val filteredTasks = when (currentTaskTab) {
        TaskTab.PENDING -> tasks.filter { it.status == TaskStatus.PENDING }
        TaskTab.ALERTING -> tasks.filter { it.status == TaskStatus.ALERTING }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            if (tasks.isEmpty()) {
                EmptyState()
            } else {
                // Tab selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val pendingCount = tasks.count { it.status == TaskStatus.PENDING }
                    val alertingCount = tasks.count { it.status == TaskStatus.ALERTING }

                    FilterChip(
                        selected = currentTaskTab == TaskTab.PENDING,
                        onClick = { onTaskTabChange(TaskTab.PENDING) },
                        label = { Text("待提醒 ($pendingCount)") }
                    )
                    FilterChip(
                        selected = currentTaskTab == TaskTab.ALERTING,
                        onClick = { onTaskTabChange(TaskTab.ALERTING) },
                        label = { Text("已提醒待完成 ($alertingCount)") }
                    )
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredTasks, key = { it.taskId }) { task ->
                        TaskCard(
                            task = task,
                            onDelete = { onDelete(task) },
                            onDone = { onDone(task) },
                            onEdit = { onEdit(task) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = { showInputSheet = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("添加提醒") }
        )
    }

    if (showInputSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInputSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            InputCard(
                inputText = uiState.inputText,
                createReminderMode = uiState.createReminderMode,
                isLoading = uiState.isLoading,
                onInputChange = onInputChange,
                onReminderModeChange = onReminderModeChange,
                onSubmit = {
                    onSubmit()
                    // Don't close sheet here - let ViewModel handle success/failure
                    // Sheet will be closed when error is cleared
                },
                onSubmitWithOptions = onSubmitWithOptions
            )
        }
    }
}

@Composable
private fun PermissionCenterContent(
    permissionIssues: List<AppPermissionIssue>,
    onResolvePermission: (AppPermissionType) -> Unit,
    onAcknowledgePermission: (AppPermissionType) -> Unit
) {
    // Sort permissions by importance: NOTIFICATION > EXACT_ALARM > OVERLAY > FULL_SCREEN > BATTERY_OPTIMIZATION > AUTO_START
    val sortedPermissions = remember(permissionIssues) {
        permissionIssues.sortedBy { issue ->
            when (issue.type) {
                AppPermissionType.NOTIFICATION -> 0
                AppPermissionType.EXACT_ALARM -> 1
                AppPermissionType.OVERLAY -> 2
                AppPermissionType.FULL_SCREEN -> 3
                AppPermissionType.BATTERY_OPTIMIZATION -> 4
                AppPermissionType.AUTO_START -> 5
            }
        }
    }

    // Guided flow state management
    var guidedPermissions by remember { mutableStateOf<List<AppPermissionType>>(emptyList()) }
    var guidedIndex by mutableIntStateOf(0)
    var isProcessing by remember { mutableStateOf(false) }
    var currentProcessingPermission by remember { mutableStateOf<AppPermissionType?>(null) }

    // Helper to get display name for permission type
    val permissionDisplayName = { type: AppPermissionType ->
        when (type) {
            AppPermissionType.NOTIFICATION -> "通知权限"
            AppPermissionType.EXACT_ALARM -> "精确闹钟权限"
            AppPermissionType.OVERLAY -> "悬浮窗权限"
            AppPermissionType.FULL_SCREEN -> "全屏权限"
            AppPermissionType.BATTERY_OPTIMIZATION -> "电池优化权限"
            AppPermissionType.AUTO_START -> "自启动权限"
        }
    }

    // Automatically move to next permission when previous one is resolved
    // Process only ONE permission per trigger to prevent re-entrancy issues
    LaunchedEffect(permissionIssues, guidedPermissions, guidedIndex) {
        if (guidedPermissions.isEmpty() || isProcessing) return@LaunchedEffect

        // Find the next unresolved permission in our queue
        while (guidedIndex < guidedPermissions.size) {
            val nextType = guidedPermissions[guidedIndex]
            // Check if this permission still needs resolution
            if (permissionIssues.none { it.type == nextType }) {
                // Permission was resolved, move to next
                guidedIndex++
            } else {
                // Found next unresolved permission, open it
                currentProcessingPermission = nextType
                isProcessing = true
                onResolvePermission(nextType)
                guidedIndex++
                isProcessing = false
                return@LaunchedEffect  // Exit to let user authorize, effect will re-trigger on return
            }
        }
        // All done
        guidedPermissions = emptyList()
        guidedIndex = 0
        currentProcessingPermission = null
    }

    // Start guided permission flow - opens the first unresolved permission
    val startGuidedPermissionFlow: () -> Unit = {
        // Filter out AUTO_START as it can't be auto-opened
        guidedPermissions = sortedPermissions
            .filter { it.type != AppPermissionType.AUTO_START }
            .map { it.type }
        guidedIndex = 0
        if (guidedPermissions.isNotEmpty()) {
            onResolvePermission(guidedPermissions[0])
            guidedIndex++
        }
    }

    val isGuidedFlowActive = guidedPermissions.isNotEmpty()

    if (sortedPermissions.isEmpty()) {
        EmptyState(
            title = "运行保障已就绪",
            subtitle = "通知、定时和后台提醒相关权限都已配置完成。"
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("建议优先完成这些设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "它们会影响通知准时性、后台悬浮窗强提醒以及系统长时间保活。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // One-click authorization button
        item {
            Spacer(modifier = Modifier.height(4.dp))
            ExtendedFloatingActionButton(
                onClick = { if (!isProcessing) startGuidedPermissionFlow() },
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (isGuidedFlowActive) DuckOrange else MaterialTheme.colorScheme.primary,
                contentColor = if (isGuidedFlowActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary,
                expanded = !isProcessing,
                icon = {
                    if (isGuidedFlowActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                text = {
                    val currentPerm = currentProcessingPermission?.let { permissionDisplayName(it) } ?: ""
                    Text(
                        text = if (isGuidedFlowActive) {
                            if (currentPerm.isNotEmpty()) "正在处理: $currentPerm" else "正在引导授权中..."
                        } else {
                            "一键授权全部权限"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }

        items(sortedPermissions, key = { it.type.name }) { issue ->
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Error.copy(alpha = 0.10f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                                tint = Error
                            )
                        }
                        Column {
                            Text(issue.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = issue.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (issue.type == AppPermissionType.AUTO_START) {
                            TextButton(onClick = { onAcknowledgePermission(issue.type) }) {
                                Text("我已开启")
                            }
                        }
                        Button(onClick = { onResolvePermission(issue.type) }) {
                            Text(issue.actionLabel)
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun DeleteConfirmDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除提醒？") },
        text = {
            Text(
                text = "将删除“${task.event}”这条提醒。删除后不会恢复。",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun InputCard(
    inputText: String,
    createReminderMode: Int,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onReminderModeChange: (Int) -> Unit,
    onSubmit: () -> Unit,
    onSubmitWithOptions: (Boolean, Boolean, Int, Boolean, Int, Int) -> Unit
) {
    var alarmEnabled by remember { mutableStateOf(false) }
    var alarmRingtone by remember { mutableStateOf(true) }
    var alarmVibrateCount by remember { mutableIntStateOf(5) }
    var alertLoopEnabled by remember { mutableStateOf(false) }
    var alertLoopInterval by remember { mutableIntStateOf(1) }
    var alertLoopMaxCount by remember { mutableIntStateOf(5) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "输入提醒内容，如：周五晚上提醒我打电话给老妈",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        IconButton(onClick = { onInputChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))
            ReminderModePicker(selectedMode = createReminderMode, onModeSelected = onReminderModeChange)

            Spacer(modifier = Modifier.height(8.dp))

            // 提醒样式选择
            Text(
                text = "提醒样式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !alarmEnabled,
                    onClick = { alarmEnabled = false },
                    label = { Text("普通提醒") }
                )
                FilterChip(
                    selected = alarmEnabled,
                    onClick = { alarmEnabled = true },
                    label = { Text("闹钟样式") }
                )
            }

            // 闹钟样式选项
            if (alarmEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("铃声", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = alarmRingtone, onCheckedChange = { alarmRingtone = it })
                        }

                        Text(
                            text = "震动次数: $alarmVibrateCount",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Slider(
                            value = alarmVibrateCount.toFloat(),
                            onValueChange = { alarmVibrateCount = it.toInt() },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                    }
                }
            }

            // 循环提醒选项（强提醒时显示）
            if (createReminderMode == ReminderMode.STRONG) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("循环提醒", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = alertLoopEnabled, onCheckedChange = { alertLoopEnabled = it })
                }

                if (alertLoopEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "间隔: $alertLoopInterval 分钟",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = alertLoopInterval.toFloat(),
                                onValueChange = { alertLoopInterval = it.toInt() },
                                valueRange = 1f..10f,
                                steps = 8
                            )

                            Text(
                                text = "最大次数: $alertLoopMaxCount 次",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Slider(
                                value = alertLoopMaxCount.toFloat(),
                                onValueChange = { alertLoopMaxCount = it.toInt() },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (alarmEnabled || alertLoopEnabled) {
                        // 使用带选项的提交
                        onSubmitWithOptions(alarmEnabled, alarmRingtone, alarmVibrateCount, alertLoopEnabled, alertLoopInterval, alertLoopMaxCount)
                    } else {
                        onSubmit()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading && inputText.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("添加提醒", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ReminderModePicker(selectedMode: Int, onModeSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = selectedMode == ReminderMode.NORMAL,
            onClick = { onModeSelected(ReminderMode.NORMAL) },
            label = { Text("普通提醒") }
        )
        FilterChip(
            selected = selectedMode == ReminderMode.STRONG,
            onClick = { onModeSelected(ReminderMode.STRONG) },
            label = { Text("强提醒") }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    task: Task,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit
) {
    val accent = if (task.reminderMode == ReminderMode.STRONG) Error else DuckOrange
    val statusTone = if (task.isAlerting()) Error else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TaskMetaChip(text = task.reminderModeLabel(), tone = accent)
                        TaskMetaChip(
                            text = if (task.isAlerting()) "待处理" else "待提醒",
                            tone = statusTone
                        )
                        if (task.hasRepeat()) {
                            TaskMetaChip(
                                text = task.repeatRule()?.toHumanText().orEmpty(),
                                tone = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = task.event,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (task.description.isNotBlank() && task.description != task.event) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = accent.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = accent
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (task.isAlerting()) {
                                    "已触发：${formatAbsoluteTime(task.nextRunTime)}"
                                } else {
                                    "下次执行：${formatAbsoluteTime(task.nextRunTime)}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (task.isAlerting()) {
                                    "等待你手动确认处理"
                                } else {
                                    formatReminderTime(task.nextRunTime)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = Error.copy(alpha = 0.65f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("编辑", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Button(
                            onClick = onDone,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(if (task.isAlerting()) "已处理" else "完成")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskMetaChip(text: String, tone: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tone.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tone,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EditTaskContent(
    task: Task?,
    uiState: MainUiState,
    onBack: () -> Unit,
    onSave: (Task, String, String, String, Int) -> Unit
) {
    if (task == null) {
        EmptyState(title = "提醒不存在", subtitle = "这条提醒可能已经被删除。")
        return
    }

    var event by remember(task.taskId, task.event) { mutableStateOf(task.event) }
    var description by remember(task.taskId, task.description) { mutableStateOf(task.description) }
    var nextRunTime by remember(task.taskId, task.nextRunTime) { mutableStateOf(formatEditableDateTime(task.nextRunTime)) }
    var reminderMode by remember(task.taskId, task.reminderMode) { mutableStateOf(task.reminderMode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = event,
                    onValueChange = { event = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("事件") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") }
                )
                OutlinedTextField(
                    value = nextRunTime,
                    onValueChange = { nextRunTime = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("下次提醒时间") },
                    supportingText = { Text("格式：yyyy-MM-dd HH:mm") }
                )
                ReminderModePicker(selectedMode = reminderMode, onModeSelected = { reminderMode = it })
                if (task.hasRepeat()) {
                    Text(
                        text = "重复规则：${task.repeatRule()?.toHumanText().orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DuckOrange
                    )
                }
                uiState.errorMessage?.let {
                    Text(text = it, color = Error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onBack) {
                        Text("取消")
                    }
                    Button(
                        onClick = { onSave(task, event, description, nextRunTime, reminderMode) },
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionLogContent(logs: List<ReminderExecutionLog>) {
    if (logs.isEmpty()) {
        EmptyState(title = "暂无执行记录", subtitle = "提醒真正触发后，记录会显示在这里。")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(12.dp)) }
        items(logs, key = { it.id }) { log ->
            val accent = if (log.reminderMode == ReminderMode.STRONG) Error else DuckOrange
            val statusTone = if (log.acknowledgedAt != null) Success else DuckOrange
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.16f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.event, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "触发于 ${formatAbsoluteTime(log.triggeredAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                            )
                        }
                        LogChip(text = log.reminderModeLabel(), tone = accent)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogChip(text = log.dismissMethodLabel(), tone = statusTone)
                        log.nextRunTime?.let {
                            LogChip(
                                text = "下次 ${formatReminderTime(it)}",
                                tone = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ExecutionMetaRow(label = "提醒方式", value = log.reminderModeLabel())
                            ExecutionMetaRow(label = "处理状态", value = log.dismissMethodLabel())
                            log.nextRunTime?.let {
                                ExecutionMetaRow(label = "下次执行", value = formatAbsoluteTime(it))
                            }
                            log.acknowledgedAt?.let {
                                ExecutionMetaRow(label = "处理时间", value = formatAbsoluteTime(it))
                            }
                            if (log.description.isNotBlank()) {
                                Text(
                                    text = "备注",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                                )
                                Text(
                                    text = log.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun LogChip(text: String, tone: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tone.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tone,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ExecutionMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f)
        )
    }
}

@Composable
private fun RuntimeLogContent(logs: List<AppRuntimeLog>) {
    val clipboardManager = LocalClipboardManager.current
    if (logs.isEmpty()) {
        EmptyState(title = "暂无运行日志", subtitle = "出现错误、解析失败或弹窗异常时，日志会记录在这里。")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    val all = logs.joinToString("\n\n----------------\n\n") { it.toCopyText() }
                    clipboardManager.setText(AnnotatedString(all))
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("复制全部")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(logs, key = { it.id }) { log ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    "${log.level} · ${log.tag}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(onClick = { clipboardManager.setText(AnnotatedString(log.toCopyText())) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("复制")
                            }
                        }
                        Text("时间：${formatAbsoluteTime(log.createdAt)}", style = MaterialTheme.typography.bodySmall)
                        Text(log.message, style = MaterialTheme.typography.bodyMedium)
                        if (!log.details.isNullOrBlank()) {
                            Text(
                                log.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun EmptyState(
    title: String = "暂无待提醒的任务",
    subtitle: String = "输入如“明天下午3点提醒我开会”来创建提醒"
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
