package edu.bluejack252.hwixel.ui.project.taskdetail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.MainDispatcherRule
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.Subtask
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.repository.TaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TaskDetailViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val taskLiveData = MutableLiveData<Task?>()
    private val repository = FakeTaskRepository(taskLiveData)
    private lateinit var viewModel: TaskDetailViewModel

    @Before
    fun setUp() {
        viewModel = TaskDetailViewModel("p1", "t1", repository)
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

    private class FakeTaskRepository(
        private val taskLiveData: LiveData<Task?>
    ) : TaskRepository {
        var updatedSubtaskId: String? = null
        var updatedSubtaskValue: Boolean? = null

        override fun observeAllTasks(): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTasks(projectId: String): LiveData<List<Task>> = MutableLiveData(emptyList())
        override fun observeTask(projectId: String, taskId: String): LiveData<Task?> = taskLiveData
        override suspend fun createTask(task: Task): Result<Unit> = Result.success(Unit)
        override suspend fun updateTask(task: Task, actorId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateTaskStatus(projectId: String, taskId: String, newStatus: String, actorId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addComment(projectId: String, taskId: String, comment: Comment): Result<Unit> = Result.success(Unit)
        override suspend fun updateSubtask(projectId: String, taskId: String, subtaskId: String, isDone: Boolean): Result<Unit> {
            updatedSubtaskId = subtaskId
            updatedSubtaskValue = isDone
            return Result.success(Unit)
        }
        override suspend fun deleteTask(task: Task): Result<Unit> = Result.success(Unit)
    }
}
