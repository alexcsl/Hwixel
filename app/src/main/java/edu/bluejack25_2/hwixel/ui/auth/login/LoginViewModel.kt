package edu.bluejack25_2.hwixel.ui.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.data.repository.AuthRepository
import edu.bluejack25_2.hwixel.ui.auth.AuthErrorMapper
import edu.bluejack25_2.hwixel.util.validators.EmailValidator
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val uiState: LiveData<LoginUiState> = _uiState

    fun login(email: String, password: String) {
        val normalizedEmail = email.trim()
        if (!EmailValidator.isValid(normalizedEmail)) {
            _uiState.value = LoginUiState.Error(R.string.error_invalid_email)
            return
        }
        if (password.isBlank()) {
            _uiState.value = LoginUiState.Error(R.string.error_empty_password)
            return
        }

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.login(normalizedEmail, password)
                .onSuccess { _uiState.value = LoginUiState.Success }
                .onFailure { error ->
                    _uiState.value = LoginUiState.Error(AuthErrorMapper.messageResId(error))
                }
        }
    }
}
