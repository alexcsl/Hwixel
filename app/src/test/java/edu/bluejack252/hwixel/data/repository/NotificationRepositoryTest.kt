package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.data.model.Notification
import edu.bluejack252.hwixel.data.source.remote.NotificationFirebaseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {
    private val source = FakeNotificationSource()
    private val repository = NotificationRepositoryImpl(source)

    @Test
    fun writeToProjectMembersFansOutToEachMember() = runTest {
        source.memberIds = listOf("user-1", "user-2", "user-3")
        repository.writeToProjectMembers("proj-1", "deadline", "Deadline changed", "proj-1|task-1")
        assertEquals(listOf("user-1", "user-2", "user-3"), source.writtenTo)
    }

    @Test
    fun writeToProjectMembersWithNoMembersWritesNothing() = runTest {
        source.memberIds = emptyList()
        repository.writeToProjectMembers("proj-1", "deadline", "Deadline changed", "proj-1|task-1")
        assertTrue(source.writtenTo.isEmpty())
    }

    @Test
    fun writeNotificationSwallowsExceptions() = runTest {
        source.shouldThrow = true
        // must not propagate the exception
        repository.writeNotification("user-1", "deadline", "Deadline changed", "task-1")
    }

    @Test
    fun markReadDelegatesToSource() = runTest {
        val result = repository.markRead("user-1", "notif-1")
        assertTrue(result.isSuccess)
        assertEquals("user-1" to "notif-1", source.lastMarkRead)
    }

    @Test
    fun markAllReadDelegatesToSource() = runTest {
        val result = repository.markAllRead("user-1")
        assertTrue(result.isSuccess)
        assertEquals("user-1", source.lastMarkAllRead)
    }

    private class FakeNotificationSource : NotificationFirebaseSource(null) {
        var memberIds = listOf<String>()
        var shouldThrow = false
        val writtenTo = mutableListOf<String>()
        var lastMarkRead: Pair<String, String>? = null
        var lastMarkAllRead: String? = null

        override fun observeNotifications(userId: String): LiveData<List<Notification>> =
            MutableLiveData(emptyList())

        override suspend fun writeNotification(userId: String, type: String, message: String, referenceId: String) {
            if (shouldThrow) throw RuntimeException("Source failure")
            writtenTo.add(userId)
        }

        override suspend fun markRead(userId: String, notifId: String) {
            lastMarkRead = userId to notifId
        }

        override suspend fun markAllRead(userId: String) {
            lastMarkAllRead = userId
        }

        override suspend fun fetchProjectMemberIds(projectId: String): List<String> = memberIds
    }
}
