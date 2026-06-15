package edu.bluejack25_2.hwixel.ui.notifications

sealed class NotificationsUiState {
    object Idle : NotificationsUiState()
    object Loading : NotificationsUiState()
    object MarkedAllRead : NotificationsUiState()
    data class Error(val message: String) : NotificationsUiState()
}
