package com.ducktask.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ducktask.app.domain.model.AppRuntimeLog
import com.ducktask.app.domain.model.ReminderExecutionLog
import com.ducktask.app.domain.model.ReminderMode
import com.ducktask.app.domain.model.Task
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
    LOGS
}

private enum class LogTab {
    EXECUTION,
    RUNTIME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    tasks: List<Task>,
    executionLogs: List<ReminderExecutionLog>,
    runtimeLogs: List<AppRuntimeLog>,
    permissionIssues: List<AppPermissionIssue>,
    onResolvePermission: (AppPermissionType) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var destination by rememberSaveable { mutableStateOf(MainDestination.HOME.name) }
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var logTab by rememberSaveable { mutableStateOf(LogTab.EXECUTION.name) }
    var showSuccess by remember { mutableStateOf(false) }
    val currentDestination = MainDestination.valueOf(destination)
    val currentLogTab = LogTab.valueOf(logTab)
    val editingTask = tasks.firstOrNull { it.taskId == editingTaskId }
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
                    permissionIssues = permissionIssues,
                    onResolvePermission = onResolvePermission,
                    onInputChange = { viewModel.onEvent(MainUiEvent.InputChanged(it)) },
                    onReminderModeChange = { viewModel.onEvent(MainUiEvent.CreateReminderModeChanged(it)) },
                    onSubmit = {
                        focusManager.clearFocus()
                        viewModel.onEvent(MainUiEvent.SubmitTask)
                    },
                    executionLogCount = executionLogs.size,
                    runtimeLogCount = runtimeLogs.size,
                    onOpenExecutionLogs = { openLogTab(LogTab.EXECUTION) },
                    onOpenRuntimeLogs = { openLogTab(LogTab.RUNTIME) },
                    onDelete = { viewModel.onEvent(MainUiEvent.DeleteTask(it)) },
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
            }

            AnimatedVisibility(
                visible = showSuccess,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Success,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = uiState.successMessage ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = currentLogTab == LogTab.EXECUTION,
                onClick = { onLogTabChange(LogTab.EXECUTION) },
                label = { Text("执行记录") }
            )
            FilterChip(
                selected = currentLogTab == LogTab.RUNTIME,
                onClick = { onLogTabChange(LogTab.RUNTIME) },
                label = { Text("运行日志") }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (currentLogTab) {
            LogTab.EXECUTION -> ExecutionLogContent(logs = executionLogs)
            LogTab.RUNTIME -> RuntimeLogContent(logs = runtimeLogs)
        }
    }
}

@Composable
private fun HomeContent(
    uiState: MainUiState,
    tasks: List<Task>,
    permissionIssues: List<AppPermissionIssue>,
    onResolvePermission: (AppPermissionType) -> Unit,
    onInputChange: (String) -> Unit,
    onReminderModeChange: (Int) -> Unit,
    onSubmit: () -> Unit,
    executionLogCount: Int,
    runtimeLogCount: Int,
    onOpenExecutionLogs: () -> Unit,
    onOpenRuntimeLogs: () -> Unit,
    onDelete: (Task) -> Unit,
    onDone: (Task) -> Unit,
    onEdit: (Task) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        if (permissionIssues.isNotEmpty()) {
            PermissionIssueCard(permissionIssues = permissionIssues, onResolvePermission = onResolvePermission)
            Spacer(modifier = Modifier.height(16.dp))
        }

        InputCard(
            inputText = uiState.inputText,
            createReminderMode = uiState.createReminderMode,
            isLoading = uiState.isLoading,
            errorMessage = uiState.errorMessage,
            onInputChange = onInputChange,
            onReminderModeChange = onReminderModeChange,
            onSubmit = onSubmit
        )

        Spacer(modifier = Modifier.height(16.dp))
        LogShortcutRow(
            executionLogCount = executionLogCount,
            runtimeLogCount = runtimeLogCount,
            onOpenExecutionLogs = onOpenExecutionLogs,
            onOpenRuntimeLogs = onOpenRuntimeLogs
        )

        Spacer(modifier = Modifier.height(16.dp))
        if (tasks.isEmpty()) {
            EmptyState()
        } else {
            Text(
                text = "待提醒 (${tasks.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tasks, key = { it.taskId }) { task ->
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
}

@Composable
private fun PermissionIssueCard(
    permissionIssues: List<AppPermissionIssue>,
    onResolvePermission: (AppPermissionType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Text("运行保障", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            permissionIssues.forEach { issue ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(issue.title, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(issue.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        TextButton(onClick = { onResolvePermission(issue.type) }) {
                            Text(issue.actionLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogShortcutRow(
    executionLogCount: Int,
    runtimeLogCount: Int,
    onOpenExecutionLogs: () -> Unit,
    onOpenRuntimeLogs: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickEntryCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.History,
            title = "执行记录",
            subtitle = if (executionLogCount == 0) "暂无触发记录" else "已记录 $executionLogCount 条提醒触发",
            tint = MaterialTheme.colorScheme.primary,
            onClick = onOpenExecutionLogs
        )
        QuickEntryCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Description,
            title = "运行日志",
            subtitle = if (runtimeLogCount == 0) "暂无错误日志" else "可复制 $runtimeLogCount 条调试日志",
            tint = DuckOrange,
            onClick = onOpenRuntimeLogs
        )
    }
}

@Composable
private fun QuickEntryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tint.copy(alpha = 0.14f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = tint
                )
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun InputCard(
    inputText: String,
    createReminderMode: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onInputChange: (String) -> Unit,
    onReminderModeChange: (Int) -> Unit,
    onSubmit: () -> Unit
) {
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

            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = Error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSubmit,
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

@Composable
private fun TaskCard(
    task: Task,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit
) {
    val accent = if (task.reminderMode == ReminderMode.STRONG) Error else DuckOrange
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.06f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.event,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (task.description.isNotBlank() && task.description != task.event) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = accent
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "下次执行：${formatAbsoluteTime(task.nextRunTime)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                            )
                            Text(
                                text = formatReminderTime(task.nextRunTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = true,
                            onClick = { },
                            label = { Text(task.reminderModeLabel()) }
                        )
                        if (task.hasRepeat()) {
                            FilterChip(
                                selected = true,
                                onClick = { },
                                label = { Text(task.repeatRule()?.toHumanText().orEmpty()) }
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Check, contentDescription = "完成", tint = Success)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = Error.copy(alpha = 0.7f))
                    }
                }
            }
        }
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
