package edu.bluejack252.hwixel.ui.project.tasks

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.MainDispatcherRule
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User
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
    private val usersLiveData = MutableLiveData<List<User>>()
    private lateinit var viewModel: TaskBoardViewModel

    @Before
    fun setUp() {
        viewModel = TaskBoardViewModel("p1", repository, FakeUserRepository(usersLiveData))
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

    private class FakeTaskRepository(
        private val tasksLiveData: LiveData<List<Task>>
    ) : TaskRepository {
        var updatedTaskId: String? = null
        var updatedStatus: String? = null

        override fun observeAllTasks(): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTasks(projectId: String): LiveData<List<Task>> = tasksLiveData
        override fun observeTask(projectId: String, taskId: String): LiveData<Task?> = MutableLiveData(null)
        override suspend fun createTask(task: Task): Result<Unit> = Result.success(Unit)
        override suspend fun updateTask(task: Task, actorId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateTaskStatus(projectId: String, taskId: String, newStatus: String, actorId: String): Result<Unit> {
            updatedTaskId = taskId
            updatedStatus = newStatus
            return Result.success(Unit)
        }
        override suspend fun addComment(projectId: String, taskId: String, comment: Comment): Result<Unit> = Result.success(Unit)
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
}
