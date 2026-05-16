package edu.bluejack252.hwixel.ui.project.hub

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
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
                    actorName = entry.actorId,
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
        viewModelScope.launch {
            val project = Project(
                name = name,
                description = description,
                goals = goals,
                dueDate = dueDate,
                createdBy = creatorId,
                members = mapOf(creatorId to ProjectMember(
                    userId = creatorId,
                    role = "Team Lead",
                    status = "active"
                ))
            )
            _createProjectResult.value = projectRepository.createProject(project)
        }
    }

    fun consumeCreateResult() {
        _createProjectResult.value = null
    }
}
