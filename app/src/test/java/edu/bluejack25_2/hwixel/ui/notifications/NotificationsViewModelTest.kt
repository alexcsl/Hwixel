package edu.bluejack25_2.hwixel.ui.notifications

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack25_2.hwixel.MainDispatcherRule
import edu.bluejack25_2.hwixel.data.model.Notification
import edu.bluejack25_2.hwixel.data.repository.NotificationRepository
import edu.bluejack25_2.hwixel.data.repository.ProfileSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeNotificationRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var viewModel: NotificationsViewModel

    @Before
    fun setUp() {
        repository = FakeNotificationRepository()
        settingsRepository = FakeSettingsRepository()
        viewModel = NotificationsViewModel(repository, settingsRepository, currentUserId = "u1")
        viewModel.notifications.observeForever { }
        viewModel.unreadCount.observeForever { }
    }

    @Test
    fun unreadCountCanBeDerivedFromNotifications() {
        repository.notifications.value = listOf(
            Notification(id = "n1", isRead = false),
            Notification(id = "n2", isRead = true),
            Notification(id = "n3", isRead = false)
        )

        assertEquals(2, viewModel.unreadCount.value)
    }

    @Test
    fun disabledNotificationTypesAreFilteredOut() {
        settingsRepository.disabledTypes.add("task_assigned")

        repository.notifications.value = listOf(
            Notification(id = "n1", type = "task_assigned", isRead = false),
            Notification(id = "n2", type = "mention", isRead = false)
        )

        assertEquals(listOf("n2"), viewModel.notifications.value?.map { it.id })
        assertEquals(1, viewModel.unreadCount.value)
    }

    @Test
    fun markReadUsesCurrentUserAndNotificationId() = runTest {
        viewModel.markRead("n1")
        advanceUntilIdle()

        assertEquals("u1" to "n1", repository.markedRead.single())
    }

    @Test
    fun markAllReadUsesCurrentUser() = runTest {
        viewModel.markAllRead()
        advanceUntilIdle()

        assertEquals(listOf("u1"), repository.markedAllRead)
    }

    private class FakeNotificationRepository : NotificationRepository {
        val notifications = MutableLiveData<List<Notification>>(emptyList())
        val markedRead = mutableListOf<Pair<String, String>>()
        val markedAllRead = mutableListOf<String>()

        override fun observeNotifications(userId: String): LiveData<List<Notification>> = notifications

        override suspend fun writeNotification(
            userId: String,
            type: String,
            message: String,
            referenceId: String
        ) = Unit

        override suspend fun markRead(userId: String, notifId: String): Result<Unit> {
            markedRead.add(userId to notifId)
            return Result.success(Unit)
        }

        override suspend fun markAllRead(userId: String): Result<Unit> {
            markedAllRead.add(userId)
            return Result.success(Unit)
        }

        override suspend fun writeToProjectMembers(
            projectId: String,
            type: String,
            message: String,
            referenceId: String
        ) = Unit
    }

    private class FakeSettingsRepository : ProfileSettingsRepository {
        val disabledTypes = mutableSetOf<String>()

        override fun isDarkMode(): Boolean = false
        override fun setDarkMode(enabled: Boolean) = Unit
        override fun languageTag(): String = "en"
        override fun setLanguageTag(tag: String) = Unit
        override fun isNotificationEnabled(type: String): Boolean = type !in disabledTypes
        override fun setNotificationEnabled(type: String, enabled: Boolean) {
            if (enabled) disabledTypes.remove(type) else disabledTypes.add(type)
        }
        override fun notificationSettings(): Map<String, Boolean> = emptyMap()
        override fun applyAppearance() = Unit
        override fun consumeNavigationRecoveryRequired(): Boolean = false
    }
}
