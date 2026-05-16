package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.User

open class UserFirebaseSource(
    database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : UserRemoteSource {
    private val usersRef = database.reference.child("users")

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

    override suspend fun upsertUser(user: User) {
        usersRef.child(user.id).setValue(user).awaitResult()
    }
}
