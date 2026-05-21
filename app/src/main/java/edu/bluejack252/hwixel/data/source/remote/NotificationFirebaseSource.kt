package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.Notification
import kotlinx.coroutines.tasks.await

class NotificationFirebaseSource {

    private val db = FirebaseDatabase.getInstance().reference

    fun observeNotifications(userId: String): LiveData<List<Notification>> {
        return object : LiveData<List<Notification>>() {
            private val ref = db.child("notifications").child(userId)
            private var listener: ValueEventListener? = null

            override fun onActive() {
                if (listener != null) return
                listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = snapshot.children.mapNotNull { child ->
                            val type = child.child("type").getValue(String::class.java) ?: return@mapNotNull null
                            val message = child.child("message").getValue(String::class.java) ?: ""
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val isRead = child.child("isRead").getValue(Boolean::class.java) ?: false
                            val referenceId = child.child("referenceId").getValue(String::class.java) ?: ""
                            Notification(
                                id = child.key ?: "",
                                type = type,
                                message = message,
                                timestamp = timestamp,
                                isRead = isRead,
                                referenceId = referenceId
                            )
                        }.sortedByDescending { it.timestamp }
                        postValue(list)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        postValue(emptyList())
                    }
                }.also { ref.addValueEventListener(it) }
            }

            override fun onInactive() {
                listener?.let(ref::removeEventListener)
                listener = null
            }
        }
    }

    suspend fun writeNotification(
        userId: String,
        type: String,
        message: String,
        referenceId: String
    ) {
        val ref = db.child("notifications").child(userId).push()
        val data = mapOf(
            "type" to type,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false,
            "referenceId" to referenceId
        )
        ref.setValue(data).await()
    }

    suspend fun markRead(userId: String, notifId: String) {
        db.child("notifications").child(userId).child(notifId).child("isRead").setValue(true).await()
    }

    suspend fun markAllRead(userId: String) {
        val snapshot = db.child("notifications").child(userId).get().await()
        val updates = mutableMapOf<String, Any>()
        snapshot.children.forEach { child ->
            val key = child.key ?: return@forEach
            updates["$key/isRead"] = true
        }
        if (updates.isNotEmpty()) {
            db.child("notifications").child(userId).updateChildren(updates).await()
        }
    }

    suspend fun fetchProjectMemberIds(projectId: String): List<String> {
        val snapshot = db.child("projects").child(projectId).child("members").get().await()
        return snapshot.children.mapNotNull { it.key }
    }
}
