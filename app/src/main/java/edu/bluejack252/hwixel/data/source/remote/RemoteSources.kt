package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.data.model.AttendanceSession
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.FileLink
import edu.bluejack252.hwixel.data.model.HistoryEntry
import edu.bluejack252.hwixel.data.model.Notification
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User

interface AuthRemoteSource {
    val currentUserId: String?
    suspend fun login(email: String, password: String)
    suspend fun register(email: String, password: String): String
    fun logout()
}

interface ProjectRemoteSource {
    fun observeProjects(): LiveData<List<Project>>
    fun observeProjectsForUser(userId: String): LiveData<List<Project>> = observeProjects()
    fun observeProject(projectId: String): LiveData<Project?>
    suspend fun createProject(project: Project): Project
    suspend fun updateProject(project: Project)
    suspend fun updateCompletionPercentage(projectId: String, percentage: Float)
    suspend fun addMember(projectId: String, userId: String, member: ProjectMember)
    suspend fun updateMember(projectId: String, userId: String, member: ProjectMember)
    suspend fun updateMemberScore(projectId: String, userId: String, score: Float)
}

interface TaskRemoteSource {
    fun observeAllTasks(): LiveData<List<Task>>
    fun observeTasks(projectId: String): LiveData<List<Task>>
    fun observeTask(projectId: String, taskId: String): LiveData<Task?>
    suspend fun fetchTasksOnce(projectId: String): List<Task>
    suspend fun createTask(task: Task): Task
    suspend fun updateTask(task: Task)
    suspend fun updateTaskStatus(projectId: String, taskId: String, status: String)
    suspend fun addHistoryEntry(projectId: String, taskId: String, entry: HistoryEntry)
    suspend fun addComment(projectId: String, taskId: String, comment: Comment)
    suspend fun updateSubtask(projectId: String, taskId: String, subtaskId: String, isDone: Boolean)
    suspend fun deleteTask(projectId: String, taskId: String)
}

interface UserRemoteSource {
    fun observeUsers(): LiveData<List<User>>
    fun observeUser(userId: String): LiveData<User?>
    fun observeNotifications(userId: String): LiveData<List<Notification>> = MutableLiveData(emptyList())
    suspend fun upsertUser(user: User)
    suspend fun findByEmail(email: String): User?
    suspend fun writeNotification(userId: String, notifId: String, payload: Map<String, Any>)
    suspend fun markNotificationRead(userId: String, notifId: String) = Unit
}

interface AttendanceRemoteSource {
    fun observeSessions(projectId: String): LiveData<List<AttendanceSession>>
    suspend fun createSession(projectId: String, date: Long, nextSessionDate: Long): Result<String>
    suspend fun markAttendance(projectId: String, sessionId: String, userId: String, present: Boolean): Result<Unit>
    suspend fun setNextSessionDate(projectId: String, sessionId: String, nextDate: Long): Result<Unit>
}

interface FileRemoteSource {
    fun observeFiles(projectId: String): LiveData<List<FileLink>>
    suspend fun addFile(projectId: String, file: FileLink): Result<Unit>
    suspend fun deleteFile(projectId: String, fileId: String): Result<Unit>
}
