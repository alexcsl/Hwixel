package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.source.local.ProjectDao
import edu.bluejack252.hwixel.data.source.local.ProjectEntity
import edu.bluejack252.hwixel.data.source.remote.ProjectRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRepositoryTest {
    private val firebaseSource = FakeProjectRemoteSource()
    private val localDao = FakeProjectDao()
    private val repository = ProjectRepositoryImpl(firebaseSource, localDao)

    @Test
    fun createProjectWritesToFirebaseAndLocalCache() = runTest {
        val project = Project(id = "project-1", name = "Research App")

        val result = repository.createProject(project)

        assertTrue(result.isSuccess)
        assertEquals(project, firebaseSource.createdProject)
        assertEquals(project.id, localDao.upsertedProject?.id)
    }

    @Test
    fun updateProjectWritesToFirebaseAndLocalCache() = runTest {
        val project = Project(id = "project-1", name = "Updated")

        val result = repository.updateProject(project)

        assertTrue(result.isSuccess)
        assertEquals(project, firebaseSource.updatedProject)
        assertEquals(project.id, localDao.upsertedProject?.id)
    }

    private class FakeProjectRemoteSource : ProjectRemoteSource {
        var createdProject: Project? = null
        var updatedProject: Project? = null

        override fun observeProjects(): LiveData<List<Project>> = MutableLiveData(emptyList())

        override fun observeProject(projectId: String): LiveData<Project?> = MutableLiveData(null)

        override suspend fun createProject(project: Project) {
            createdProject = project
        }

        override suspend fun updateProject(project: Project) {
            updatedProject = project
        }

        override suspend fun updateCompletionPercentage(projectId: String, percentage: Float) = Unit

        override suspend fun addMember(projectId: String, userId: String, member: ProjectMember) = Unit

        override suspend fun updateMember(projectId: String, userId: String, member: ProjectMember) = Unit

        override suspend fun updateMemberScore(projectId: String, userId: String, score: Float) = Unit
    }

    private class FakeProjectDao : ProjectDao {
        var upsertedProject: ProjectEntity? = null

        override fun observeAll(): LiveData<List<ProjectEntity>> = MutableLiveData(emptyList())

        override suspend fun upsert(project: ProjectEntity) {
            upsertedProject = project
        }

        override suspend fun delete(project: ProjectEntity) = Unit
    }
}
