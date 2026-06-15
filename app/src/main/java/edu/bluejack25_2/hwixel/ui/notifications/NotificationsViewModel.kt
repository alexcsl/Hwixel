package edu.bluejack25_2.hwixel.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import edu.bluejack25_2.hwixel.data.model.Notification
import edu.bluejack25_2.hwixel.data.repository.NotificationRepository
import edu.bluejack25_2.hwixel.data.repository.ProfileSettingsRepository
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val repository: NotificationRepository,
    private val settingsRepository: ProfileSettingsRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableLiveData<NotificationsUiState>(NotificationsUiState.Idle)
    val uiState: LiveData<NotificationsUiState> = _uiState

    val notifications: LiveData<List<Notification>> = repository.observeNotifications(currentUserId).map { list ->
        list.filter { notification ->
            settingsRepository.isNotificationEnabled(notification.type)
        }
    }

    val unreadCount: LiveData<Int> = notifications.map { list ->
        list.count { !it.isRead }
    }

    fun markRead(notifId: String) {
        if (currentUserId.isBlank() || notifId.isBlank()) return
        viewModelScope.launch {
            repository.markRead(currentUserId, notifId)
        }
    }

    fun markAllRead() {
        if (currentUserId.isBlank()) return
        _uiState.value = NotificationsUiState.Loading
        viewModelScope.launch {
            val result = repository.markAllRead(currentUserId)
            _uiState.value = if (result.isSuccess) {
                NotificationsUiState.MarkedAllRead
            } else {
                NotificationsUiState.Error(result.exceptionOrNull()?.message ?: "")
            }
        }
    }
}
