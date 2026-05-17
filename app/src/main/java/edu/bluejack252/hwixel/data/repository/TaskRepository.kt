package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import edu.bluejack252.hwixel.data.mapper.toDomain
import edu.bluejack252.hwixel.data.mapper.toEntity
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.HistoryEntry
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.source.local.TaskDao
import edu.bluejack252.hwixel.data.source.remote.NotificationFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.ProjectRemoteSource
import edu.bluejack252.hwixel.data.source.remote.TaskRemoteSource
import edu.bluejack252.hwixel.data.source.remote.UserRemoteSource
import edu.bluejack252.hwixel.util.BadgeEngine
import edu.bluejack252.hwixel.util.ScoreCalculator
import edu.bluejack252.hwixel.util.constants.Constants

interface TaskRepository {
    fun observeAllTasks(): LiveData<List<Task>>
    fun observeTasks(projectId: String): LiveData<List<Task>>
    fun observeTask(projectId: String, taskId: String): LiveData<Task?>
    suspend fun createTask(task: Task): Result<Unit>
    suspend fun updateTask(task: Task, actorId: String = ""): Result<Unit>
    suspend fun updateTaskStatus(
        projectId: String,
        taskId: String,
        newStatus: String,
        actorId: String
    ): Result<Unit>
    suspend fun addComment(projectId: String, taskId: String, comment: Comment): Result<Unit>
    suspend fun updateSubtask(
        projectId: String,
        taskId: String,
        subtaskId: String,
        isDone: Boolean
    ): Result<Unit>
    suspend fun deleteTask(task: Task): Result<Unit>
}

class TaskRepositoryImpl(
    private val firebaseSource: TaskRemoteSource,
    private val localDao: TaskDao,
    private val projectSource: ProjectRemoteSource? = null,
    private val notifSource: NotificationFirebaseSource? = null,
    private val userSource: UserRemoteSource? = null
) : TaskRepository {

    override fun observeAllTasks(): LiveData<List<Task>> {
        return localDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeTasks(projectId: String): LiveData<List<Task>> {
        return localDao.observeByProject(projectId).map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeTask(projectId: String, taskId: String): LiveData<Task?> {
        return firebaseSource.observeTask(projectId, taskId)
    }

    override suspend fun createTask(task: Task): Result<Unit> = runCatching {
        firebaseSource.createTask(task)
        localDao.upsert(task.toEntity())
        notifSource?.let { src ->
            val ref = "${task.projectId}|${task.id}"
            task.assignees.forEach { uid ->
                runCatching { src.writeNotification(uid, "task_assigned", "You were assigned to \"${task.title}\"", ref) }
            }
        }
    }

    override suspend fun updateTask(task: Task, actorId: String): Result<Unit> = runCatching {
        firebaseSource.updateTask(task)
        if (actorId.isNotBlank()) {
            firebaseSource.addHistoryEntry(
                task.projectId,
                task.id,
                HistoryEntry(
                    actorId = actorId,
                    action = "updated task",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        localDao.upsert(task.toEntity())
    }

    override suspend fun updateTaskStatus(
        projectId: String,
        taskId: String,
        newStatus: String,
        actorId: String
    ): Result<Unit> = runCatching {
        val completionTimestamp = if (newStatus == Constants.STATUS_DONE) System.currentTimeMillis() else 0L
        firebaseSource.updateTaskStatus(projectId, taskId, newStatus, completionTimestamp)

        val historyEntry = HistoryEntry(
            actorId = actorId,
            action = "changed status to $newStatus",
            timestamp = System.currentTimeMillis()
        )
        firebaseSource.addHistoryEntry(projectId, taskId, historyEntry)

        val cached = localDao.getById(taskId)
        if (cached != null) {
            localDao.upsert(cached.copy(status = newStatus, completedAt = completionTimestamp))
        }

        if (newStatus == Constants.STATUS_DONE && projectSource != null) {
            val allTasks = firebaseSource.fetchTasksOnce(projectId).map { task ->
                if (task.id == taskId) task.copy(status = newStatus, completedAt = completionTimestamp) else task
            }
            val total = allTasks.size
            val done = allTasks.count { it.status == Constants.STATUS_DONE }
            val completionPct = if (total > 0) (done.toFloat() / total) * 100f else 0f
            projectSource.updateCompletionPercentage(projectId, completionPct)

            val actorTasks = allTasks.filter { it.assignees.contains(actorId) }
            val actorCompleted = actorTasks.count { it.status == Constants.STATUS_DONE }
            val actorHighPri = actorTasks.count {
                it.status == Constants.STATUS_DONE && it.priority == Constants.PRIORITY_HIGH
            }
            val score = ScoreCalculator.calculate(actorCompleted, actorTasks.size, actorHighPri)
            projectSource.updateMemberScore(projectId, actorId, score)
            awardBadges(projectId, actorId, score, allTasks)
        }
    }

    private suspend fun awardBadges(
        projectId: String,
        actorId: String,
        actorScore: Float,
        allTasks: List<Task>
    ) {
        if (actorId.isBlank() || userSource == null || projectSource == null) return
        val project = projectSource.fetchProjectOnce(projectId) ?: return
        val updatedMembers = project.members.toMutableMap()
        val currentMember = updatedMembers[actorId]
        if (currentMember != null) {
            updatedMembers[actorId] = currentMember.copy(contributionScore = actorScore)
        }

        val user = userSource.fetchUser(actorId) ?: return
        var badges = user.badges
        if (BadgeEngine.shouldAwardTopContributor(actorId, updatedMembers)) {
            badges = BadgeEngine.withBadge(badges, BadgeEngine.BADGE_TOP_CONTRIBUTOR)
        }
        if (BadgeEngine.shouldAwardDeadlineCrusher(actorId, allTasks)) {
            badges = BadgeEngine.withBadge(badges, BadgeEngine.BADGE_DEADLINE_CRUSHER)
        }
        if (badges != user.badges) {
            userSource.updateBadges(actorId, badges)
        }
    }

    override suspend fun addComment(
        projectId: String,
        taskId: String,
        comment: Comment
    ): Result<Unit> = runCatching {
        firebaseSource.addComment(projectId, taskId, comment)
        notifSource?.let { src ->
            val ref = "$projectId|$taskId"
            val mentionRegex = Regex("@([A-Za-z0-9_-]{20,})")
            mentionRegex.findAll(comment.content).forEach { match ->
                val mentionedUid = match.groupValues[1]
                if (mentionedUid != comment.authorId) {
                    runCatching { src.writeNotification(mentionedUid, "mention", "You were mentioned in a comment", ref) }
                }
            }
        }
    }

    override suspend fun updateSubtask(
        projectId: String,
        taskId: String,
        subtaskId: String,
        isDone: Boolean
    ): Result<Unit> = runCatching {
        firebaseSource.updateSubtask(projectId, taskId, subtaskId, isDone)
    }

    override suspend fun deleteTask(task: Task): Result<Unit> = runCatching {
        firebaseSource.deleteTask(task.projectId, task.id)
        localDao.delete(task.toEntity())
    }
}
