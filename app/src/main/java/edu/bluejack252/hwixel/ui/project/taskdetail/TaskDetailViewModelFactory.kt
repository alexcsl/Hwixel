package edu.bluejack252.hwixel.ui.project.taskdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository

class TaskDetailViewModelFactory(
    private val projectId: String,
    private val taskId: String,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TaskDetailViewModel(projectId, taskId, taskRepository, userRepository) as T
    }
}
