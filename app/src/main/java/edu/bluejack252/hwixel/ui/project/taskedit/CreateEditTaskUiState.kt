package edu.bluejack252.hwixel.ui.project.taskedit

import edu.bluejack252.hwixel.data.model.Subtask
import edu.bluejack252.hwixel.data.model.Task

sealed class CreateEditTaskUiState {
    object Idle : CreateEditTaskUiState()
    object Loading : CreateEditTaskUiState()
    data class Loaded(
        val task: Task?,
        val projectMembers: List<MemberOption>
    ) : CreateEditTaskUiState()
    object Success : CreateEditTaskUiState()
    data class Error(val message: String) : CreateEditTaskUiState()
}

data class MemberOption(
    val userId: String,
    val name: String,
    var isSelected: Boolean = false
)
