package edu.bluejack252.hwixel.ui.project.members

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.FirebaseDatabase
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.launch

class MembersViewModel(
    private val projectId: String,
    private val currentUserId: String,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MediatorLiveData<MembersUiState>()
    val uiState: LiveData<MembersUiState> = _uiState

    private val _actionResult = MutableLiveData<String?>()
    val actionResult: LiveData<String?> = _actionResult

    private var currentProject: Project? = null
    private var allUsers: List<User> = emptyList()

    init {
        _uiState.value = MembersUiState(isLoading = true)
        _uiState.addSource(projectRepository.observeProject(projectId)) { project ->
            currentProject = project
            publishState()
        }
        _uiState.addSource(userRepository.observeUsers()) { users ->
            allUsers = users
            publishState()
        }
    }

    private fun publishState() {
        val project = currentProject ?: return
        val currentRole = project.members[currentUserId]?.role.orEmpty()
        val members = project.members.map { (userId, member) ->
            val user = allUsers.firstOrNull { it.id == userId }
            MemberUi(
                userId = userId,
                name = user?.name?.takeIf { it.isNotBlank() } ?: UNKNOWN_MEMBER_NAME,
                email = user?.email.orEmpty(),
                avatarUrl = user?.avatarUrl.orEmpty(),
                role = member.role,
                status = member.status,
                contributionScore = member.contributionScore,
                phone = user?.phone.orEmpty()
            )
        }
        _uiState.value = MembersUiState(
            members = members,
            currentUserRole = currentRole,
            isLoading = false
        )
    }

    fun isCurrentUserTeamLead(): Boolean =
        currentProject?.members?.get(currentUserId)?.role == Constants.ROLE_TEAM_LEAD

    fun updateMemberRole(userId: String, newRole: String) {
        if (!isCurrentUserTeamLead()) {
            _actionResult.value = "no_permission"
            return
        }
        viewModelScope.launch {
            val existing = currentProject?.members?.get(userId) ?: return@launch
            val updated = existing.copy(role = newRole)
            val result = projectRepository.updateMember(projectId, userId, updated)
            _actionResult.value = if (result.isSuccess) "role_updated" else "error"
        }
    }

    fun toggleMemberStatus(userId: String) {
        if (!isCurrentUserTeamLead()) {
            _actionResult.value = "no_permission"
            return
        }
        viewModelScope.launch {
            val existing = currentProject?.members?.get(userId) ?: return@launch
            val newStatus = if (existing.status == Constants.MEMBER_STATUS_ACTIVE)
                Constants.MEMBER_STATUS_INACTIVE
            else
                Constants.MEMBER_STATUS_ACTIVE
            val updated = existing.copy(status = newStatus)
            val result = projectRepository.updateMember(projectId, userId, updated)
            _actionResult.value = if (result.isSuccess) "status_toggled" else "error"
        }
    }

    fun inviteMember(email: String) {
        viewModelScope.launch {
            val result = userRepository.findUserByEmail(email)
            if (result.isFailure) {
                _actionResult.value = "error:${result.exceptionOrNull()?.localizedMessage.orEmpty()}"
                return@launch
            }
            val foundUser = result.getOrNull()
            if (foundUser == null) {
                _actionResult.value = "user_not_found"
                return@launch
            }
            val newMember = ProjectMember(
                userId = foundUser.id,
                role = Constants.ROLE_OTHER,
                status = Constants.MEMBER_STATUS_ACTIVE,
                contributionScore = 0f
            )
            val addResult = projectRepository.addMember(projectId, foundUser.id, newMember)
            if (addResult.isSuccess) {
                val notifKey = FirebaseDatabase.getInstance().reference.push().key.orEmpty()
                userRepository.writeNotification(
                    foundUser.id,
                    notifKey,
                    mapOf(
                        "type" to "invite",
                        "message" to "You have been invited to a project.",
                        "timestamp" to System.currentTimeMillis(),
                        "isRead" to false,
                        "referenceId" to projectId
                    )
                )
                _actionResult.value = "invite_success"
            } else {
                _actionResult.value = "error:${addResult.exceptionOrNull()?.localizedMessage.orEmpty()}"
            }
        }
    }

    fun consumeActionResult() {
        _actionResult.value = null
    }

    private companion object {
        const val UNKNOWN_MEMBER_NAME = "Unknown member"
    }
}
