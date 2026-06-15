package edu.bluejack25_2.hwixel.ui.project.taskedit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack25_2.hwixel.data.model.Attachment
import edu.bluejack25_2.hwixel.data.model.Subtask
import edu.bluejack25_2.hwixel.data.model.Task
import edu.bluejack25_2.hwixel.data.repository.ProjectRepository
import edu.bluejack25_2.hwixel.data.repository.TaskRepository
import edu.bluejack25_2.hwixel.data.repository.UserRepository
import edu.bluejack25_2.hwixel.util.constants.Constants
import kotlinx.coroutines.launch
import java.util.UUID

class CreateEditTaskViewModel(
    private val projectId: String,
    private val taskId: String,
    private val currentUserId: String,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MediatorLiveData<CreateEditTaskUiState>()
    val uiState: LiveData<CreateEditTaskUiState> = _uiState

    private var existingTask: Task? = null
    private var memberOptions: List<MemberOption> = emptyList()
    private var usersLoaded = false
    private var canSaveTask = false

    init {
        _uiState.value = CreateEditTaskUiState.Loading
        _uiState.addSource(projectRepository.observeProject(projectId)) { project ->
            val members = project?.members?.map { (userId, _) ->
                MemberOption(userId = userId, name = UNKNOWN_MEMBER_NAME)
            } ?: emptyList()
            memberOptions = members
            val role = project?.members?.get(currentUserId)?.role
                ?: Constants.ROLE_TEAM_LEAD.takeIf { project?.createdBy == currentUserId }
            canSaveTask = role == Constants.ROLE_TEAM_LEAD
            publishLoaded()
        }
        _uiState.addSource(userRepository.observeUsers()) { users ->
            usersLoaded = true
            memberOptions = memberOptions.map { option ->
                val user = users.firstOrNull { it.id == option.userId }
                option.copy(name = user?.name?.takeIf { it.isNotBlank() } ?: UNKNOWN_MEMBER_NAME)
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
        if (memberOptions.isNotEmpty() && !usersLoaded) return
        val options = memberOptions.map { option ->
            option.copy(isSelected = existingTask?.assignees?.contains(option.userId) == true)
        }
        _uiState.value = CreateEditTaskUiState.Loaded(
            task = existingTask,
            projectMembers = options,
            canSaveTask = canSaveTask
        )
    }

    fun saveTask(
        title: String,
        description: String,
        deadline: Long,
        assigneeIds: List<String>,
        priority: String,
        subtasksInput: List<Subtask>,
        attachmentsInput: List<Attachment>,
        actorId: String
    ) {
        if (!canSaveTask) {
            _uiState.value = CreateEditTaskUiState.Error("no_permission")
            return
        }
        if (title.isBlank()) {
            _uiState.value = CreateEditTaskUiState.Error("empty_title")
            return
        }
        val subtasks = subtasksInput
            .filter { it.title.isNotBlank() }
            .map { subtask ->
                val id = subtask.id.ifBlank { "s-${UUID.randomUUID()}" }
                id to subtask.copy(id = id)
            }
            .toMap()

        viewModelScope.launch {
            val task = if (existingTask != null) {
                existingTask!!.copy(
                    title = title,
                    description = description,
                    deadline = deadline,
                    assignees = assigneeIds,
                    priority = priority,
                    subtasks = subtasks,
                    attachments = attachmentsInput
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
                    subtasks = subtasks,
                    attachments = attachmentsInput
                )
            }
            val result = if (existingTask != null) {
                taskRepository.updateTask(task, actorId)
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

    private companion object {
        const val UNKNOWN_MEMBER_NAME = "Unknown member"
    }
}
