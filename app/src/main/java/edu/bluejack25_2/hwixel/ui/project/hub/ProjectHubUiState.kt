package edu.bluejack25_2.hwixel.ui.project.hub

import edu.bluejack25_2.hwixel.data.model.HistoryEntry
import edu.bluejack25_2.hwixel.data.model.Project

data class ProjectHubUiState(
    val project: Project? = null,
    val recentActivity: List<ActivityUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ActivityUi(
    val actorName: String,
    val action: String,
    val timestamp: Long
)
