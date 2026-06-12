package edu.bluejack252.hwixel.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import edu.bluejack252.hwixel.data.mapper.toDomain
import edu.bluejack252.hwixel.data.mapper.toEntity
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.HistoryEntry
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.source.local.TaskDao
import edu.bluejack252.hwixel.data.source.remote.NotificationFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.ProjectRemoteSource
import edu.bluejack252.hwixel.data.source.remote.TaskFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.TaskRemoteSource
import edu.bluejack252.hwixel.data.source.remote.UserRemoteSource
import edu.bluejack252.hwixel.util.BadgeEngine
import edu.bluejack252.hwixel.util.DeadlineScheduler
import edu.bluejack252.hwixel.util.ScoreCalculator
import edu.bluejack252.hwixel.util.constants.Constants

interface TaskRepository {
    fun observeAllTasks(): LiveData<List<Task>>
    fun observeTasksForProjects(projectIds: Set<String>): LiveData<List<Task>>
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
    suspend fun addComment(
        projectId: String,
        taskId: String,
        comment: Comment,
        mentionedUserIds: List<String> = emptyList()
    ): Result<Unit>
    suspend fun updateSubtask(
        projectId: String,
        taskId: String,
        subtaskId: String,
        isDone: Boolean
    ): Result<Unit>
    suspend fun deleteTask(task: Task): Result<Unit>
    suspend fun addActivity(
        projectId: String,
        taskId: String,
        actorId: String,
        action: String
    ): Result<Unit> = Result.success(Unit)
}

class TaskRepositoryImpl(
    private val firebaseSource: TaskRemoteSource,
    private val localDao: TaskDao,
    private val projectSource: ProjectRemoteSource? = null,
    private val notifSource: NotificationFirebaseSource? = null,
    private val userSource: UserRemoteSource? = null,
    private val appContext: Context? = null
) : TaskRepository {

    override fun observeAllTasks(): LiveData<List<Task>> {
        return firebaseSource.observeAllTasks()
    }

    override fun observeTasksForProjects(projectIds: Set<String>): LiveData<List<Task>> {
        if (firebaseSource is TaskFirebaseSource) {
            return firebaseSource.observeTasksForProjects(projectIds)
        }
        val merged = MediatorLiveData<List<Task>>()
        val tasksByProject = mutableMapOf<String, List<Task>>()
        if (projectIds.isEmpty()) {
            merged.value = emptyList()
            return merged
        }
        projectIds.forEach { pid ->
            merged.addSource(firebaseSource.observeTasks(pid)) { tasks ->
                tasksByProject[pid] = tasks
                merged.value = tasksByProject.values.flatten()
            }
        }
        return merged
    }

    override fun observeTasks(projectId: String): LiveData<List<Task>> {
        return firebaseSource.observeTasks(projectId)
    }

    override fun observeTask(projectId: String, taskId: String): LiveData<Task?> {
        return firebaseSource.observeTask(projectId, taskId)
    }

    override suspend fun createTask(task: Task): Result<Unit> = runCatching {
        val created = firebaseSource.createTask(task)
        localDao.upsert(created.toEntity())
        scheduleDeadline(created)
        notifyAssignedUsers(created, created.assignees)
        recomputeCompletionPercentage(created.projectId)
    }

    override suspend fun updateTask(task: Task, actorId: String): Result<Unit> = runCatching {
        val previousTask = localDao.getById(task.id)?.toDomain()
        val previousAssignees = previousTask?.assignees.orEmpty()
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
        scheduleDeadline(task)
        notifyAssignedUsers(task, task.assignees.filterNot { it in previousAssignees })
        if (previousTask != null && previousTask.deadline != task.deadline) {
            notifyDeadlineChanged(task)
        }
        recomputeCompletionPercentage(task.projectId)
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

        val allTasks = recomputeCompletionPercentage(projectId)
        if (newStatus == Constants.STATUS_DONE && projectSource != null) {
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
        comment: Comment,
        mentionedUserIds: List<String>
    ): Result<Unit> = runCatching {
        firebaseSource.addComment(projectId, taskId, comment)
        notifSource?.let { src ->
            val ref = "$projectId|$taskId"
            val uidMentions = UID_MENTION_REGEX.findAll(comment.content)
                .map { it.groupValues[1] }
                .toList()
            (mentionedUserIds + uidMentions)
                .distinct()
                .filter { it.isNotBlank() && it != comment.authorId }
                .forEach { mentionedUid ->
                    runCatching {
                        src.writeNotification(
                            mentionedUid,
                            TYPE_MENTION,
                            "You were mentioned in a comment",
                            ref
                        )
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
        appContext?.let { DeadlineScheduler.cancel(it, task.id) }
        recomputeCompletionPercentage(task.projectId)
    }

    override suspend fun addActivity(
        projectId: String,
        taskId: String,
        actorId: String,
        action: String
    ): Result<Unit> = runCatching {
        if (actorId.isBlank() || action.isBlank()) return@runCatching
        firebaseSource.addHistoryEntry(
            projectId,
            taskId,
            HistoryEntry(
                actorId = actorId,
                action = action,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun scheduleDeadline(task: Task) {
        val context = appContext ?: return
        if (task.deadline > 0L && task.id.isNotBlank()) {
            DeadlineScheduler.schedule(context, task.id, task.title, task.deadline)
        } else if (task.id.isNotBlank()) {
            DeadlineScheduler.cancel(context, task.id)
        }
    }

    private suspend fun notifyAssignedUsers(task: Task, assigneeIds: List<String>) {
        val source = notifSource ?: return
        val referenceId = "${task.projectId}|${task.id}"
        assigneeIds.distinct().filter { it.isNotBlank() }.forEach { userId ->
            runCatching {
                source.writeNotification(
                    userId,
                    TYPE_TASK_ASSIGNED,
                    "You were assigned to ${task.title.ifBlank { "a task" }}",
                    referenceId
                )
            }
        }
    }

    private suspend fun notifyDeadlineChanged(task: Task) {
        val source = notifSource ?: return
        val referenceId = "${task.projectId}|${task.id}"
        task.assignees.distinct().filter { it.isNotBlank() }.forEach { userId ->
            runCatching {
                source.writeNotification(
                    userId,
                    TYPE_DEADLINE,
                    "Deadline changed for ${task.title.ifBlank { "a task" }}",
                    referenceId
                )
            }
        }
    }

    private suspend fun recomputeCompletionPercentage(projectId: String): List<Task> {
        val allTasks = firebaseSource.fetchTasksOnce(projectId)
        if (projectSource != null) {
            val total = allTasks.size
            val done = allTasks.count { it.status == Constants.STATUS_DONE }
            val completionPct = if (total > 0) (done.toFloat() / total) * 100f else 0f
            projectSource.updateCompletionPercentage(projectId, completionPct)
        }
        return allTasks
    }

    private companion object {
        const val TYPE_TASK_ASSIGNED = "task_assigned"
        const val TYPE_MENTION = "mention"
        const val TYPE_DEADLINE = "deadline"

        // Backward-compatible UID mention support. User-facing mentions are resolved by display name in TaskDetailViewModel.
        val UID_MENTION_REGEX = Regex("@([A-Za-z0-9_-]{20,})")
    }
}
