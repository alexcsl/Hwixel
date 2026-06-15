package edu.bluejack25_2.hwixel.ui.project.members

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack25_2.hwixel.MainDispatcherRule
import edu.bluejack25_2.hwixel.data.model.Project
import edu.bluejack25_2.hwixel.data.model.ProjectMember
import edu.bluejack25_2.hwixel.data.model.User
import edu.bluejack25_2.hwixel.data.repository.ProjectRepository
import edu.bluejack25_2.hwixel.data.repository.UserRepository
import edu.bluejack25_2.hwixel.util.constants.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MembersViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val projectLiveData = MutableLiveData<Project?>()
    private val usersLiveData = MutableLiveData<List<User>>()
    private lateinit var viewModel: MembersViewModel

    @Before
    fun setUp() {
        viewModel = MembersViewModel(
            projectId = "p1",
            currentUserId = "lead",
            projectRepository = FakeProjectRepository(projectLiveData),
            userRepository = FakeUserRepository(usersLiveData)
        )
        viewModel.uiState.observeForever { }
    }

    @Test
    fun memberStateResolvesPhoneAndTeamLeadRole() {
        projectLiveData.value = Project(
            id = "p1",
            members = mapOf(
                "lead" to ProjectMember("lead", Constants.ROLE_TEAM_LEAD, Constants.MEMBER_STATUS_ACTIVE, 10f),
                "u1" to ProjectMember("u1", Constants.ROLE_OTHER, Constants.MEMBER_STATUS_ACTIVE, 5f)
            )
        )
        usersLiveData.value = listOf(User(id = "u1", name = "Bob", email = "b@example.com", phone = "628123"))

        val state = viewModel.uiState.value!!

        assertTrue(viewModel.isCurrentUserTeamLead())
        assertEquals("628123", state.members.first { it.userId == "u1" }.phone)
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
}
