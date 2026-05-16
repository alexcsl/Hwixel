package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.User
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

open class UserFirebaseSource(
    database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : UserRemoteSource {
    private val usersRef = database.reference.child("users")
    private val notificationsRef = database.reference.child("notifications")

    override fun observeUsers(): LiveData<List<User>> {
        val liveData = MutableLiveData<List<User>>()
        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.children.mapNotNull { child ->
                    child.getValue(User::class.java)?.copy(id = child.key.orEmpty())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = emptyList()
            }
        })
        return liveData
    }

    override fun observeUser(userId: String): LiveData<User?> {
        val liveData = MutableLiveData<User?>()
        usersRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.getValue(User::class.java)?.copy(id = snapshot.key.orEmpty())
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = null
            }
        })
        return liveData
    }

    override suspend fun upsertUser(user: User) {
        usersRef.child(user.id).setValue(user).awaitResult()
    }

    override suspend fun findByEmail(email: String): User? = suspendCoroutine { continuation ->
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.children.mapNotNull { child ->
                    child.getValue(User::class.java)?.copy(id = child.key.orEmpty())
                }.firstOrNull { it.email == email }
                continuation.resume(user)
            }

            override fun onCancelled(error: DatabaseError) {
                continuation.resumeWithException(error.toException())
            }
        })
    }

    override suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>) {
        notificationsRef.child(userId).child(notifId).setValue(payload).awaitResult()
    }
}
