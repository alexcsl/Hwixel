package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import edu.bluejack252.hwixel.data.mapper.toEntity
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.source.local.ProjectDao
import edu.bluejack252.hwixel.data.source.remote.ProjectRemoteSource

interface ProjectRepository {
    fun observeProjects(): LiveData<List<Project>>
    fun observeProjectsForUser(userId: String): LiveData<List<Project>> = observeProjects()
    fun observeProject(projectId: String): LiveData<Project?>
    suspend fun createProject(project: Project): Result<Unit>
    suspend fun updateProject(project: Project): Result<Unit>
    suspend fun updateCompletionPercentage(projectId: String, percentage: Float): Result<Unit>
    suspend fun addMember(projectId: String, userId: String, member: ProjectMember): Result<Unit>
    suspend fun updateMember(projectId: String, userId: String, member: ProjectMember): Result<Unit>
    suspend fun updateMemberScore(projectId: String, userId: String, score: Float): Result<Unit>
}

class ProjectRepositoryImpl(
    private val firebaseSource: ProjectRemoteSource,
    private val localDao: ProjectDao
) : ProjectRepository {

    override fun observeProjects(): LiveData<List<Project>> {
        return firebaseSource.observeProjects()
    }

    override fun observeProjectsForUser(userId: String): LiveData<List<Project>> {
        return firebaseSource.observeProjects()
    }

    override fun observeProject(projectId: String): LiveData<Project?> {
        return firebaseSource.observeProject(projectId)
    }

    override suspend fun createProject(project: Project): Result<Unit> = runCatching {
        val createdProject = firebaseSource.createProject(project)
        localDao.upsert(createdProject.toEntity())
    }

    override suspend fun updateProject(project: Project): Result<Unit> = runCatching {
        firebaseSource.updateProject(project)
        localDao.upsert(project.toEntity())
    }

    override suspend fun updateCompletionPercentage(
        projectId: String,
        percentage: Float
    ): Result<Unit> = runCatching {
        firebaseSource.updateCompletionPercentage(projectId, percentage)
    }

    override suspend fun addMember(
        projectId: String,
        userId: String,
        member: ProjectMember
    ): Result<Unit> = runCatching {
        firebaseSource.addMember(projectId, userId, member)
    }

    override suspend fun updateMember(
        projectId: String,
        userId: String,
        member: ProjectMember
    ): Result<Unit> = runCatching {
        firebaseSource.updateMember(projectId, userId, member)
    }

    override suspend fun updateMemberScore(
        projectId: String,
        userId: String,
        score: Float
    ): Result<Unit> = runCatching {
        firebaseSource.updateMemberScore(projectId, userId, score)
    }

}
