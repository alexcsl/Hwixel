package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.source.local.UserDao
import edu.bluejack252.hwixel.data.source.local.UserEntity
import edu.bluejack252.hwixel.data.source.remote.AuthRemoteSource
import edu.bluejack252.hwixel.data.source.remote.UserRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {
    private val authSource = FakeAuthRemoteSource()
    private val userSource = FakeUserRemoteSource()
    private val userDao = FakeUserDao()
    private val repository = AuthRepositoryImpl(authSource, userSource, userDao)

    @Test
    fun loginCallsFirebaseAuthSource() = runTest {
        val result = repository.login("student@example.com", "Password1!")

        assertTrue(result.isSuccess)
        assertEquals("student@example.com", authSource.loggedInEmail)
    }

    @Test
    fun registerCreatesUserProfileInRemoteUsersNode() = runTest {
        val result = repository.register("student@example.com", "Password1!", "Student", "2500001")

        assertTrue(result.isSuccess)
        assertEquals("uid-1", userSource.upsertedUser?.id)
        assertEquals("Student", userSource.upsertedUser?.name)
        assertEquals("uid-1", userDao.upsertedUser?.id)
    }

    private class FakeAuthRemoteSource : AuthRemoteSource {
        override val currentUserId: String = "uid-1"
        var loggedInEmail: String? = null

        override suspend fun login(email: String, password: String) {
            loggedInEmail = email
        }

        override suspend fun register(email: String, password: String): String = currentUserId

        override fun logout() = Unit
    }

    private class FakeUserRemoteSource : UserRemoteSource {
        var upsertedUser: User? = null

        override fun observeUsers(): LiveData<List<User>> = MutableLiveData(emptyList())
        override fun observeUser(userId: String): LiveData<User?> = MutableLiveData(null)

        override suspend fun upsertUser(user: User) {
            upsertedUser = user
        }

        override suspend fun findByEmail(email: String): User? = null

        override suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>) = Unit
    }

    private class FakeUserDao : UserDao {
        var upsertedUser: UserEntity? = null

        override fun observeAll(): LiveData<List<UserEntity>> = MutableLiveData(emptyList())

        override fun observeById(userId: String): LiveData<UserEntity?> = MutableLiveData(null)

        override suspend fun upsert(user: UserEntity) {
            upsertedUser = user
        }
    }
}
