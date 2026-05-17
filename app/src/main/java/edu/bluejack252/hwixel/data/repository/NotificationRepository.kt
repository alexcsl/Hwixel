package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import edu.bluejack252.hwixel.data.model.Notification
import edu.bluejack252.hwixel.data.source.remote.NotificationFirebaseSource

interface NotificationRepository {
    fun observeNotifications(userId: String): LiveData<List<Notification>>
    suspend fun writeNotification(userId: String, type: String, message: String, referenceId: String)
    suspend fun markRead(userId: String, notifId: String): Result<Unit>
    suspend fun markAllRead(userId: String): Result<Unit>
    suspend fun writeToProjectMembers(projectId: String, type: String, message: String, referenceId: String)
}

class NotificationRepositoryImpl(
    private val source: NotificationFirebaseSource
) : NotificationRepository {

    override fun observeNotifications(userId: String) = source.observeNotifications(userId)

    override suspend fun writeNotification(userId: String, type: String, message: String, referenceId: String) {
        runCatching { source.writeNotification(userId, type, message, referenceId) }
    }

    override suspend fun markRead(userId: String, notifId: String) = runCatching {
        source.markRead(userId, notifId)
    }

    override suspend fun markAllRead(userId: String) = runCatching {
        source.markAllRead(userId)
    }

    override suspend fun writeToProjectMembers(projectId: String, type: String, message: String, referenceId: String) {
        runCatching {
            val memberIds = source.fetchProjectMemberIds(projectId)
            memberIds.forEach { uid ->
                source.writeNotification(uid, type, message, referenceId)
            }
        }
    }
}
