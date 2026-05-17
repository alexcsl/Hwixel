package edu.bluejack252.hwixel.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack252.hwixel.data.repository.ProfileSettingsRepository
import edu.bluejack252.hwixel.data.repository.UserRepository

class ProfileViewModelFactory(
    private val userRepository: UserRepository,
    private val settingsRepository: ProfileSettingsRepository,
    private val currentUserId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(userRepository, settingsRepository, currentUserId) as T
    }
}
