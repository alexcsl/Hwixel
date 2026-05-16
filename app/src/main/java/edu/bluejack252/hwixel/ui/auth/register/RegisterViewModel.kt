package edu.bluejack252.hwixel.ui.auth.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.repository.AuthRepository
import edu.bluejack252.hwixel.ui.auth.AuthErrorMapper
import edu.bluejack252.hwixel.util.validators.EmailValidator
import edu.bluejack252.hwixel.util.validators.PasswordValidator
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableLiveData<RegisterUiState>(RegisterUiState.Idle)
    val uiState: LiveData<RegisterUiState> = _uiState

    fun register(
        name: String,
        studentId: String,
        email: String,
        password: String,
        confirmPassword: String
    ) {
        val normalizedName = name.trim()
        val normalizedStudentId = studentId.trim()
        val normalizedEmail = email.trim()

        when {
            normalizedName.isBlank() -> {
                _uiState.value = RegisterUiState.Error(R.string.error_empty_name)
                return
            }
            normalizedStudentId.isBlank() -> {
                _uiState.value = RegisterUiState.Error(R.string.error_empty_student_id)
                return
            }
            !EmailValidator.isValid(normalizedEmail) -> {
                _uiState.value = RegisterUiState.Error(R.string.error_invalid_email)
                return
            }
            !PasswordValidator.isValid(password) -> {
                _uiState.value = RegisterUiState.Error(R.string.error_weak_password)
                return
            }
            password != confirmPassword -> {
                _uiState.value = RegisterUiState.Error(R.string.error_password_mismatch)
                return
            }
        }

        _uiState.value = RegisterUiState.Loading
        viewModelScope.launch {
            authRepository.register(
                email = normalizedEmail,
                password = password,
                name = normalizedName,
                studentId = normalizedStudentId
            ).onSuccess {
                _uiState.value = RegisterUiState.Success
            }.onFailure { error ->
                _uiState.value = RegisterUiState.Error(AuthErrorMapper.messageResId(error))
            }
        }
    }
}
