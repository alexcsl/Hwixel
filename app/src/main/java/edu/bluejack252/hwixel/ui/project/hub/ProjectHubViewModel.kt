package edu.bluejack252.hwixel.ui.project.hub

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.launch

class ProjectHubViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MediatorLiveData<ProjectHubUiState>()
    val uiState: LiveData<ProjectHubUiState> = _uiState

    private val _createProjectResult = MutableLiveData<Result<Unit>?>()
    val createProjectResult: LiveData<Result<Unit>?> = _createProjectResult

    private var currentProject: Project? = null
    private var tasks: List<Task> = emptyList()
    private var users: List<User> = emptyList()

    init {
        _uiState.value = ProjectHubUiState(isLoading = true)
        _uiState.addSource(projectRepository.observeProject(projectId)) { project ->
            currentProject = project
            publishState()
        }
        _uiState.addSource(taskRepository.observeTasks(projectId)) { taskList ->
            tasks = taskList
            publishState()
        }
        _uiState.addSource(userRepository.observeUsers()) { userList ->
            users = userList
            publishState()
        }
    }

    private fun publishState() {
        val history = tasks
            .flatMap { task -> task.history.values.toList() }
            .sortedByDescending { it.timestamp }
            .take(10)

        _uiState.value = ProjectHubUiState(
            project = currentProject,
            recentActivity = history.map { entry ->
                ActivityUi(
                    actorName = users.firstOrNull { it.id == entry.actorId }?.name?.takeIf { it.isNotBlank() }
                        ?: UNKNOWN_MEMBER_NAME,
                    action = entry.action,
                    timestamp = entry.timestamp
                )
            },
            isLoading = false
        )
    }

    fun createProject(
        name: String,
        description: String,
        goals: String,
        dueDate: Long,
        creatorId: String
    ) {
        if (name.isBlank()) return
        if (creatorId.isBlank()) {
            _createProjectResult.value = Result.failure(IllegalStateException("You must be logged in to create a project."))
            return
        }
        viewModelScope.launch {
            val project = Project(
                name = name,
                description = description,
                goals = goals,
                dueDate = dueDate,
                createdBy = creatorId,
                members = mapOf(creatorId to ProjectMember(
                    userId = creatorId,
                    role = Constants.ROLE_TEAM_LEAD,
                    status = Constants.MEMBER_STATUS_ACTIVE
                ))
            )
            _createProjectResult.value = projectRepository.createProject(project)
        }
    }

    fun consumeCreateResult() {
        _createProjectResult.value = null
    }

    fun getMembersForNav(): Triple<Array<String>, Array<String>, Array<String>> {
        val project = currentProject ?: return Triple(emptyArray(), emptyArray(), emptyArray())
        val ids = project.members.keys.toList()
        val names = ids.map { id -> users.firstOrNull { it.id == id }?.name ?: "" }
        val roles = ids.map { id -> project.members[id]?.role ?: "" }
        return Triple(ids.toTypedArray(), names.toTypedArray(), roles.toTypedArray())
    private companion object {
        const val UNKNOWN_MEMBER_NAME = "Unknown member"
    }
}
