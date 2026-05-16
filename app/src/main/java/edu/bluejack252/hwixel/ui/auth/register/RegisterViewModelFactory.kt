package edu.bluejack252.hwixel.ui.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack252.hwixel.data.repository.AuthRepository

class RegisterViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RegisterViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
