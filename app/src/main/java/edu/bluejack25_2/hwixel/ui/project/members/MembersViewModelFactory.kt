package edu.bluejack25_2.hwixel.ui.project.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack25_2.hwixel.data.repository.ProjectRepository
import edu.bluejack25_2.hwixel.data.repository.UserRepository

class MembersViewModelFactory(
    private val projectId: String,
    private val currentUserId: String,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MembersViewModel(projectId, currentUserId, projectRepository, userRepository) as T
    }
}
