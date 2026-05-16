package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.source.local.UserDao
import edu.bluejack252.hwixel.data.source.local.UserEntity
import edu.bluejack252.hwixel.data.source.remote.UserRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryTest {
    private val firebaseSource = FakeUserRemoteSource()
    private val localDao = FakeUserDao()
    private val repository = UserRepositoryImpl(firebaseSource, localDao)

    @Test
    fun upsertUserWritesToFirebaseAndLocalCache() = runTest {
        val user = User(id = "user-1", name = "Alice", email = "alice@example.com")

        val result = repository.upsertUser(user)

        assertTrue(result.isSuccess)
        assertEquals(user, firebaseSource.upsertedUser)
        assertEquals(user.id, localDao.upsertedUser?.id)
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
