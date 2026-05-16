package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import edu.bluejack252.hwixel.data.mapper.toDomain
import edu.bluejack252.hwixel.data.mapper.toEntity
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.source.local.ProjectDao
import edu.bluejack252.hwixel.data.source.remote.ProjectRemoteSource

interface ProjectRepository {
    fun observeProjects(): LiveData<List<Project>>
    suspend fun createProject(project: Project): Result<Unit>
    suspend fun updateProject(project: Project): Result<Unit>
}

class ProjectRepositoryImpl(
    private val firebaseSource: ProjectRemoteSource,
    private val localDao: ProjectDao
) : ProjectRepository {
    override fun observeProjects(): LiveData<List<Project>> {
        return localDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun createProject(project: Project): Result<Unit> = runCatching {
        firebaseSource.createProject(project)
        localDao.upsert(project.toEntity())
    }

    override suspend fun updateProject(project: Project): Result<Unit> = runCatching {
        firebaseSource.updateProject(project)
        localDao.upsert(project.toEntity())
    }
}
