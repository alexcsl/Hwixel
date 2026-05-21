package edu.bluejack252.hwixel.data.model

data class User(
    val id: String = "",
    val name: String = "",
    val studentId: String = "",
    val email: String = "",
    val phone: String = "",
    val avatarUrl: String = "",
    val totalProjectsCompleted: Int = 0,
    val averagePeerRating: Float = 0f,
    val badges: List<String> = emptyList()
)

data class Project(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val goals: String = "",
    val dueDate: Long = 0L,
    val createdBy: String = "",
    val completionPercentage: Float = 0f,
    val members: Map<String, ProjectMember> = emptyMap()
)

data class ProjectMember(
    val userId: String = "",
    val role: String = "",
    val status: String = "",
    val contributionScore: Float = 0f
)

data class TeamHealthResult(
    val status: String = "",
    val summary: String = "",
    val recommendations: List<String> = emptyList()
)

data class Task(
    val id: String = "",
    val projectId: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "",
    val priority: String = "",
    val deadline: Long = 0L,
    val assignees: List<String> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val comments: Map<String, Comment> = emptyMap(),
    val subtasks: Map<String, Subtask> = emptyMap(),
    val history: Map<String, HistoryEntry> = emptyMap()
)

data class Subtask(
    val id: String = "",
    val title: String = "",
    val isDone: Boolean = false
)

data class Comment(
    val id: String = "",
    val authorId: String = "",
    val content: String = "",
    val timestamp: Long = 0L
)

data class Attachment(
    val id: String = "",
    val label: String = "",
    val url: String = "",
    val type: String = ""
)

data class HistoryEntry(
    val id: String = "",
    val actorId: String = "",
    val action: String = "",
    val timestamp: Long = 0L
)

data class AttendanceSession(
    val id: String = "",
    val projectId: String = "",
    val date: Long = 0L,
    val nextSessionDate: Long = 0L,
    val records: Map<String, Boolean> = emptyMap()
)

data class EvaluationSubmission(
    val id: String = "",
    val projectId: String = "",
    val periodId: String = "",
    val evaluatorId: String = "",
    val evaluateeId: String = "",
    val communication: Int = 0,
    val quality: Int = 0,
    val reliability: Int = 0,
    val effort: Int = 0,
    val feedback: String = ""
)

data class FileLink(
    val id: String = "",
    val projectId: String = "",
    val label: String = "",
    val url: String = "",
    val type: String = "",
    val versionNotes: String = "",
    val createdAt: Long = 0L
)

data class Notification(
    val id: String = "",
    val type: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val referenceId: String = ""
)
