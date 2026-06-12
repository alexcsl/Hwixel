package edu.bluejack252.hwixel.ui.project.attendance

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.data.model.AttendanceSession
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.repository.AttendanceRepository
import edu.bluejack252.hwixel.util.constants.Constants
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AttendanceViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val sessionsLiveData = MutableLiveData<List<AttendanceSession>>(emptyList())
    private val repository = FakeAttendanceRepository(sessionsLiveData)
    private val viewModel = AttendanceViewModel(repository, "p1")

    @Test
    fun computeMemberAttendancePercentageUsesAllSessionsAsDenominator() {
        val sessions = listOf(
            AttendanceSession(id = "s1", records = mapOf("u1" to true)),
            AttendanceSession(id = "s2", records = mapOf("u1" to false)),
            AttendanceSession(id = "s3", records = emptyMap())
        )

        val result = viewModel.computeMemberAttendancePercentage(sessions, "u1")

        assertEquals(33.33f, result, 0.01f)
    }

    @Test
    fun selectSessionMapsEveryProjectMemberWithMissingRecordsAsUnmarked() {
        viewModel.setProjectMembers(
            members = listOf(
                ProjectMember(userId = "u1", role = Constants.ROLE_TEAM_LEAD),
                ProjectMember(userId = "u2", role = Constants.ROLE_OTHER)
            ),
            names = mapOf("u1" to "Alice", "u2" to "Bob"),
            currentRole = Constants.ROLE_TEAM_LEAD
        )

        viewModel.selectSession(
            AttendanceSession(id = "s1", records = mapOf("u1" to true))
        )

        val state = viewModel.uiState.value as AttendanceUiState.SessionSelected
        assertEquals(true, state.memberAttendance["u1"])
        assertEquals(null, state.memberAttendance["u2"])
        assertEquals(mapOf("u1" to "Alice", "u2" to "Bob"), state.memberNames)
    }

    @Test
    fun computeReminderDelayReturnsNextSessionMinusCurrentTime() {
        assertEquals(1_500L, viewModel.computeReminderDelayMs(now = 1_000L, nextSessionDate = 2_500L))
        assertEquals(-500L, viewModel.computeReminderDelayMs(now = 2_000L, nextSessionDate = 1_500L))
    }

    private class FakeAttendanceRepository(
        private val sessions: LiveData<List<AttendanceSession>>
    ) : AttendanceRepository {
        override fun observeSessions(projectId: String): LiveData<List<AttendanceSession>> = sessions

        override suspend fun createSession(projectId: String, date: Long, nextSessionDate: Long): Result<String> {
            return Result.success("s1")
        }

        override suspend fun markAttendance(
            projectId: String,
            sessionId: String,
            userId: String,
            present: Boolean
        ): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun setNextSessionDate(projectId: String, sessionId: String, nextDate: Long): Result<Unit> {
            return Result.success(Unit)
        }
    }
}
