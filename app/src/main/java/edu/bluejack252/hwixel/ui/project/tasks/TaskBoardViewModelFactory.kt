package edu.bluejack252.hwixel.ui.project.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack252.hwixel.data.repository.TaskRepository

class TaskBoardViewModelFactory(
    private val projectId: String,
    private val taskRepository: TaskRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TaskBoardViewModel(projectId, taskRepository) as T
    }
}
