package edu.bluejack252.hwixel.ui.project.taskedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository

class CreateEditTaskViewModelFactory(
    private val projectId: String,
    private val taskId: String,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CreateEditTaskViewModel(
            projectId, taskId, taskRepository, projectRepository, userRepository
        ) as T
    }
}
