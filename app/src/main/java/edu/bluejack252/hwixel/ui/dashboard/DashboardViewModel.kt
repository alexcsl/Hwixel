package edu.bluejack252.hwixel.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.Project
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
            deadlines = buildDeadlineItems(projects, tasks, now),
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
        nowMillis: Long
    ): List<DashboardDeadlineUi> {
        val projectNames = projects.associate { project -> project.id to project.name }
        return tasks
            .filter { task -> task.deadline > 0L && task.deadline >= nowMillis }
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
}
