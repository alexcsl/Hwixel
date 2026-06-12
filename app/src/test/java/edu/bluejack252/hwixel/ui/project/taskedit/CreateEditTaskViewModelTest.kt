package edu.bluejack252.hwixel.ui.project.taskedit

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.MainDispatcherRule
import edu.bluejack252.hwixel.data.model.Attachment
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.Subtask
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CreateEditTaskViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val projectLiveData = MutableLiveData<Project?>()
    private val usersLiveData = MutableLiveData<List<User>>()
    private val taskLiveData = MutableLiveData<Task?>()
    private val taskRepository = FakeTaskRepository(taskLiveData)
    private lateinit var viewModel: CreateEditTaskViewModel

    @Before
    fun setUp() {
        viewModel = CreateEditTaskViewModel(
            projectId = "p1",
            taskId = "t1",
            currentUserId = "u1",
            taskRepository = taskRepository,
            projectRepository = FakeProjectRepository(projectLiveData),
            userRepository = FakeUserRepository(usersLiveData)
        )
        viewModel.uiState.observeForever { }
    }

    @Test
    fun editPreservesSubtaskIdAndDoneState() = runTest {
        projectLiveData.value = leadProject()
        usersLiveData.value = emptyList()
        taskLiveData.value = Task(
            id = "t1",
            projectId = "p1",
            title = "Old",
            subtasks = mapOf("s-real" to Subtask(id = "s-real", title = "Draft", isDone = true))
        )

        viewModel.saveTask(
            title = "Updated",
            description = "",
            deadline = 0L,
            assigneeIds = emptyList(),
            priority = "medium",
            subtasksInput = listOf(Subtask(id = "s-real", title = "Draft", isDone = true)),
            attachmentsInput = emptyList(),
            actorId = "u1"
        )

        val subtask = taskRepository.updatedTask?.subtasks?.get("s-real")
        assertEquals(true, subtask?.isDone)
        assertEquals("s-real", subtask?.id)
        assertEquals("u1", taskRepository.updatedActorId)
    }

    @Test
    fun editPreservesProvidedAttachments() = runTest {
        val attachment = Attachment(
            id = "a-real",
            label = "Design",
            url = "https://example.com/design.png",
            type = "image"
        )
        projectLiveData.value = leadProject()
        usersLiveData.value = emptyList()
        taskLiveData.value = Task(
            id = "t1",
            projectId = "p1",
            title = "Old",
            attachments = listOf(attachment)
        )

        viewModel.saveTask(
            title = "Updated",
            description = "",
            deadline = 0L,
            assigneeIds = emptyList(),
            priority = "medium",
            subtasksInput = emptyList(),
            attachmentsInput = listOf(attachment),
            actorId = "u1"
        )

        assertEquals(listOf(attachment), taskRepository.updatedTask?.attachments)
    }

    private class FakeTaskRepository(private val task: LiveData<Task?>) : TaskRepository {
        var updatedTask: Task? = null
        var updatedActorId: String? = null

        override fun observeAllTasks(): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTasks(projectId: String): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTasksForProjects(projectIds: Set<String>): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTask(projectId: String, taskId: String): LiveData<Task?> = task
        override suspend fun createTask(task: Task): Result<Unit> = Result.success(Unit)
        override suspend fun updateTask(task: Task, actorId: String): Result<Unit> {
            updatedTask = task
            updatedActorId = actorId
            return Result.success(Unit)
        }
        override suspend fun updateTaskStatus(projectId: String, taskId: String, newStatus: String, actorId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addComment(projectId: String, taskId: String, comment: Comment, mentionedUserIds: List<String>): Result<Unit> = Result.success(Unit)
        override suspend fun updateSubtask(projectId: String, taskId: String, subtaskId: String, isDone: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun deleteTask(task: Task): Result<Unit> = Result.success(Unit)
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

    private class FakeUserRepository(private val users: LiveData<List<User>>) : UserRepository {
        override fun observeUsers(): LiveData<List<User>> = users
        override fun observeUser(userId: String): LiveData<User?> = MutableLiveData(null)
        override suspend fun upsertUser(user: User): Result<Unit> = Result.success(Unit)
        override suspend fun findUserByEmail(email: String): Result<User?> = Result.success(null)
        override suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>): Result<Unit> = Result.success(Unit)
    }

    private fun leadProject(): Project {
        return Project(
            id = "p1",
            members = mapOf(
                "u1" to ProjectMember(userId = "u1", role = "Team Lead")
            )
        )
    }
}
