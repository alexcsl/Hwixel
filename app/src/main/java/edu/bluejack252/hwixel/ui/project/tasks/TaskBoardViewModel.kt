package edu.bluejack252.hwixel.ui.project.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.launch

class TaskBoardViewModel(
    private val projectId: String,
    private val currentUserId: String,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MediatorLiveData<TaskBoardUiState>()
    val uiState: LiveData<TaskBoardUiState> = _uiState

    private val _statusUpdateResult = MutableLiveData<Result<Unit>?>()
    val statusUpdateResult: LiveData<Result<Unit>?> = _statusUpdateResult

    private var allTasks: List<Task> = emptyList()
    private var users: List<User> = emptyList()
    private var currentFilter = TaskFilter()
    private var currentViewMode = ViewMode.LIST
    private var isTeamLead = false

    init {
        _uiState.value = TaskBoardUiState(isLoading = true)
        _uiState.addSource(projectRepository.observeProject(projectId)) { project ->
            val role = project?.members?.get(currentUserId)?.role
                ?: Constants.ROLE_TEAM_LEAD.takeIf { project?.createdBy == currentUserId }
            isTeamLead = role == Constants.ROLE_TEAM_LEAD
            publishState()
        }
        _uiState.addSource(taskRepository.observeTasks(projectId)) { tasks ->
            allTasks = tasks
            publishState()
        }
        _uiState.addSource(userRepository.observeUsers()) { userList ->
            users = userList
            publishState()
        }
    }

    private fun applyFilter(tasks: List<Task>, filter: TaskFilter): List<Task> {
        return tasks.filter { task ->
            val assigneeMatch = filter.assigneeIds.isEmpty() ||
                task.assignees.any { it in filter.assigneeIds }
            val priorityMatch = filter.priority == null || task.priority == filter.priority
            val deadlineFromMatch = filter.deadlineFrom == null ||
                (task.deadline > 0L && task.deadline >= filter.deadlineFrom)
            val deadlineToMatch = filter.deadlineTo == null ||
                (task.deadline > 0L && task.deadline <= filter.deadlineTo)
            assigneeMatch && priorityMatch && deadlineFromMatch && deadlineToMatch
        }
    }

    private fun publishState() {
        val visibleTasks = visibleTasksForRole()
        _uiState.value = TaskBoardUiState(
            tasks = visibleTasks,
            filteredTasks = applyFilter(visibleTasks, currentFilter),
            memberNames = buildMemberNames(),
            filter = currentFilter,
            viewMode = currentViewMode,
            isLoading = false
        )
    }

    private fun visibleTasksForRole(): List<Task> {
        if (isTeamLead) return allTasks
        return allTasks.filter { task -> currentUserId.isNotBlank() && currentUserId in task.assignees }
    }

    private fun buildMemberNames(): Map<String, String> {
        return visibleTasksForRole()
            .flatMap { it.assignees }
            .distinct()
            .associateWith { userId ->
                users.firstOrNull { it.id == userId }?.name?.takeIf { it.isNotBlank() }
                    ?: UNKNOWN_MEMBER_NAME
            }
    }

    fun setFilter(filter: TaskFilter) {
        currentFilter = filter
        publishState()
    }

    fun resetFilter() {
        currentFilter = TaskFilter()
        publishState()
    }

    fun toggleViewMode() {
        currentViewMode = if (currentViewMode == ViewMode.LIST) ViewMode.KANBAN else ViewMode.LIST
        publishState()
    }

    fun setViewMode(mode: ViewMode) {
        currentViewMode = mode
        publishState()
    }

    fun updateTaskStatus(taskId: String, newStatus: String, actorId: String) {
        viewModelScope.launch {
            val result = taskRepository.updateTaskStatus(projectId, taskId, newStatus, actorId)
            _statusUpdateResult.value = result
        }
    }

    fun consumeStatusResult() {
        _statusUpdateResult.value = null
    }

    fun getTasksByStatus(status: String): List<Task> {
        return (_uiState.value?.filteredTasks ?: emptyList()).filter { it.status == status }
    }

    fun getAllStatuses() = listOf(
        Constants.STATUS_TODO,
        Constants.STATUS_IN_PROGRESS,
        Constants.STATUS_REVIEW,
        Constants.STATUS_DONE
    )

    private companion object {
        const val UNKNOWN_MEMBER_NAME = "Unknown member"
    }
}
