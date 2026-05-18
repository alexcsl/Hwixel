package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import edu.bluejack252.hwixel.data.mapper.toEntity
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.HistoryEntry
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.source.local.TaskDao
import edu.bluejack252.hwixel.data.source.remote.ProjectRemoteSource
import edu.bluejack252.hwixel.data.source.remote.TaskRemoteSource
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
    private val projectSource: ProjectRemoteSource? = null
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
        firebaseSource.createTask(task)
        localDao.upsert(task.toEntity())
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
        firebaseSource.updateTaskStatus(projectId, taskId, newStatus)

        val historyEntry = HistoryEntry(
            actorId = actorId,
            action = "changed status to $newStatus",
            timestamp = System.currentTimeMillis()
        )
        firebaseSource.addHistoryEntry(projectId, taskId, historyEntry)

        val cached = localDao.getById(taskId)
        if (cached != null) localDao.upsert(cached.copy(status = newStatus))

        if (newStatus == Constants.STATUS_DONE && projectSource != null) {
            val allTasks = firebaseSource.fetchTasksOnce(projectId)
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
        }
    }

    override suspend fun addComment(
        projectId: String,
        taskId: String,
        comment: Comment
    ): Result<Unit> = runCatching {
        firebaseSource.addComment(projectId, taskId, comment)
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
