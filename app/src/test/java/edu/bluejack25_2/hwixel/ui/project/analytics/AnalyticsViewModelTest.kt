package edu.bluejack25_2.hwixel.ui.project.analytics

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack25_2.hwixel.MainDispatcherRule
import edu.bluejack25_2.hwixel.data.model.Comment
import edu.bluejack25_2.hwixel.data.model.HistoryEntry
import edu.bluejack25_2.hwixel.data.model.Project
import edu.bluejack25_2.hwixel.data.model.ProjectMember
import edu.bluejack25_2.hwixel.data.model.Task
import edu.bluejack25_2.hwixel.data.model.TeamHealthResult
import edu.bluejack25_2.hwixel.data.model.User
import edu.bluejack25_2.hwixel.data.repository.ProjectRepository
import edu.bluejack25_2.hwixel.data.repository.TaskRepository
import edu.bluejack25_2.hwixel.data.repository.TeamHealthRepository
import edu.bluejack25_2.hwixel.data.repository.UserRepository
import edu.bluejack25_2.hwixel.util.constants.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AnalyticsViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val projectLiveData = MutableLiveData<Project?>()
    private val tasksLiveData = MutableLiveData<List<Task>>()
    private val usersLiveData = MutableLiveData<List<User>>()
    private val healthRepository = FakeTeamHealthRepository()

    private fun createViewModel(healthEnabled: Boolean = true): AnalyticsViewModel {
        return AnalyticsViewModel(
            projectId = "p1",
            projectRepository = FakeProjectRepository(projectLiveData),
            taskRepository = FakeTaskRepository(tasksLiveData),
            userRepository = FakeUserRepository(usersLiveData),
            teamHealthRepository = healthRepository,
            gptTeamHealthEnabled = healthEnabled
        ).also { it.uiState.observeForever { } }
    }

    @Test
    fun buildsMemberStatsWithoutCallingDisabledHealthAnalysis() {
        val viewModel = createViewModel(healthEnabled = false)
        healthRepository.result = Result.success(
            TeamHealthResult("Healthy", "Balanced work.", listOf("Keep rotating tasks"))
        )
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf(
                "u1" to ProjectMember(userId = "u1", contributionScore = 90f),
                "u2" to ProjectMember(userId = "u2", contributionScore = 30f)
            )
        )
        usersLiveData.value = listOf(User(id = "u1", name = "Alice"), User(id = "u2", name = "Bob"))
        tasksLiveData.value = listOf(
            Task(id = "t1", assignees = listOf("u1"), status = Constants.STATUS_DONE),
            Task(id = "t2", assignees = listOf("u1", "u2"), status = Constants.STATUS_TODO)
        )

        val state = viewModel.uiState.value!!

        assertFalse(state.isLoading)
        assertEquals("Alice", state.members.first().name)
        assertEquals(2, state.members.first { it.userId == "u1" }.assignedCount)
        assertEquals(1, state.members.first { it.userId == "u1" }.completedCount)
        assertEquals(null, state.teamHealth)
        assertEquals(0, healthRepository.callCount)
    }

    @Test
    fun dateRangeFiltersCompletedTaskCounts() {
        val viewModel = createViewModel()
        val inRange = 2_000L
        val outOfRange = 9_000L
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf("u1" to ProjectMember(userId = "u1", contributionScore = 50f))
        )
        usersLiveData.value = listOf(User(id = "u1", name = "Alice"))
        tasksLiveData.value = listOf(
            Task(
                id = "t1",
                assignees = listOf("u1"),
                status = Constants.STATUS_DONE,
                deadline = inRange,
                history = mapOf("h1" to HistoryEntry(action = "changed status to done", timestamp = inRange))
            ),
            Task(
                id = "t2",
                assignees = listOf("u1"),
                status = Constants.STATUS_DONE,
                deadline = outOfRange,
                history = mapOf("h2" to HistoryEntry(action = "changed status to done", timestamp = outOfRange))
            )
        )

        viewModel.setDateRange(1_000L, 3_000L)

        assertEquals(1, viewModel.uiState.value!!.members.single().completedCount)
    }

    @Test
    fun disabledHealthAnalysisDoesNotShowNetworkError() {
        val viewModel = createViewModel(healthEnabled = false)
        healthRepository.result = Result.failure(IllegalStateException("network down"))
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf("u1" to ProjectMember(userId = "u1"))
        )
        usersLiveData.value = listOf(User(id = "u1", name = "Alice"))
        tasksLiveData.value = emptyList()

        val state = viewModel.uiState.value!!

        assertFalse(state.teamHealthLoading)
        assertEquals(null, state.teamHealthError)
        assertEquals(0, healthRepository.callCount)
    }

    @Test
    fun unchangedAnalyticsFingerprintDoesNotRefireHealthCall() {
        val viewModel = createViewModel()
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf("u1" to ProjectMember(userId = "u1"))
        )
        usersLiveData.value = listOf(User(id = "u1", name = "Alice"))
        tasksLiveData.value = listOf(
            Task(id = "t1", assignees = listOf("u1"), status = Constants.STATUS_TODO)
        )
        val firstCallCount = healthRepository.callCount

        tasksLiveData.value = listOf(
            Task(
                id = "t1",
                assignees = listOf("u1"),
                status = Constants.STATUS_TODO,
                comments = mapOf("c1" to Comment(id = "c1", content = "No stat change"))
            )
        )

        assertEquals(firstCallCount, healthRepository.callCount)
    }

    private class FakeTeamHealthRepository : TeamHealthRepository {
        var result: Result<TeamHealthResult> = Result.success(
            TeamHealthResult("Healthy", "OK", emptyList())
        )
        var lastPrompt: String = ""
        var callCount: Int = 0

        override suspend fun analyze(prompt: String): Result<TeamHealthResult> {
            callCount++
            lastPrompt = prompt
            return result
        }
    }

    private class FakeProjectRepository(private val project: LiveData<Project?>) : ProjectRepository {
        override fun observeProjects(): LiveData<List<Project>> = MutableLiveData(emptyList())
        override fun observeProject(projectId: String): LiveData<Project?> = project
        override suspend fun createProject(project: Project): Result<Unit> = Result.success(Unit)
        override suspend fun updateProject(project: Project): Result<Unit> = Result.success(Unit)
        override suspend fun updateCompletionPercentage(projectId: String, percentage: Float): Result<Unit> = Result.success(Unit)
        override suspend fun addMember(projectId: String, userId: String, member: ProjectMember): Result<Unit> = Result.success(Unit)
        override suspend fun updateMember(projectId: String, userId: String, member: ProjectMember): Result<Unit> = Result.success(Unit)
        override suspend fun updateMemberScore(projectId: String, userId: String, score: Float): Result<Unit> = Result.success(Unit)
    }

    private class FakeTaskRepository(private val tasks: LiveData<List<Task>>) : TaskRepository {
        override fun observeAllTasks(): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTasks(projectId: String): LiveData<List<Task>> = tasks
        override fun observeTasksForProjects(projectIds: Set<String>): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTask(projectId: String, taskId: String): LiveData<Task?> = MutableLiveData(null)
        override suspend fun createTask(task: Task): Result<Unit> = Result.success(Unit)
        override suspend fun updateTask(task: Task, actorId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateTaskStatus(projectId: String, taskId: String, newStatus: String, actorId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addComment(projectId: String, taskId: String, comment: Comment, mentionedUserIds: List<String>): Result<Unit> = Result.success(Unit)
        override suspend fun updateSubtask(projectId: String, taskId: String, subtaskId: String, isDone: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun deleteTask(task: Task): Result<Unit> = Result.success(Unit)
    }

    private class FakeUserRepository(private val users: LiveData<List<User>>) : UserRepository {
        override fun observeUsers(): LiveData<List<User>> = users
        override fun observeUser(userId: String): LiveData<User?> = MutableLiveData(null)
        override suspend fun upsertUser(user: User): Result<Unit> = Result.success(Unit)
        override suspend fun findUserByEmail(email: String): Result<User?> = Result.success(null)
        override suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>): Result<Unit> = Result.success(Unit)
    }
}
