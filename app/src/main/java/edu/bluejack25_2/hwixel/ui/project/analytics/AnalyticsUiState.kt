package edu.bluejack25_2.hwixel.ui.project.analytics

import edu.bluejack25_2.hwixel.data.model.TeamHealthResult

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val members: List<MemberAnalyticsUi> = emptyList(),
    val selectedMemberId: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val teamHealth: TeamHealthResult? = null,
    val teamHealthLoading: Boolean = false,
    val teamHealthError: String? = null
)

data class MemberAnalyticsUi(
    val userId: String,
    val name: String,
    val assignedCount: Int,
    val completedCount: Int,
    val overdueCount: Int,
    val contributionScore: Float
) {
    val completionRatio: Float
        get() = if (assignedCount == 0) 0f else completedCount.toFloat() / assignedCount
}
