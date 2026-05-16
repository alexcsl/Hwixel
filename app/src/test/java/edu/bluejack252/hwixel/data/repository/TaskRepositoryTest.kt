package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.HistoryEntry
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.source.local.TaskDao
import edu.bluejack252.hwixel.data.source.local.TaskEntity
import edu.bluejack252.hwixel.data.source.remote.TaskRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepositoryTest {
    private val firebaseSource = FakeTaskRemoteSource()
    private val localDao = FakeTaskDao()
    private val repository = TaskRepositoryImpl(firebaseSource, localDao)

    @Test
    fun createTaskWritesToFirebaseAndLocalCache() = runTest {
        val task = Task(id = "task-1", projectId = "project-1", title = "Prototype")

        val result = repository.createTask(task)

        assertTrue(result.isSuccess)
        assertEquals(task, firebaseSource.createdTask)
        assertEquals(task.id, localDao.upsertedTask?.id)
    }

    @Test
    fun updateTaskWritesToFirebaseAndLocalCache() = runTest {
        val task = Task(id = "task-1", projectId = "project-1", title = "Prototype v2")

        val result = repository.updateTask(task)

        assertTrue(result.isSuccess)
        assertEquals(task, firebaseSource.updatedTask)
        assertEquals(task.id, localDao.upsertedTask?.id)
    }

    private class FakeTaskRemoteSource : TaskRemoteSource {
        var createdTask: Task? = null
        var updatedTask: Task? = null

        override fun observeAllTasks(): LiveData<List<Task>> = MutableLiveData(emptyList())

        override fun observeTasks(projectId: String): LiveData<List<Task>> = MutableLiveData(emptyList())

        override fun observeTask(projectId: String, taskId: String): LiveData<Task?> = MutableLiveData(null)

        override suspend fun fetchTasksOnce(projectId: String): List<Task> = emptyList()

        override suspend fun createTask(task: Task) {
            createdTask = task
        }

        override suspend fun updateTask(task: Task) {
            updatedTask = task
        }

        override suspend fun updateTaskStatus(projectId: String, taskId: String, status: String) = Unit

        override suspend fun addHistoryEntry(projectId: String, taskId: String, entry: HistoryEntry) = Unit

        override suspend fun addComment(projectId: String, taskId: String, comment: Comment) = Unit

        override suspend fun updateSubtask(projectId: String, taskId: String, subtaskId: String, isDone: Boolean) = Unit

        override suspend fun deleteTask(projectId: String, taskId: String) = Unit
    }

    private class FakeTaskDao : TaskDao {
        var upsertedTask: TaskEntity? = null

        override fun observeAll(): LiveData<List<TaskEntity>> {
            return MutableLiveData(emptyList())
        }

        override fun observeByProject(projectId: String): LiveData<List<TaskEntity>> {
            return MutableLiveData(emptyList())
        }

        override suspend fun getById(taskId: String): TaskEntity? = null

        override suspend fun upsert(task: TaskEntity) {
            upsertedTask = task
        }

        override suspend fun delete(task: TaskEntity) = Unit
    }
}
