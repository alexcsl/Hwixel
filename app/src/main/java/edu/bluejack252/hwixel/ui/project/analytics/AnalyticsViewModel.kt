package edu.bluejack252.hwixel.ui.project.analytics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import edu.bluejack252.hwixel.data.source.remote.TeamHealthSource
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.launch

class AnalyticsViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
    private val teamHealthSource: TeamHealthSource
) : ViewModel() {

    private val _uiState = MediatorLiveData<AnalyticsUiState>()
    val uiState: LiveData<AnalyticsUiState> = _uiState

    private var project: Project? = null
    private var tasks: List<Task> = emptyList()
    private var users: List<User> = emptyList()
    private var startDate: Long? = null
    private var endDate: Long? = null

    init {
        _uiState.value = AnalyticsUiState(isLoading = true)
        _uiState.addSource(projectRepository.observeProject(projectId)) {
            project = it
            publishState(analyzeHealth = true)
        }
        _uiState.addSource(taskRepository.observeTasks(projectId)) {
            tasks = it
            publishState(analyzeHealth = true)
        }
        _uiState.addSource(userRepository.observeUsers()) {
            users = it
            publishState(analyzeHealth = true)
        }
    }

    fun setDateRange(start: Long?, end: Long?) {
        startDate = start
        endDate = end
        publishState(analyzeHealth = true)
    }

    fun selectMember(userId: String?) {
        _uiState.value = _uiState.value?.copy(selectedMemberId = userId)
    }

    fun refreshTeamHealth() {
        publishState(analyzeHealth = true, forceHealthRefresh = true)
    }

    private fun publishState(analyzeHealth: Boolean, forceHealthRefresh: Boolean = false) {
        val members = buildMemberStats()
        val current = _uiState.value ?: AnalyticsUiState()
        _uiState.value = current.copy(
            isLoading = false,
            members = members,
            startDate = startDate,
            endDate = endDate,
            selectedMemberId = current.selectedMemberId?.takeIf { selected ->
                members.any { it.userId == selected }
            }
        )

        if (analyzeHealth && (forceHealthRefresh || current.members != members)) {
            analyzeTeamHealth(members)
        }
    }

    private fun buildMemberStats(): List<MemberAnalyticsUi> {
        val currentProject = project ?: return emptyList()
        val now = System.currentTimeMillis()
        return currentProject.members.map { (userId, member) ->
            val assignedTasks = tasks.filter { task ->
                userId in task.assignees && isAssignedTaskInRange(task)
            }
            val completedTasks = assignedTasks.filter { task ->
                task.status == Constants.STATUS_DONE && isCompletedTaskInRange(task)
            }
            val overdueTasks = assignedTasks.count { task ->
                task.deadline > 0L && task.deadline < now && task.status != Constants.STATUS_DONE
            }
            val user = users.firstOrNull { it.id == userId }
            MemberAnalyticsUi(
                userId = userId,
                name = user?.name ?: userId,
                assignedCount = assignedTasks.size,
                completedCount = completedTasks.size,
                overdueCount = overdueTasks,
                contributionScore = member.contributionScore
            )
        }.sortedByDescending { it.contributionScore }
    }

    private fun isAssignedTaskInRange(task: Task): Boolean {
        val start = startDate
        val end = endDate
        if (start == null && end == null) return true
        val date = task.deadline.takeIf { it > 0L } ?: return true
        return (start == null || date >= start) && (end == null || date <= end)
    }

    private fun isCompletedTaskInRange(task: Task): Boolean {
        val start = startDate
        val end = endDate
        if (start == null && end == null) return true
        val completedAt = task.history.values
            .filter { it.action.contains(Constants.STATUS_DONE, ignoreCase = true) }
            .maxOfOrNull { it.timestamp }
            ?: task.deadline.takeIf { it > 0L }
            ?: return true
        return (start == null || completedAt >= start) && (end == null || completedAt <= end)
    }

    private fun analyzeTeamHealth(members: List<MemberAnalyticsUi>) {
        if (members.isEmpty()) return
        _uiState.value = _uiState.value?.copy(teamHealthLoading = true, teamHealthError = null)
        viewModelScope.launch {
            val result = teamHealthSource.analyze(buildPrompt(members))
            _uiState.value = _uiState.value?.copy(
                teamHealth = result.getOrNull() ?: _uiState.value?.teamHealth,
                teamHealthLoading = false,
                teamHealthError = result.exceptionOrNull()?.message
            )
        }
    }

    internal fun buildPrompt(members: List<MemberAnalyticsUi>): String {
        val dataLines = members.joinToString(separator = "\n") { member ->
            "- ${member.name}: assigned=${member.assignedCount}, completed=${member.completedCount}, overdue=${member.overdueCount}"
        }
        return """
            Analyze this student group project workload and return ONLY a JSON object with:
            status (Healthy, Mild Imbalance, or Severe Imbalance),
            summary (one sentence),
            recommendations (array of up to 3 short strings).
            Data:
            $dataLines
        """.trimIndent()
    }
}

class AnalyticsViewModelFactory(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
    private val teamHealthSource: TeamHealthSource
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AnalyticsViewModel(
            projectId,
            projectRepository,
            taskRepository,
            userRepository,
            teamHealthSource
        ) as T
    }
}
