package edu.bluejack252.hwixel.ui.project.taskdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.repository.TaskRepository
import kotlinx.coroutines.launch

class TaskDetailViewModel(
    private val projectId: String,
    private val taskId: String,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MediatorLiveData<TaskDetailUiState>()
    val uiState: LiveData<TaskDetailUiState> = _uiState

    private val _commentResult = MutableLiveData<Result<Unit>?>()
    val commentResult: LiveData<Result<Unit>?> = _commentResult

    private val _deleteResult = MutableLiveData<Result<Unit>?>()
    val deleteResult: LiveData<Result<Unit>?> = _deleteResult

    private var currentTask: Task? = null

    init {
        _uiState.value = TaskDetailUiState(isLoading = true)
        _uiState.addSource(taskRepository.observeTask(projectId, taskId)) { task ->
            currentTask = task
            if (task != null) {
                _uiState.value = TaskDetailUiState(
                    task = task,
                    attachments = task.attachments,
                    subtasks = task.subtasks.values.sortedBy { it.id },
                    comments = task.comments.values.sortedBy { it.timestamp },
                    history = task.history.values.sortedByDescending { it.timestamp },
                    isLoading = false
                )
            }
        }
    }

    fun addComment(content: String, authorId: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val comment = Comment(
                authorId = authorId,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            _commentResult.value = taskRepository.addComment(projectId, taskId, comment)
        }
    }

    fun deleteTask() {
        val task = currentTask ?: return
        viewModelScope.launch {
            _deleteResult.value = taskRepository.deleteTask(task)
        }
    }

    fun toggleSubtask(subtaskId: String, isDone: Boolean) {
        viewModelScope.launch {
            taskRepository.updateSubtask(projectId, taskId, subtaskId, isDone)
        }
    }

    fun consumeCommentResult() { _commentResult.value = null }
    fun consumeDeleteResult() { _deleteResult.value = null }
}
