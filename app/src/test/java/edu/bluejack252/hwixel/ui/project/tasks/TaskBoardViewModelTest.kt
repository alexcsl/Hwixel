package edu.bluejack252.hwixel.ui.project.tasks

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.MainDispatcherRule
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TaskBoardViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val tasksLiveData = MutableLiveData<List<Task>>()
    private val repository = FakeTaskRepository(tasksLiveData)
    private val projectLiveData = MutableLiveData<Project?>()
    private val usersLiveData = MutableLiveData<List<User>>()
    private lateinit var viewModel: TaskBoardViewModel

    @Before
    fun setUp() {
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf("u1" to ProjectMember(userId = "u1", role = Constants.ROLE_TEAM_LEAD))
        )
        viewModel = TaskBoardViewModel(
            projectId = "p1",
            currentUserId = "u1",
            taskRepository = repository,
            projectRepository = FakeProjectRepository(projectLiveData),
            userRepository = FakeUserRepository(usersLiveData)
        )
        viewModel.uiState.observeForever { }
    }

    @Test
    fun filterComposesAssigneeAndPriority() {
        tasksLiveData.value = listOf(
            Task(id = "1", assignees = listOf("u1"), priority = Constants.PRIORITY_HIGH),
            Task(id = "2", assignees = listOf("u1"), priority = Constants.PRIORITY_LOW),
            Task(id = "3", assignees = listOf("u2"), priority = Constants.PRIORITY_HIGH)
        )

        viewModel.setFilter(TaskFilter(assigneeIds = setOf("u1"), priority = Constants.PRIORITY_HIGH))

        assertEquals(listOf("1"), viewModel.uiState.value?.filteredTasks?.map { it.id })
    }

    @Test
    fun statusTransitionCallsRepository() = runTest {
        viewModel.updateTaskStatus("t1", Constants.STATUS_DONE, "u1")

        assertEquals("t1", repository.updatedTaskId)
        assertEquals(Constants.STATUS_DONE, repository.updatedStatus)
    }

    @Test
    fun nonLeadOnlySeesAssignedTasks() {
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf("u1" to ProjectMember(userId = "u1", role = "Member"))
        )
        tasksLiveData.value = listOf(
            Task(id = "1", assignees = listOf("u1")),
            Task(id = "2", assignees = listOf("u2")),
            Task(id = "3", assignees = emptyList())
        )

        assertEquals(listOf("1"), viewModel.uiState.value?.filteredTasks?.map { it.id })
    }

    private class FakeTaskRepository(
        private val tasksLiveData: LiveData<List<Task>>
    ) : TaskRepository {
        var updatedTaskId: String? = null
        var updatedStatus: String? = null

        override fun observeAllTasks(): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTasks(projectId: String): LiveData<List<Task>> = tasksLiveData
        override fun observeTasksForProjects(projectIds: Set<String>): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTask(projectId: String, taskId: String): LiveData<Task?> = MutableLiveData(null)
        override suspend fun createTask(task: Task): Result<Unit> = Result.success(Unit)
        override suspend fun updateTask(task: Task, actorId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateTaskStatus(projectId: String, taskId: String, newStatus: String, actorId: String): Result<Unit> {
            updatedTaskId = taskId
            updatedStatus = newStatus
            return Result.success(Unit)
        }
        override suspend fun addComment(projectId: String, taskId: String, comment: Comment, mentionedUserIds: List<String>): Result<Unit> = Result.success(Unit)
        override suspend fun updateSubtask(projectId: String, taskId: String, subtaskId: String, isDone: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun deleteTask(task: Task): Result<Unit> = Result.success(Unit)
    }

    private class FakeUserRepository(
        private val usersLiveData: LiveData<List<User>>
    ) : UserRepository {
        override fun observeUsers(): LiveData<List<User>> = usersLiveData
        override fun observeUser(userId: String): LiveData<User?> = MutableLiveData(null)
        override suspend fun upsertUser(user: User): Result<Unit> = Result.success(Unit)
        override suspend fun findUserByEmail(email: String): Result<User?> = Result.success(null)
        override suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>): Result<Unit> = Result.success(Unit)
    }

    private class FakeProjectRepository(
        private val projectLiveData: LiveData<Project?>
    ) : ProjectRepository {
        override fun observeProjects(): LiveData<List<Project>> = MutableLiveData(emptyList())
        override fun observeProject(projectId: String): LiveData<Project?> = projectLiveData
        override suspend fun createProject(project: Project): Result<Unit> = Result.success(Unit)
        override suspend fun updateProject(project: Project): Result<Unit> = Result.success(Unit)
        override suspend fun updateCompletionPercentage(projectId: String, percentage: Float): Result<Unit> = Result.success(Unit)
        override suspend fun addMember(projectId: String, userId: String, member: ProjectMember): Result<Unit> = Result.success(Unit)
        override suspend fun updateMember(projectId: String, userId: String, member: ProjectMember): Result<Unit> = Result.success(Unit)
        override suspend fun updateMemberScore(projectId: String, userId: String, score: Float): Result<Unit> = Result.success(Unit)
    }
}
