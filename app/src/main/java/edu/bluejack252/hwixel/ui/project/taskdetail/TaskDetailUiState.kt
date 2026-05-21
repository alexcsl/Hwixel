package edu.bluejack252.hwixel.ui.project.taskdetail

import edu.bluejack252.hwixel.data.model.Attachment
import edu.bluejack252.hwixel.data.model.Subtask
import edu.bluejack252.hwixel.data.model.Task

data class TaskDetailUiState(
    val task: Task? = null,
    val assigneeNames: List<String> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val comments: List<CommentUi> = emptyList(),
    val history: List<HistoryUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class CommentUi(
    val id: String,
    val authorName: String,
    val content: String,
    val timestamp: Long
)

data class HistoryUi(
    val id: String,
    val actorName: String,
    val action: String,
    val timestamp: Long
)
