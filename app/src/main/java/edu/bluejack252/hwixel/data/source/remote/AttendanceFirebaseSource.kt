package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.AttendanceSession
import kotlinx.coroutines.tasks.await

class AttendanceFirebaseSource {

    private val db = FirebaseDatabase.getInstance().reference

    fun observeSessions(projectId: String): LiveData<List<AttendanceSession>> {
        val liveData = MutableLiveData<List<AttendanceSession>>()
        db.child("attendance").child(projectId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sessions = snapshot.children.mapNotNull { child ->
                        val id = child.key ?: return@mapNotNull null
                        val date = child.child("date").getValue(Long::class.java) ?: 0L
                        val nextSessionDate = child.child("nextSessionDate").getValue(Long::class.java) ?: 0L
                        val records = child.child("records").children.associate { rec ->
                            rec.key!! to (rec.getValue(Boolean::class.java) ?: false)
                        }
                        AttendanceSession(
                            id = id,
                            projectId = projectId,
                            date = date,
                            nextSessionDate = nextSessionDate,
                            records = records
                        )
                    }.sortedByDescending { it.date }
                    liveData.postValue(sessions)
                }

                override fun onCancelled(error: DatabaseError) {
                    liveData.postValue(emptyList())
                }
            })
        return liveData
    }

    suspend fun createSession(
        projectId: String,
        date: Long,
        nextSessionDate: Long
    ): Result<String> = runCatching {
        val ref = db.child("attendance").child(projectId).push()
        val sessionId = ref.key ?: throw IllegalStateException("Failed to generate session ID")
        val data = mapOf(
            "date" to date,
            "nextSessionDate" to nextSessionDate
        )
        ref.setValue(data).await()
        sessionId
    }

    suspend fun markAttendance(
        projectId: String,
        sessionId: String,
        userId: String,
        present: Boolean
    ): Result<Unit> = runCatching {
        db.child("attendance")
            .child(projectId)
            .child(sessionId)
            .child("records")
            .child(userId)
            .setValue(present)
            .await()
    }

    suspend fun setNextSessionDate(
        projectId: String,
        sessionId: String,
        nextDate: Long
    ): Result<Unit> = runCatching {
        db.child("attendance")
            .child(projectId)
            .child(sessionId)
            .child("nextSessionDate")
            .setValue(nextDate)
            .await()
    }
}
