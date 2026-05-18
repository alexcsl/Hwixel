package edu.bluejack252.hwixel.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.Notification
import edu.bluejack252.hwixel.data.repository.UserRepository
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val userRepository: UserRepository
) : ViewModel() {
    private val _notifications = MediatorLiveData<List<Notification>>()
    val notifications: LiveData<List<Notification>> = _notifications

    private var currentUserId = ""
    private var hasLoaded = false

    init {
        _notifications.value = emptyList()
    }

    fun load(userId: String) {
        if (userId.isBlank() || (hasLoaded && currentUserId == userId)) return
        hasLoaded = true
        currentUserId = userId
        _notifications.addSource(userRepository.observeNotifications(userId)) { value ->
            _notifications.value = value
        }
    }

    fun markRead(notificationId: String) {
        if (currentUserId.isBlank() || notificationId.isBlank()) return
        viewModelScope.launch {
            userRepository.markNotificationRead(currentUserId, notificationId)
        }
    }
}
