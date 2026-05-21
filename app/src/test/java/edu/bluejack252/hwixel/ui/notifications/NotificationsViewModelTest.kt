package edu.bluejack252.hwixel.ui.notifications

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.MainDispatcherRule
import edu.bluejack252.hwixel.data.model.Notification
import edu.bluejack252.hwixel.data.repository.NotificationRepository
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
    private lateinit var viewModel: NotificationsViewModel

    @Before
    fun setUp() {
        repository = FakeNotificationRepository()
        viewModel = NotificationsViewModel(repository)
        viewModel.notifications.observeForever { }
    }

    @Test
    fun unreadCountCanBeDerivedFromNotifications() {
        repository.notifications.value = listOf(
            Notification(id = "n1", isRead = false),
            Notification(id = "n2", isRead = true),
            Notification(id = "n3", isRead = false)
        )

        viewModel.load("u1")

        assertEquals(2, viewModel.notifications.value.orEmpty().count { !it.isRead })
    }

    @Test
    fun markReadUsesCurrentUserAndNotificationId() = runTest {
        viewModel.load("u1")

        viewModel.markRead("n1")
        advanceUntilIdle()

        assertEquals("u1" to "n1", repository.markedRead.single())
    }

    @Test
    fun markAllReadUsesCurrentUser() = runTest {
        viewModel.load("u1")

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
}
