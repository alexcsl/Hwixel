package edu.bluejack25_2.hwixel.ui.dashboard

data class DashboardUiState(
    val projects: List<DashboardProjectUi> = emptyList(),
    val deadlines: List<DashboardDeadlineUi> = emptyList(),
    val pendingTaskCount: Int = 0,
    val completedTaskCount: Int = 0,
    val isLoading: Boolean = true
)

data class DashboardProjectUi(
    val id: String,
    val name: String,
    val role: String,
    val completionPercentage: Float
)

data class DashboardDeadlineUi(
    val taskId: String,
    val projectId: String,
    val taskTitle: String,
    val projectName: String,
    val deadline: Long,
    val countdown: CountdownParts
)

data class CountdownParts(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long
)
