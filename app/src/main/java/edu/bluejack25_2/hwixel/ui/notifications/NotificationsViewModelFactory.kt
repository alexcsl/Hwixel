package edu.bluejack25_2.hwixel.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack25_2.hwixel.data.repository.NotificationRepository
import edu.bluejack25_2.hwixel.data.repository.ProfileSettingsRepository

class NotificationsViewModelFactory(
    private val repository: NotificationRepository,
    private val settingsRepository: ProfileSettingsRepository,
    private val currentUserId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        NotificationsViewModel(repository, settingsRepository, currentUserId) as T
}
