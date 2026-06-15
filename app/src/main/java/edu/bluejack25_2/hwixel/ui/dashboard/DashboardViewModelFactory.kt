package edu.bluejack25_2.hwixel.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack25_2.hwixel.data.repository.ProjectRepository
import edu.bluejack25_2.hwixel.data.repository.TaskRepository

class DashboardViewModelFactory(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(projectRepository, taskRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
