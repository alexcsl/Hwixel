package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import edu.bluejack252.hwixel.data.mapper.toDomain
import edu.bluejack252.hwixel.data.mapper.toEntity
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.source.local.UserDao
import edu.bluejack252.hwixel.data.source.remote.UserRemoteSource

interface UserRepository {
    fun observeUsers(): LiveData<List<User>>
    fun observeUser(userId: String): LiveData<User?>
    suspend fun upsertUser(user: User): Result<Unit>
    suspend fun findUserByEmail(email: String): Result<User?>
    suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>): Result<Unit>
}

class UserRepositoryImpl(
    private val firebaseSource: UserRemoteSource,
    private val localDao: UserDao
) : UserRepository {

    override fun observeUsers(): LiveData<List<User>> {
        return localDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeUser(userId: String): LiveData<User?> {
        return localDao.observeById(userId).map { entity -> entity?.toDomain() }
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
}
