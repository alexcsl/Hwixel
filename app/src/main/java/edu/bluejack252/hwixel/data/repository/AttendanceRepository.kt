package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import edu.bluejack252.hwixel.data.model.AttendanceSession
import edu.bluejack252.hwixel.data.source.remote.AttendanceFirebaseSource

interface AttendanceRepository {
    fun observeSessions(projectId: String): LiveData<List<AttendanceSession>>
    suspend fun createSession(projectId: String, date: Long, nextSessionDate: Long): Result<String>
    suspend fun markAttendance(projectId: String, sessionId: String, userId: String, present: Boolean): Result<Unit>
    suspend fun setNextSessionDate(projectId: String, sessionId: String, nextDate: Long): Result<Unit>
}

class AttendanceRepositoryImpl(
    private val firebaseSource: AttendanceFirebaseSource
) : AttendanceRepository {

    override fun observeSessions(projectId: String): LiveData<List<AttendanceSession>> =
        firebaseSource.observeSessions(projectId)

    override suspend fun createSession(
        projectId: String,
        date: Long,
        nextSessionDate: Long
    ): Result<String> = firebaseSource.createSession(projectId, date, nextSessionDate)

    override suspend fun markAttendance(
        projectId: String,
        sessionId: String,
        userId: String,
        present: Boolean
    ): Result<Unit> = firebaseSource.markAttendance(projectId, sessionId, userId, present)

    override suspend fun setNextSessionDate(
        projectId: String,
        sessionId: String,
        nextDate: Long
    ): Result<Unit> = firebaseSource.setNextSessionDate(projectId, sessionId, nextDate)
}
