package edu.bluejack25_2.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack25_2.hwixel.data.model.AttendanceSession
import edu.bluejack25_2.hwixel.data.source.remote.AttendanceRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceRepositoryTest {
    private val source = FakeAttendanceSource()
    private val repository = AttendanceRepositoryImpl(source)

    @Test
    fun createSessionDelegatesToSource() = runTest {
        val result = repository.createSession("proj-1", 1_000L, 2_000L)
        assertTrue(result.isSuccess)
        assertEquals("proj-1", source.lastCreateProjectId)
        assertEquals(1_000L, source.lastCreateDate)
        assertEquals(2_000L, source.lastCreateNextDate)
    }

    @Test
    fun markAttendanceDelegatesToSource() = runTest {
        val result = repository.markAttendance("proj-1", "sess-1", "user-1", present = true)
        assertTrue(result.isSuccess)
        assertEquals("proj-1", source.lastMarkProjectId)
        assertEquals("sess-1", source.lastMarkSessionId)
        assertEquals("user-1", source.lastMarkUserId)
        assertEquals(true, source.lastMarkPresent)
    }

    @Test
    fun markAttendanceAbsentDelegatesToSource() = runTest {
        repository.markAttendance("proj-1", "sess-1", "user-1", present = false)
        assertEquals(false, source.lastMarkPresent)
    }

    @Test
    fun setNextSessionDateDelegatesToSource() = runTest {
        val result = repository.setNextSessionDate("proj-1", "sess-1", 5_000L)
        assertTrue(result.isSuccess)
        assertEquals(5_000L, source.lastNextDate)
    }

    private class FakeAttendanceSource : AttendanceRemoteSource {
        var lastCreateProjectId: String? = null
        var lastCreateDate: Long? = null
        var lastCreateNextDate: Long? = null
        var lastMarkProjectId: String? = null
        var lastMarkSessionId: String? = null
        var lastMarkUserId: String? = null
        var lastMarkPresent: Boolean? = null
        var lastNextDate: Long? = null

        override fun observeSessions(projectId: String): LiveData<List<AttendanceSession>> =
            MutableLiveData(emptyList())

        override suspend fun createSession(projectId: String, date: Long, nextSessionDate: Long): Result<String> {
            lastCreateProjectId = projectId
            lastCreateDate = date
            lastCreateNextDate = nextSessionDate
            return Result.success("session-id")
        }

        override suspend fun markAttendance(
            projectId: String,
            sessionId: String,
            userId: String,
            present: Boolean
        ): Result<Unit> {
            lastMarkProjectId = projectId
            lastMarkSessionId = sessionId
            lastMarkUserId = userId
            lastMarkPresent = present
            return Result.success(Unit)
        }

        override suspend fun setNextSessionDate(
            projectId: String,
            sessionId: String,
            nextDate: Long
        ): Result<Unit> {
            lastNextDate = nextDate
            return Result.success(Unit)
        }
    }
}
