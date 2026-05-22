package edu.bluejack252.hwixel.ui.project.hub

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.MainDispatcherRule
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.HistoryEntry
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProjectHubViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val projectLiveData = MutableLiveData<Project?>()
    private val taskLiveData = MutableLiveData<List<Task>>()
    private val usersLiveData = MutableLiveData<List<User>>()
    private lateinit var viewModel: ProjectHubViewModel

    @Before
    fun setUp() {
        viewModel = ProjectHubViewModel(
            projectId = "p1",
            projectRepository = FakeProjectRepository(projectLiveData),
            taskRepository = FakeTaskRepository(taskLiveData),
            userRepository = FakeUserRepository(usersLiveData)
        )
        viewModel.uiState.observeForever { }
    }

    @Test
    fun recentActivityIsSortedLimitedAndUsesActorName() {
        projectLiveData.value = Project(id = "p1")
        usersLiveData.value = listOf(User(id = "u1", name = "Alice"))
        taskLiveData.value = (1..12).map { index ->
            Task(
                id = "t$index",
                history = mapOf(
                    "h$index" to HistoryEntry(
                        id = "h$index",
                        actorId = "u1",
                        action = "action-$index",
                        timestamp = index.toLong()
                    )
                )
            )
        }

        val activity = viewModel.uiState.value?.recentActivity.orEmpty()

        assertEquals(10, activity.size)
        assertEquals("Alice", activity.first().actorName)
        assertEquals("action-12", activity.first().action)
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
