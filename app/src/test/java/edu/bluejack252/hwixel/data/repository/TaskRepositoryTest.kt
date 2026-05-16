package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

        override fun observeTasks(projectId: String): LiveData<List<Task>> = MutableLiveData(emptyList())

        override suspend fun createTask(task: Task) {
            createdTask = task
        }

        override suspend fun updateTask(task: Task) {
            updatedTask = task
        }
    }

    private class FakeTaskDao : TaskDao {
        var upsertedTask: TaskEntity? = null

        override fun observeByProject(projectId: String): LiveData<List<TaskEntity>> {
            return MutableLiveData(emptyList())
        }

        override suspend fun upsert(task: TaskEntity) {
            upsertedTask = task
        }

        override suspend fun delete(task: TaskEntity) = Unit
    }
}
