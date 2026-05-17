package edu.bluejack252.hwixel.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.ProfileSettingsRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val settingsRepository: ProfileSettingsRepository,
    private val currentUserId: String
) : ViewModel() {
    private val _uiState = MediatorLiveData<ProfileUiState>()
    val uiState: LiveData<ProfileUiState> = _uiState

    private val _saveResult = MutableLiveData<Result<Unit>?>()
    val saveResult: LiveData<Result<Unit>?> = _saveResult

    private var userSource: LiveData<User?>? = null

    init {
        _uiState.value = ProfileUiState(
            isDarkMode = settingsRepository.isDarkMode(),
            languageTag = settingsRepository.languageTag(),
            notificationSettings = settingsRepository.notificationSettings()
        )
        observeUser()
        refreshProfile()
    }

    private fun observeUser() {
        if (currentUserId.isBlank()) return
        userSource?.let { _uiState.removeSource(it) }
        userSource = userRepository.observeUser(currentUserId).also { source ->
            _uiState.addSource(source) { user ->
                publish { copy(user = user, isLoading = false) }
            }
        }
    }

    fun refreshProfile() {
        if (currentUserId.isBlank()) {
            publish { copy(isLoading = false, errorMessage = "Missing user session.") }
            return
        }
        publish { copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = userRepository.refreshUser(currentUserId)
            if (result.isFailure) {
                publish {
                    copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    fun saveProfile(name: String, studentId: String, avatarUrl: String) {
        val current = _uiState.value?.user ?: return
        if (name.isBlank() || studentId.isBlank()) return
        publish { copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = userRepository.upsertUser(
                current.copy(
                    name = name.trim(),
                    studentId = studentId.trim(),
                    avatarUrl = avatarUrl.trim()
                )
            )
            _saveResult.value = result
            publish {
                copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        settingsRepository.setDarkMode(enabled)
        publish { copy(isDarkMode = enabled) }
    }

    fun setLanguage(tag: String) {
        settingsRepository.setLanguageTag(tag)
        publish { copy(languageTag = settingsRepository.languageTag()) }
    }

    fun setNotificationEnabled(type: String, enabled: Boolean) {
        settingsRepository.setNotificationEnabled(type, enabled)
        publish { copy(notificationSettings = settingsRepository.notificationSettings()) }
    }

    fun consumeSaveResult() {
        _saveResult.value = null
    }

    private fun publish(reducer: ProfileUiState.() -> ProfileUiState) {
        _uiState.value = (_uiState.value ?: ProfileUiState()).reducer()
    }
}
