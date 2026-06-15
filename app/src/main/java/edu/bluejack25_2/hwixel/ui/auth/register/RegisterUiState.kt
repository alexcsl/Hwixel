package edu.bluejack25_2.hwixel.ui.auth.register

sealed class RegisterUiState {
    data object Idle : RegisterUiState()
    data object Loading : RegisterUiState()
    data object Success : RegisterUiState()
    data class Error(val messageResId: Int) : RegisterUiState()
}
