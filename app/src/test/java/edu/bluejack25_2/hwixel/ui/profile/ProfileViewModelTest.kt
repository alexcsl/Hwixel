package edu.bluejack25_2.hwixel.ui.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import edu.bluejack25_2.hwixel.data.model.User
import edu.bluejack25_2.hwixel.data.repository.ProfileSettingsRepository
import edu.bluejack25_2.hwixel.data.repository.SharedPrefsProfileSettingsRepository
import edu.bluejack25_2.hwixel.data.repository.UserRepository
import edu.bluejack25_2.hwixel.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveProfileUpdatesRepositoryWithTrimmedValues() = runTest {
        val users = MutableLiveData(User(id = "u1", name = "Old", studentId = "S1"))
        val repository = FakeUserRepository(users)
        val viewModel = ProfileViewModel(repository, FakeSettingsRepository(), "u1")
        val observer = Observer<ProfileUiState> { }
        viewModel.uiState.observeForever(observer)

        viewModel.saveProfile("  New Name  ", "  260200  ", "", "content://avatar")
        advanceUntilIdle()

        assertEquals("New Name", repository.savedUser?.name)
        assertEquals("260200", repository.savedUser?.studentId)
        assertEquals("content://avatar", repository.savedUser?.avatarUrl)
        viewModel.uiState.removeObserver(observer)
    }

    @Test
    fun settingsChangesPublishCurrentPreferences() {
        val viewModel = ProfileViewModel(
            FakeUserRepository(MutableLiveData(User(id = "u1"))),
            FakeSettingsRepository(),
            "u1"
        )

        viewModel.setDarkMode(false)
        viewModel.setLanguage("id")
        viewModel.setNotificationEnabled(SharedPrefsProfileSettingsRepository.NOTIF_DEADLINE, false)

        val state = viewModel.uiState.value!!
        assertFalse(state.isDarkMode)
        assertEquals("id", state.languageTag)
        assertFalse(state.notificationSettings.getValue(SharedPrefsProfileSettingsRepository.NOTIF_DEADLINE))
    }

    private class FakeUserRepository(
        private val user: MutableLiveData<User?>
    ) : UserRepository {
        var savedUser: User? = null

        override fun observeUsers(): LiveData<List<User>> = MutableLiveData(emptyList())
        override fun observeUser(userId: String): LiveData<User?> = user
        override suspend fun refreshUser(userId: String): Result<User?> = Result.success(user.value)
        override suspend fun upsertUser(user: User): Result<Unit> {
            savedUser = user
            this.user.value = user
            return Result.success(Unit)
        }
        override suspend fun findUserByEmail(email: String): Result<User?> = Result.success(null)
        override suspend fun writeNotification(
            userId: String,
            notifId: String,
            payload: Map<String, Any>
        ): Result<Unit> = Result.success(Unit)
    }

    private class FakeSettingsRepository : ProfileSettingsRepository {
        private var darkMode = true
        private var language = "en"
        private val notifications = SharedPrefsProfileSettingsRepository.NOTIFICATION_TYPES
            .associateWith { true }
            .toMutableMap()

        override fun isDarkMode(): Boolean = darkMode
        override fun setDarkMode(enabled: Boolean) {
            darkMode = enabled
        }
        override fun languageTag(): String = language
        override fun setLanguageTag(tag: String) {
            language = tag
        }
        override fun isNotificationEnabled(type: String): Boolean = notifications[type] ?: true
        override fun setNotificationEnabled(type: String, enabled: Boolean) {
            notifications[type] = enabled
        }
        override fun notificationSettings(): Map<String, Boolean> = notifications.toMap()
        override fun applyAppearance() = Unit
        override fun consumeNavigationRecoveryRequired(): Boolean = false
    }
}
