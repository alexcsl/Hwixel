package edu.bluejack252.hwixel.ui.project.taskdetail

import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.HistoryEntry
import edu.bluejack252.hwixel.data.model.Task

data class TaskDetailUiState(
    val task: Task? = null,
    val comments: List<Comment> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
