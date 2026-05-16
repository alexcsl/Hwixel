package edu.bluejack252.hwixel.ui.auth.login

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val messageResId: Int) : LoginUiState()
}
