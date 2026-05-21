package edu.bluejack252.hwixel.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack252.hwixel.data.repository.NotificationRepository

class NotificationsViewModelFactory(
    private val notificationRepository: NotificationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotificationsViewModel(notificationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
