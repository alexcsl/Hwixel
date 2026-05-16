package edu.bluejack252.hwixel.data.repository

import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.mapper.toEntity
import edu.bluejack252.hwixel.data.source.local.UserDao
import edu.bluejack252.hwixel.data.source.remote.AuthRemoteSource
import edu.bluejack252.hwixel.data.source.remote.UserRemoteSource

interface AuthRepository {
    val currentUserId: String?
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(email: String, password: String, name: String, studentId: String): Result<User>
    fun logout()
}

class AuthRepositoryImpl(
    private val authFirebaseSource: AuthRemoteSource,
    private val userFirebaseSource: UserRemoteSource,
    private val userDao: UserDao
) : AuthRepository {
    override val currentUserId: String?
        get() = authFirebaseSource.currentUserId

    override suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        authFirebaseSource.login(email, password)
        Unit
    }

    override suspend fun register(
        email: String,
        password: String,
        name: String,
        studentId: String
    ): Result<User> = runCatching {
        val userId = authFirebaseSource.register(email, password)
        val user = User(
            id = userId,
            name = name,
            studentId = studentId,
            email = email,
            avatarUrl = "",
            badges = emptyList()
        )
        userFirebaseSource.upsertUser(user)
        userDao.upsert(user.toEntity())
        user
    }

    override fun logout() {
        authFirebaseSource.logout()
    }
}
