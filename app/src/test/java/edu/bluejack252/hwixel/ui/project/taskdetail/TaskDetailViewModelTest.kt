package edu.bluejack252.hwixel.ui.project.taskdetail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.MainDispatcherRule
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.Subtask
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

class TaskDetailViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val taskLiveData = MutableLiveData<Task?>()
    private val projectLiveData = MutableLiveData<Project?>()
    private val repository = FakeTaskRepository(taskLiveData)
    private val usersLiveData = MutableLiveData<List<User>>()
    private lateinit var viewModel: TaskDetailViewModel

    @Before
    fun setUp() {
        viewModel = TaskDetailViewModel(
            "p1",
            "t1",
            "u1",
            repository,
            FakeProjectRepository(projectLiveData),
            FakeUserRepository(usersLiveData)
        )
        viewModel.uiState.observeForever { }
    }

    @Test
    fun stateIncludesSubtasksForToggleUi() {
        taskLiveData.value = Task(
            id = "t1",
            subtasks = mapOf("s1" to Subtask(id = "s1", title = "Draft", isDone = false))
        )

        assertEquals("s1", viewModel.uiState.value?.subtasks?.single()?.id)
    }

    @Test
    fun toggleSubtaskCallsRepository() = runTest {
        viewModel.toggleSubtask("s1", true)

        assertEquals("s1", repository.updatedSubtaskId)
        assertEquals(true, repository.updatedSubtaskValue)
    }

    @Test
    fun teamLeadCanEditTask() {
        taskLiveData.value = Task(id = "t1")
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf(
                "u1" to ProjectMember(userId = "u1", role = Constants.ROLE_TEAM_LEAD)
            )
        )

        assertEquals(true, viewModel.uiState.value?.canEditTask)
    }

    @Test
    fun nonLeadCannotEditTask() {
        taskLiveData.value = Task(id = "t1")
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf(
                "u1" to ProjectMember(userId = "u1", role = Constants.ROLE_OTHER)
            )
        )

        assertEquals(false, viewModel.uiState.value?.canEditTask)
    }

    @Test
    fun addCommentResolvesNameMentionsToProjectMemberIds() = runTest {
        taskLiveData.value = Task(id = "t1")
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf(
                "author" to ProjectMember(userId = "author", role = Constants.ROLE_OTHER),
                "alice-id" to ProjectMember(userId = "alice-id", role = Constants.ROLE_OTHER)
            )
        )
        usersLiveData.value = listOf(
            User(id = "author", name = "Bob Writer"),
            User(id = "alice-id", name = "Alice Smith")
        )

        viewModel.addComment("Please review this @Alice Smith.", "author")

        assertEquals(listOf("alice-id"), repository.mentionedUserIds)
    }

    @Test
    fun addCommentResolvesFirstNameMentionToProjectMemberId() = runTest {
        seedMentionUsers()

        viewModel.addComment("Please review this @Alice.", "author")

        assertEquals(listOf("alice-id"), repository.mentionedUserIds)
    }

    @Test
    fun addCommentResolvesCompactNameMentionToProjectMemberId() = runTest {
        seedMentionUsers()

        viewModel.addComment("Please review this @AliceSmith.", "author")

        assertEquals(listOf("alice-id"), repository.mentionedUserIds)
    }

    private fun seedMentionUsers() {
        taskLiveData.value = Task(id = "t1")
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf(
                "author" to ProjectMember(userId = "author", role = Constants.ROLE_OTHER),
                "alice-id" to ProjectMember(userId = "alice-id", role = Constants.ROLE_OTHER)
            )
        )
        usersLiveData.value = listOf(
            User(id = "author", name = "Bob Writer"),
            User(id = "alice-id", name = "Alice Smith")
        )
    }

    private class FakeTaskRepository(
        private val taskLiveData: LiveData<Task?>
    ) : TaskRepository {
        var updatedSubtaskId: String? = null
        var updatedSubtaskValue: Boolean? = null
        var mentionedUserIds: List<String> = emptyList()

        override fun observeAllTasks(): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTasks(projectId: String): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTask(projectId: String, taskId: String): LiveData<Task?> = taskLiveData
        override suspend fun createTask(task: Task): Result<Unit> = Result.success(Unit)
        override suspend fun updateTask(task: Task, actorId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateTaskStatus(projectId: String, taskId: String, newStatus: String, actorId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addComment(
            projectId: String,
            taskId: String,
            comment: Comment,
            mentionedUserIds: List<String>
        ): Result<Unit> {
            this.mentionedUserIds = mentionedUserIds
            return Result.success(Unit)
        }
        override suspend fun updateSubtask(projectId: String, taskId: String, subtaskId: String, isDone: Boolean): Result<Unit> {
            updatedSubtaskId = subtaskId
            updatedSubtaskValue = isDone
            return Result.success(Unit)
        }
        override suspend fun deleteTask(task: Task): Result<Unit> = Result.success(Unit)
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

    private class FakeUserRepository(
        private val usersLiveData: LiveData<List<User>>
    ) : UserRepository {
        override fun observeUsers(): LiveData<List<User>> = usersLiveData
        override fun observeUser(userId: String): LiveData<User?> = MutableLiveData(null)
        override suspend fun upsertUser(user: User): Result<Unit> = Result.success(Unit)
        override suspend fun findUserByEmail(email: String): Result<User?> = Result.success(null)
        override suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>): Result<Unit> = Result.success(Unit)
    }
}
