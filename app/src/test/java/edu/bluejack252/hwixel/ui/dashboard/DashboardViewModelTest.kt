package edu.bluejack252.hwixel.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.util.constants.Constants
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        viewModel = DashboardViewModel(FakeProjectRepository(), FakeTaskRepository())
    }

    @Test
    fun deadlinesAreSortedAscendingAndLimitedToFive() {
        val projects = listOf(Project(id = "p1", name = "Mobile"), Project(id = "p2", name = "Backend"))
        val tasks = listOf(
            Task(id = "t3", projectId = "p2", title = "Third", deadline = 3_000L),
            Task(id = "t1", projectId = "p1", title = "First", deadline = 1_000L),
            Task(id = "t2", projectId = "p1", title = "Second", deadline = 2_000L),
            Task(id = "t6", projectId = "p1", title = "Sixth", deadline = 6_000L),
            Task(id = "t5", projectId = "p1", title = "Fifth", deadline = 5_000L),
            Task(id = "t4", projectId = "p1", title = "Fourth", deadline = 4_000L)
        )

        val result = viewModel.buildDeadlineItems(projects, tasks, nowMillis = 0L)

        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), result.map { it.taskId })
        assertEquals("Mobile", result.first().projectName)
    }

    @Test
    fun pendingTasksCountOnlyTodoAndInProgressAssignedToCurrentUser() {
        val tasks = listOf(
            Task(id = "1", status = Constants.STATUS_TODO, assignees = listOf("u1")),
            Task(id = "2", status = Constants.STATUS_IN_PROGRESS, assignees = listOf("u1", "u2")),
            Task(id = "3", status = Constants.STATUS_REVIEW, assignees = listOf("u1")),
            Task(id = "4", status = Constants.STATUS_TODO, assignees = listOf("u2"))
        )

        assertEquals(2, viewModel.countPendingTasks(tasks, "u1"))
    }

    @Test
    fun countdownFormattingUsesDaysHoursMinutesSeconds() {
        val result = viewModel.calculateCountdown(
            deadlineMillis = 90_061_000L,
            nowMillis = 0L
        )

        assertEquals(CountdownParts(days = 1L, hours = 1L, minutes = 1L, seconds = 1L), result)
    }

    @Test
    fun projectItemsExposeCurrentUserRoleAndCompletion() {
        val projects = listOf(
            Project(
                id = "p1",
                name = "Mobile",
                completionPercentage = 75f,
                members = mapOf("u1" to ProjectMember(userId = "u1", role = "Team Lead", status = "active"))
            )
        )

        val result = viewModel.buildProjectItems(projects, "u1")

        assertEquals("Team Lead", result.single().role)
        assertEquals(75f, result.single().completionPercentage)
    }

    private class FakeProjectRepository : ProjectRepository {
        override fun observeProjects(): LiveData<List<Project>> = MutableLiveData(emptyList())
        override fun observeProject(projectId: String): LiveData<Project?> = MutableLiveData(null)

        override suspend fun createProject(project: Project): Result<Unit> = Result.success(Unit)

        override suspend fun updateProject(project: Project): Result<Unit> = Result.success(Unit)

        override suspend fun updateCompletionPercentage(projectId: String, percentage: Float): Result<Unit> = Result.success(Unit)

        override suspend fun addMember(projectId: String, userId: String, member: ProjectMember): Result<Unit> = Result.success(Unit)

        override suspend fun updateMember(projectId: String, userId: String, member: ProjectMember): Result<Unit> = Result.success(Unit)

        override suspend fun updateMemberScore(projectId: String, userId: String, score: Float): Result<Unit> = Result.success(Unit)
    }

    private class FakeTaskRepository : TaskRepository {
        override fun observeAllTasks(): LiveData<List<Task>> = MutableLiveData(emptyList())

        override fun observeTasks(projectId: String): LiveData<List<Task>> = MutableLiveData(emptyList())

        override fun observeTask(projectId: String, taskId: String): LiveData<Task?> = MutableLiveData(null)

        override suspend fun createTask(task: Task): Result<Unit> = Result.success(Unit)

        override suspend fun updateTask(task: Task, actorId: String): Result<Unit> = Result.success(Unit)

        override suspend fun updateTaskStatus(projectId: String, taskId: String, newStatus: String, actorId: String): Result<Unit> = Result.success(Unit)

        override suspend fun addComment(projectId: String, taskId: String, comment: Comment): Result<Unit> = Result.success(Unit)

        override suspend fun updateSubtask(projectId: String, taskId: String, subtaskId: String, isDone: Boolean): Result<Unit> = Result.success(Unit)

        override suspend fun deleteTask(task: Task): Result<Unit> = Result.success(Unit)
    }
}
