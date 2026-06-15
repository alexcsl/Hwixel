package edu.bluejack25_2.hwixel.ui.project.members

data class MembersUiState(
    val members: List<MemberUi> = emptyList(),
    val currentUserRole: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class MemberUi(
    val userId: String,
    val name: String,
    val email: String,
    val avatarUrl: String,
    val role: String,
    val status: String,
    val contributionScore: Float,
    val phone: String = ""
)
