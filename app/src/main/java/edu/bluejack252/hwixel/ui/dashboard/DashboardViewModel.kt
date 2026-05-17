package edu.bluejack252.hwixel.ui.dashboard

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
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class DashboardViewModel(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {
    private val _uiState = MediatorLiveData<DashboardUiState>()
    val uiState: LiveData<DashboardUiState> = _uiState

    private val _createProjectResult = MutableLiveData<Result<Unit>?>()
    val createProjectResult: LiveData<Result<Unit>?> = _createProjectResult

    private val _tick = MutableLiveData(System.currentTimeMillis())
    private var projects: List<Project> = emptyList()
    private var tasks: List<Task> = emptyList()
    private var currentUserId: String = ""
    private var tickerJob: Job? = null
    private var hasLoaded = false

    init {
        _uiState.value = DashboardUiState()
        _uiState.addSource(_tick) { publishState() }
    }

    fun loadDashboard(userId: String) {
        if (hasLoaded && currentUserId == userId) return
        hasLoaded = true
        currentUserId = userId
        _uiState.addSource(projectRepository.observeProjects()) { value ->
            projects = value
            publishState()
        }
        _uiState.addSource(taskRepository.observeAllTasks()) { value ->
            tasks = value
            publishState()
        }
        startTicker()
    }

    private fun startTicker() {
        if (tickerJob != null) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                _tick.value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    private fun publishState() {
        val now = _tick.value ?: System.currentTimeMillis()
        _uiState.value = DashboardUiState(
            projects = buildProjectItems(projects, currentUserId),
            deadlines = buildDeadlineItems(projects, tasks, now, currentUserId),
            pendingTaskCount = countPendingTasks(tasks, currentUserId)
        )
    }

    fun buildProjectItems(projects: List<Project>, userId: String): List<DashboardProjectUi> {
        return projects
            .filter { project ->
                val member = project.members[userId]
                project.members.isEmpty() || (member != null && member.status != Constants.MEMBER_STATUS_INACTIVE)
            }
            .map { project ->
                DashboardProjectUi(
                    id = project.id,
                    name = project.name,
                    role = project.members[userId]?.role.orEmpty(),
                    completionPercentage = project.completionPercentage
                )
            }
    }

    fun buildDeadlineItems(
        projects: List<Project>,
        tasks: List<Task>,
        nowMillis: Long,
        userId: String = ""
    ): List<DashboardDeadlineUi> {
        val activeProjects = projects.filter { project ->
            val member = project.members[userId]
            member != null && member.status != Constants.MEMBER_STATUS_INACTIVE
        }
        val projectNames = activeProjects.associate { project -> project.id to project.name }
        val activeProjectIds = projectNames.keys
        return tasks
            .filter { task ->
                task.projectId in activeProjectIds &&
                    task.assignees.contains(userId) &&
                    task.deadline > 0L &&
                    task.deadline >= nowMillis
            }
            .sortedBy { task -> task.deadline }
            .take(5)
            .map { task ->
                DashboardDeadlineUi(
                    taskId = task.id,
                    projectId = task.projectId,
                    taskTitle = task.title,
                    projectName = projectNames[task.projectId].orEmpty(),
                    deadline = task.deadline,
                    countdown = calculateCountdown(task.deadline, nowMillis)
                )
            }
    }

    fun countPendingTasks(tasks: List<Task>, userId: String): Int {
        return tasks.count { task ->
            task.assignees.contains(userId) &&
                (task.status == Constants.STATUS_TODO || task.status == Constants.STATUS_IN_PROGRESS)
        }
    }

    fun calculateCountdown(deadlineMillis: Long, nowMillis: Long): CountdownParts {
        val totalSeconds = max(0L, (deadlineMillis - nowMillis) / 1_000L)
        return CountdownParts(
            days = totalSeconds / 86_400L,
            hours = (totalSeconds % 86_400L) / 3_600L,
            minutes = (totalSeconds % 3_600L) / 60L,
            seconds = totalSeconds % 60L
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
                members = mapOf(
                    creatorId to ProjectMember(
                        userId = creatorId,
                        role = Constants.ROLE_TEAM_LEAD,
                        status = Constants.MEMBER_STATUS_ACTIVE
                    )
                )
            )
            _createProjectResult.value = projectRepository.createProject(project)
        }
    }

    fun consumeCreateResult() {
        _createProjectResult.value = null
    }
}
