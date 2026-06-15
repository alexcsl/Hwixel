package edu.bluejack25_2.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack25_2.hwixel.data.mapper.toEntity
import edu.bluejack25_2.hwixel.data.model.Notification
import edu.bluejack25_2.hwixel.data.model.User
import edu.bluejack25_2.hwixel.data.source.local.UserDao
import edu.bluejack25_2.hwixel.data.source.remote.UserRemoteSource

interface UserRepository {
    fun observeUsers(): LiveData<List<User>>
    fun observeUser(userId: String): LiveData<User?>
    fun observeNotifications(userId: String): LiveData<List<Notification>> = MutableLiveData(emptyList())
    suspend fun refreshUser(userId: String): Result<User?> = Result.success(null)
    suspend fun upsertUser(user: User): Result<Unit>
    suspend fun findUserByEmail(email: String): Result<User?>
    suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>): Result<Unit>
    suspend fun markNotificationRead(userId: String, notifId: String): Result<Unit> = Result.success(Unit)
}

class UserRepositoryImpl(
    private val firebaseSource: UserRemoteSource,
    private val localDao: UserDao
) : UserRepository {

    override fun observeUsers(): LiveData<List<User>> {
        return firebaseSource.observeUsers()
    }

    override fun observeUser(userId: String): LiveData<User?> {
        return firebaseSource.observeUser(userId)
    }

    override fun observeNotifications(userId: String): LiveData<List<Notification>> {
        return firebaseSource.observeNotifications(userId)
    }

    override suspend fun refreshUser(userId: String): Result<User?> = runCatching {
        val user = firebaseSource.fetchUser(userId)
        if (user != null) localDao.upsert(user.toEntity())
        user
    }

    override suspend fun upsertUser(user: User): Result<Unit> = runCatching {
        firebaseSource.upsertUser(user)
        localDao.upsert(user.toEntity())
    }

    override suspend fun findUserByEmail(email: String): Result<User?> = runCatching {
        firebaseSource.findByEmail(email)
    }

    override suspend fun writeNotification(
        userId: String,
        notifId: String,
        payload: Map<String, Any>
    ): Result<Unit> = runCatching {
        firebaseSource.writeNotification(userId, notifId, payload)
    }

    override suspend fun markNotificationRead(
        userId: String,
        notifId: String
    ): Result<Unit> = runCatching {
        firebaseSource.markNotificationRead(userId, notifId)
    }
}
