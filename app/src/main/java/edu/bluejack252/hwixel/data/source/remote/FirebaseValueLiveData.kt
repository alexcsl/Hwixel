package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

internal class FirebaseValueLiveData<T>(
    private val ref: DatabaseReference,
    private val mapper: (DataSnapshot) -> T
) : LiveData<T>() {
    private var listener: ValueEventListener? = null

    override fun onActive() {
        if (listener != null) return
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                postValue(mapper(snapshot))
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }.also { ref.addValueEventListener(it) }
    }

    override fun onInactive() {
        listener?.let(ref::removeEventListener)
        listener = null
    }
}
