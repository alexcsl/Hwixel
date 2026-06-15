package edu.bluejack25_2.hwixel.ui.project.tasks

import edu.bluejack25_2.hwixel.data.model.Task

data class TaskBoardUiState(
    val tasks: List<Task> = emptyList(),
    val filteredTasks: List<Task> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val filter: TaskFilter = TaskFilter(),
    val viewMode: ViewMode = ViewMode.LIST,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class TaskFilter(
    val assigneeIds: Set<String> = emptySet(),
    val priority: String? = null,
    val deadlineFrom: Long? = null,
    val deadlineTo: Long? = null
)

enum class ViewMode { LIST, KANBAN }
