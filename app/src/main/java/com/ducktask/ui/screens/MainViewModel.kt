package com.ducktask.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ducktask.data.repository.TaskRepository
import com.ducktask.domain.model.Task
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val tasks: List<Task> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val parsedTime: String? = null
)

sealed class MainUiEvent {
    data class InputChanged(val text: String) : MainUiEvent()
    data object SubmitTask : MainUiEvent()
    data class DeleteTask(val task: Task) : MainUiEvent()
    data class MarkDone(val task: Task) : MainUiEvent()
    data object ClearError : MainUiEvent()
    data object ClearSuccess : MainUiEvent()
}

class MainViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val pendingTasks: StateFlow<List<Task>> = repository.getAllPendingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.InputChanged -> {
                _uiState.update { it.copy(inputText = event.text, errorMessage = null) }
            }
            MainUiEvent.SubmitTask -> submitTask()
            is MainUiEvent.DeleteTask -> deleteTask(event.task)
            is MainUiEvent.MarkDone -> markDone(event.task)
            MainUiEvent.ClearError -> _uiState.update { it.copy(errorMessage = null) }
            MainUiEvent.ClearSuccess -> _uiState.update { it.copy(successMessage = null) }
        }
    }

    private fun submitTask() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入提醒内容") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.createTaskFromText(text)
            result.fold(
                onSuccess = { task ->
                    val timeStr = formatTime(task.time)
                    _uiState.update {
                        it.copy(
                            inputText = "",
                            isLoading = false,
                            successMessage = "已设置${timeStr}的提醒",
                            parsedTime = timeStr,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "解析失败"
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
            repository.markAsDone(task.id)
        }
    }

    private fun formatTime(time: java.time.LocalDateTime): String {
        val now = java.time.LocalDateTime.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), time.toLocalDate())
        val timeStr = time.toLocalTime().toString().substring(0, 5)

        return when {
            days == 0L -> "今天 $timeStr"
            days == 1L -> "明天 $timeStr"
            days == 2L -> "后天 $timeStr"
            days < 7 -> "${time.dayOfWeek.name.replace("DAY", "").lowercase()} $timeStr"
            else -> "${time.month.value}月${time.dayOfMonth}日 $timeStr"
        }
    }

    class Factory(private val repository: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
