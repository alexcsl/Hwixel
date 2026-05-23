package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.Notification
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.SharedPrefsProfileSettingsRepository
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

open class UserFirebaseSource(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : UserRemoteSource {
    private val usersRef = database.reference.child("users")
    private val notificationsRef = database.reference.child("notifications")
    private val usersByEmailRef = database.reference.child("usersByEmail")

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

    override fun observeNotifications(userId: String): LiveData<List<Notification>> {
        val liveData = MutableLiveData<List<Notification>>()
        notificationsRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.children.mapNotNull { child ->
                    child.getValue(Notification::class.java)?.copy(id = child.key.orEmpty())
                }.sortedByDescending { notification -> notification.timestamp }
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = emptyList()
            }
        })
        return liveData
    }

    override suspend fun fetchUser(userId: String): User? = suspendCoroutine { continuation ->
        usersRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                continuation.resume(snapshot.getValue(User::class.java)?.copy(id = snapshot.key.orEmpty()))
            }

            override fun onCancelled(error: DatabaseError) {
                continuation.resumeWithException(error.toException())
            }
        })
    }

    override suspend fun upsertUser(user: User) {
        usersRef.child(user.id).setValue(user).awaitResult()
        val emailKey = user.email.trim().lowercase().replace(".", ",")
        runCatching {
            usersByEmailRef.child(emailKey).setValue(user.id).awaitResult()
        }
    }

    override suspend fun updateBadges(userId: String, badges: List<String>) {
        usersRef.child(userId).child("badges").setValue(badges).awaitResult()
    }

    override suspend fun findByEmail(email: String): User? = suspendCoroutine { continuation ->
        val normalized = email.trim().lowercase()
        val emailKey = normalized.replace(".", ",")
        var resumed = false
        fun resumeOnce(value: User?) {
            if (!resumed) {
                resumed = true
                continuation.resume(value)
            }
        }
        fun resumeOnceWithException(t: Throwable) {
            if (!resumed) {
                resumed = true
                continuation.resumeWithException(t)
            }
        }
        fun scanFallback() {
            usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val match = snap.children.firstOrNull { child ->
                        val emailVal = child.child("email").getValue(String::class.java).orEmpty()
                        emailVal.trim().lowercase() == normalized
                    }
                    val user = match?.getValue(User::class.java)?.copy(id = match.key.orEmpty())
                    resumeOnce(user)
                }
                override fun onCancelled(error: DatabaseError) {
                    resumeOnceWithException(error.toException())
                }
            })
        }
        usersByEmailRef.child(emailKey).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val uid = snapshot.getValue(String::class.java)
                if (uid == null) {
                    scanFallback()
                    return
                }
                usersRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(userSnapshot: DataSnapshot) {
                        val user = userSnapshot.getValue(User::class.java)?.copy(id = uid)
                        if (user == null) scanFallback() else resumeOnce(user)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        scanFallback()
                    }
                })
            }
            override fun onCancelled(error: DatabaseError) {
                scanFallback()
            }
        })
    }

    override suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>) {
        val type = payload["type"] as? String
        if (type != null && !isNotificationEnabled(userId, type)) return
        notificationsRef.child(userId).child(notifId).setValue(payload).awaitResult()
    }

    override suspend fun markNotificationRead(userId: String, notifId: String) {
        notificationsRef.child(userId).child(notifId).child("isRead").setValue(true).awaitResult()
    }

    private suspend fun isNotificationEnabled(userId: String, type: String): Boolean {
        val preferenceType = SharedPrefsProfileSettingsRepository.preferenceTypeFor(type)
        return suspendCoroutine { continuation ->
            database.reference
                .child("notificationPreferences")
                .child(userId)
                .child(preferenceType)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        continuation.resume(snapshot.getValue(Boolean::class.java) ?: true)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(true)
                    }
                })
        }
    }
}
