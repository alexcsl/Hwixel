package edu.bluejack252.hwixel.ui.project.taskedit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.Subtask
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.launch

class CreateEditTaskViewModel(
    private val projectId: String,
    private val taskId: String,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MediatorLiveData<CreateEditTaskUiState>()
    val uiState: LiveData<CreateEditTaskUiState> = _uiState

    private var existingTask: Task? = null
    private var memberOptions: List<MemberOption> = emptyList()

    init {
        _uiState.value = CreateEditTaskUiState.Loading
        _uiState.addSource(projectRepository.observeProject(projectId)) { project ->
            val members = project?.members?.map { (userId, _) ->
                MemberOption(userId = userId, name = userId)
            } ?: emptyList()
            memberOptions = members
            publishLoaded()
        }
        _uiState.addSource(userRepository.observeUsers()) { users ->
            memberOptions = memberOptions.map { option ->
                val user = users.firstOrNull { it.id == option.userId }
                option.copy(name = user?.name ?: option.userId)
            }
            publishLoaded()
        }
        if (taskId.isNotBlank()) {
            _uiState.addSource(taskRepository.observeTask(projectId, taskId)) { task ->
                existingTask = task
                publishLoaded()
            }
        }
    }

    private fun publishLoaded() {
        val options = memberOptions.map { option ->
            option.copy(isSelected = existingTask?.assignees?.contains(option.userId) == true)
        }
        _uiState.value = CreateEditTaskUiState.Loaded(
            task = existingTask,
            projectMembers = options
        )
    }

    fun saveTask(
        title: String,
        description: String,
        deadline: Long,
        assigneeIds: List<String>,
        priority: String,
        subtaskTitles: List<String>
    ) {
        if (title.isBlank()) {
            _uiState.value = CreateEditTaskUiState.Error("Title is required")
            return
        }
        val subtasks = subtaskTitles
            .filter { it.isNotBlank() }
            .mapIndexed { i, t -> "s$i" to Subtask(id = "s$i", title = t) }
            .toMap()

        viewModelScope.launch {
            val task = if (existingTask != null) {
                existingTask!!.copy(
                    title = title,
                    description = description,
                    deadline = deadline,
                    assignees = assigneeIds,
                    priority = priority,
                    subtasks = subtasks
                )
            } else {
                Task(
                    projectId = projectId,
                    title = title,
                    description = description,
                    deadline = deadline,
                    assignees = assigneeIds,
                    priority = priority.ifBlank { Constants.PRIORITY_MEDIUM },
                    status = Constants.STATUS_TODO,
                    subtasks = subtasks
                )
            }
            val result = if (existingTask != null) {
                taskRepository.updateTask(task)
            } else {
                taskRepository.createTask(task)
            }
            _uiState.value = if (result.isSuccess) {
                CreateEditTaskUiState.Success
            } else {
                CreateEditTaskUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
}
