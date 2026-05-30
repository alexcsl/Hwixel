package edu.bluejack252.hwixel.ui.project.attendance

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import edu.bluejack252.hwixel.data.model.AttendanceSession
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.repository.AttendanceRepository
import edu.bluejack252.hwixel.data.work.AttendanceReminderWorker
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableLiveData<AttendanceUiState>(AttendanceUiState.Idle)
    val uiState: LiveData<AttendanceUiState> = _uiState

    private val _sessions = MediatorLiveData<List<AttendanceSession>>()
    val sessions: LiveData<List<AttendanceSession>> = _sessions

    private var selectedSession: AttendanceSession? = null
    private var memberNames: Map<String, String> = emptyMap()
    private var projectMembers: List<ProjectMember> = emptyList()
    private var currentUserRole: String = ""

    private val sessionSource = repository.observeSessions(projectId)

    init {
        _sessions.addSource(sessionSource) { list ->
            _sessions.value = list
            selectedSession?.let { selected ->
                val updated = list.firstOrNull { it.id == selected.id }
                if (updated != null) {
                    selectedSession = updated
                    publishSelectedSession()
                } else {
                    clearSelectedSession()
                }
            }
        }
    }

    fun setProjectMembers(members: List<ProjectMember>, names: Map<String, String>, currentRole: String) {
        projectMembers = members
        memberNames = names
        currentUserRole = currentRole
        selectedSession?.let { publishSelectedSession() }
    }

    fun selectSession(session: AttendanceSession) {
        selectedSession = session
        publishSelectedSession()
    }

    fun toggleSessionSelection(session: AttendanceSession) {
        if (selectedSession?.id == session.id) {
            clearSelectedSession()
        } else {
            selectSession(session)
        }
    }

    private fun clearSelectedSession() {
        selectedSession = null
        _uiState.value = AttendanceUiState.Idle
    }

    private fun publishSelectedSession() {
        val session = selectedSession ?: return
        val attendance = buildMemberAttendanceMap(session)
        _uiState.value = AttendanceUiState.SessionSelected(
            session = session,
            memberAttendance = attendance,
            memberNames = memberNames
        )
    }

    fun selectedSessionId(): String? = selectedSession?.id

    fun createSession(date: Long, nextSessionDate: Long) {
        viewModelScope.launch {
            _uiState.value = AttendanceUiState.Loading
            repository.createSession(projectId, date, nextSessionDate)
                .onSuccess { _uiState.value = AttendanceUiState.SessionCreated }
                .onFailure { _uiState.value = AttendanceUiState.Error(it.message ?: "") }
        }
    }

    fun toggleAttendance(sessionId: String, userId: String, present: Boolean) {
        if (currentUserRole != Constants.ROLE_TEAM_LEAD) {
            _uiState.value = AttendanceUiState.Error("lead_only")
            return
        }
        val previousSession = selectedSession
        selectedSession = selectedSession?.takeIf { it.id == sessionId }?.copy(
            records = selectedSession?.records.orEmpty() + (userId to present)
        )
        publishSelectedSession()
        viewModelScope.launch {
            repository.markAttendance(projectId, sessionId, userId, present)
                .onSuccess { publishSelectedSession() }
                .onFailure {
                    selectedSession = previousSession
                    publishSelectedSession()
                    _uiState.value = AttendanceUiState.Error(it.message ?: "")
                }
        }
    }

    fun scheduleReminder(context: Context, projectName: String, nextSessionDate: Long) {
        val delayMs = computeReminderDelayMs(System.currentTimeMillis(), nextSessionDate)
        if (delayMs <= 0) return

        val dateStr = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(nextSessionDate))

        val data = Data.Builder()
            .putString(AttendanceReminderWorker.KEY_PROJECT_NAME, projectName)
            .putString(AttendanceReminderWorker.KEY_SESSION_DATE, dateStr)
            .build()

        val request = OneTimeWorkRequestBuilder<AttendanceReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("attendance_reminder_$projectId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "attendance_reminder_$projectId",
            ExistingWorkPolicy.REPLACE,
            request
        )
        _uiState.value = AttendanceUiState.ReminderSet
    }

    fun computeReminderDelayMs(now: Long, nextSessionDate: Long): Long {
        return nextSessionDate - now
    }

    fun computeMemberAttendancePercentage(sessions: List<AttendanceSession>, userId: String): Float {
        if (sessions.isEmpty()) return 0f
        val attended = sessions.count { it.records[userId] == true }
        return (attended.toFloat() / sessions.size) * 100f
    }

    fun isCurrentUserLead(userId: String): Boolean =
        projectMembers.any { it.userId == userId && it.role == Constants.ROLE_TEAM_LEAD }

    fun buildExportText(sessions: List<AttendanceSession>, projectName: String): String {
        val sb = StringBuilder()
        sb.appendLine("Attendance Report - $projectName")
        sb.appendLine("Total sessions: ${sessions.size}")
        sb.appendLine()

        memberNames.forEach { (userId, name) ->
            val pct = computeMemberAttendancePercentage(sessions, userId)
            sb.appendLine("$name: ${"%.0f".format(pct)}% (${sessions.count { it.records[userId] == true }}/${sessions.size})")
        }

        sb.appendLine()
        sessions.forEach { session ->
            val dateStr = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(session.date))
            val presentCount = session.records.values.count { it }
            sb.appendLine("$dateStr: $presentCount/${memberNames.size} present")
            session.records.forEach { (uid, present) ->
                val name = memberNames[uid] ?: uid
                sb.appendLine("  - $name: ${if (present) "Present" else "Absent"}")
            }
        }
        return sb.toString()
    }

    private fun buildMemberAttendanceMap(session: AttendanceSession): Map<String, Boolean?> {
        return projectMembers.associate { member ->
            member.userId to session.records[member.userId]
        }
    }

    override fun onCleared() {
        super.onCleared()
        _sessions.removeSource(sessionSource)
    }
}
