package edu.bluejack252.hwixel.ui.project.attendance

import edu.bluejack252.hwixel.data.model.AttendanceSession

sealed class AttendanceUiState {
    object Idle : AttendanceUiState()
    object Loading : AttendanceUiState()
    data class SessionsLoaded(val sessions: List<AttendanceSession>) : AttendanceUiState()
    data class SessionSelected(
        val session: AttendanceSession,
        val memberAttendance: Map<String, Boolean?>,
        val memberNames: Map<String, String>
    ) : AttendanceUiState()
    data class Error(val message: String) : AttendanceUiState()
    object AttendanceSaved : AttendanceUiState()
    object ReminderSet : AttendanceUiState()
    object SessionCreated : AttendanceUiState()
}
