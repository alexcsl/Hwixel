package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import edu.bluejack252.hwixel.data.mapper.toDomain
import edu.bluejack252.hwixel.data.mapper.toEntity
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.source.local.TaskDao
import edu.bluejack252.hwixel.data.source.remote.TaskRemoteSource

interface TaskRepository {
    fun observeTasks(projectId: String): LiveData<List<Task>>
    suspend fun createTask(task: Task): Result<Unit>
    suspend fun updateTask(task: Task): Result<Unit>
}

class TaskRepositoryImpl(
    private val firebaseSource: TaskRemoteSource,
    private val localDao: TaskDao
) : TaskRepository {
    override fun observeTasks(projectId: String): LiveData<List<Task>> {
        return localDao.observeByProject(projectId).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun createTask(task: Task): Result<Unit> = runCatching {
        firebaseSource.createTask(task)
        localDao.upsert(task.toEntity())
    }

    override suspend fun updateTask(task: Task): Result<Unit> = runCatching {
        firebaseSource.updateTask(task)
        localDao.upsert(task.toEntity())
    }
}
