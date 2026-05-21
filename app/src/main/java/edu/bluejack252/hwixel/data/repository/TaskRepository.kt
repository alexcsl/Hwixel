package edu.bluejack252.hwixel.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import edu.bluejack252.hwixel.data.mapper.toDomain
import edu.bluejack252.hwixel.data.mapper.toEntity
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.HistoryEntry
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.source.local.TaskDao
import edu.bluejack252.hwixel.data.source.remote.NotificationFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.ProjectRemoteSource
import edu.bluejack252.hwixel.data.source.remote.TaskRemoteSource
import edu.bluejack252.hwixel.util.DeadlineScheduler
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
    private val appContext: Context? = null
) : TaskRepository {

    override fun observeAllTasks(): LiveData<List<Task>> {
        return firebaseSource.observeAllTasks()
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
        val previousAssignees = localDao.getById(task.id)?.toDomain()?.assignees.orEmpty()
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
        recomputeCompletionPercentage(task.projectId)
    }

    override suspend fun updateTaskStatus(
        projectId: String,
        taskId: String,
        newStatus: String,
        actorId: String
    ): Result<Unit> = runCatching {
        firebaseSource.updateTaskStatus(projectId, taskId, newStatus)

        val historyEntry = HistoryEntry(
            actorId = actorId,
            action = "changed status to $newStatus",
            timestamp = System.currentTimeMillis()
        )
        firebaseSource.addHistoryEntry(projectId, taskId, historyEntry)

        val cached = localDao.getById(taskId)
        if (cached != null) localDao.upsert(cached.copy(status = newStatus))

        val allTasks = recomputeCompletionPercentage(projectId)
        if (newStatus == Constants.STATUS_DONE && projectSource != null) {
            val actorTasks = allTasks.filter { it.assignees.contains(actorId) }
            val actorCompleted = actorTasks.count { it.status == Constants.STATUS_DONE }
            val actorHighPri = actorTasks.count {
                it.status == Constants.STATUS_DONE && it.priority == Constants.PRIORITY_HIGH
            }
            val score = ScoreCalculator.calculate(actorCompleted, actorTasks.size, actorHighPri)
            projectSource.updateMemberScore(projectId, actorId, score)
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
            // Firebase Auth UIDs are normally 28 chars; keep the regex narrow to avoid tagging normal @words.
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
        appContext?.let { DeadlineScheduler.cancel(it, task.id) }
        recomputeCompletionPercentage(task.projectId)
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
    }
}
