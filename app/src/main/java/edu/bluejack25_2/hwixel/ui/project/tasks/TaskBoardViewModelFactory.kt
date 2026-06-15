package edu.bluejack25_2.hwixel.ui.project.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack25_2.hwixel.data.repository.ProjectRepository
import edu.bluejack25_2.hwixel.data.repository.TaskRepository
import edu.bluejack25_2.hwixel.data.repository.UserRepository

class TaskBoardViewModelFactory(
    private val projectId: String,
    private val currentUserId: String,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TaskBoardViewModel(projectId, currentUserId, taskRepository, projectRepository, userRepository) as T
    }
}
