package com.ducktask.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ducktask.app.data.repository.TaskRepository
import com.ducktask.app.domain.model.ReminderExecutionLog
import com.ducktask.app.domain.model.ReminderMode
import com.ducktask.app.domain.model.Task
import com.ducktask.app.util.formatReminderTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val inputText: String = "",
    val createReminderMode: Int = ReminderMode.NORMAL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

sealed class MainUiEvent {
    data class InputChanged(val text: String) : MainUiEvent()
    data class CreateReminderModeChanged(val mode: Int) : MainUiEvent()
    data object SubmitTask : MainUiEvent()
    data class DeleteTask(val task: Task) : MainUiEvent()
    data class MarkDone(val task: Task) : MainUiEvent()
    data object ClearSuccess : MainUiEvent()
    data object ClearError : MainUiEvent()
}

class MainViewModel(private val repository: TaskRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val pendingTasks: StateFlow<List<Task>> = repository.getAllPendingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val executionLogs: StateFlow<List<ReminderExecutionLog>> = repository.getExecutionLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.InputChanged -> {
                _uiState.update { it.copy(inputText = event.text, errorMessage = null) }
            }
            is MainUiEvent.CreateReminderModeChanged -> {
                _uiState.update { it.copy(createReminderMode = event.mode) }
            }
            MainUiEvent.SubmitTask -> submitTask()
            is MainUiEvent.DeleteTask -> deleteTask(event.task)
            is MainUiEvent.MarkDone -> markDone(event.task)
            MainUiEvent.ClearSuccess -> _uiState.update { it.copy(successMessage = null) }
            MainUiEvent.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun updateTask(
        task: Task,
        event: String,
        description: String,
        nextRunTimeText: String,
        reminderMode: Int,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.updateTask(task, event, description, nextRunTimeText, reminderMode).fold(
                onSuccess = { updatedTask ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "已更新为 ${formatReminderTime(updatedTask.nextRunTime)} 的${updatedTask.reminderModeLabel()}"
                        )
                    }
                    onResult(true)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "保存失败"
                        )
                    }
                    onResult(false)
                }
            )
        }
    }

    private fun submitTask() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入提醒内容") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.createTaskFromText(text, _uiState.value.createReminderMode).fold(
                onSuccess = { task ->
                    _uiState.update {
                        it.copy(
                            inputText = "",
                            isLoading = false,
                            successMessage = "DuckTask 将在 ${formatReminderTime(task.nextRunTime)} 以${task.reminderModeLabel()}提醒你${task.event}"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "解析失败，请换一种说法"
                        )
                    }
                }
            )
        }
    }

    private fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    private fun markDone(task: Task) {
        viewModelScope.launch {
            repository.markAsDone(task)
        }
    }

    class Factory(private val repository: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
