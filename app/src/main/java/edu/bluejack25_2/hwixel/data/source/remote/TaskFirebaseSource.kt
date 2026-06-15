package edu.bluejack25_2.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack25_2.hwixel.data.model.Attachment
import edu.bluejack25_2.hwixel.data.model.Comment
import edu.bluejack25_2.hwixel.data.model.HistoryEntry
import edu.bluejack25_2.hwixel.data.model.Subtask
import edu.bluejack25_2.hwixel.data.model.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class TaskFirebaseSource(
    database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : TaskRemoteSource {
    private val tasksRef = database.reference.child("tasks")

    override fun observeAllTasks(): LiveData<List<Task>> =
        FirebaseValueLiveData(tasksRef) { snapshot ->
            snapshot.children.flatMap { projectSnapshot ->
                val projectId = projectSnapshot.key.orEmpty()
                projectSnapshot.children.mapNotNull { taskSnapshot ->
                    taskSnapshot.toTaskOrNull(projectId)
                }
            }
        }

    override fun observeTasks(projectId: String): LiveData<List<Task>> =
        FirebaseValueLiveData(tasksRef.child(projectId)) { snapshot ->
            snapshot.children.mapNotNull { child -> child.toTaskOrNull(projectId) }
        }

    override fun observeTask(projectId: String, taskId: String): LiveData<Task?> =
        FirebaseValueLiveData(tasksRef.child(projectId).child(taskId)) { snapshot ->
            snapshot.toTaskOrNull(projectId)
        }

    fun observeTasksForProjects(projectIds: Set<String>): LiveData<List<Task>> {
        val merged = MediatorLiveData<List<Task>>()
        val tasksByProject = mutableMapOf<String, List<Task>>()
        if (projectIds.isEmpty()) {
            merged.value = emptyList()
            return merged
        }
        projectIds.forEach { pid ->
            merged.addSource(observeTasks(pid)) { tasks ->
                tasksByProject[pid] = tasks
                merged.value = tasksByProject.values.flatten()
            }
        }
        return merged
    }

    override suspend fun fetchTasksOnce(projectId: String): List<Task> =
        suspendCancellableCoroutine { continuation ->
            tasksRef.child(projectId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    continuation.resume(snapshot.children.mapNotNull { it.toTaskOrNull(projectId) })
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resumeWithException(error.toException())
                }
            })
        }

    override suspend fun createTask(task: Task): Task {
        val projectTasksRef = tasksRef.child(task.projectId)
        val key = task.id.ifBlank { projectTasksRef.push().key.orEmpty() }
        val created = task.copy(id = key)
        projectTasksRef.child(key).setValue(created).awaitResult()
        return created
    }

    override suspend fun updateTask(task: Task) {
        tasksRef.child(task.projectId).child(task.id).setValue(task).awaitResult()
    }

    override suspend fun updateTaskStatus(
        projectId: String,
        taskId: String,
        status: String,
        completedAt: Long
    ) {
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "completedAt" to completedAt
        )
        tasksRef.child(projectId).child(taskId).updateChildren(updates).awaitResult()
    }

    override suspend fun addHistoryEntry(projectId: String, taskId: String, entry: HistoryEntry) {
        val historyRef = tasksRef.child(projectId).child(taskId).child("history")
        val key = historyRef.push().key.orEmpty()
        historyRef.child(key).setValue(entry.copy(id = key)).awaitResult()
    }

    override suspend fun addComment(projectId: String, taskId: String, comment: Comment) {
        val commentsRef = tasksRef.child(projectId).child(taskId).child("comments")
        val key = commentsRef.push().key.orEmpty()
        commentsRef.child(key).setValue(comment.copy(id = key)).awaitResult()
    }

    override suspend fun updateSubtask(
        projectId: String,
        taskId: String,
        subtaskId: String,
        isDone: Boolean
    ) {
        tasksRef.child(projectId).child(taskId).child("subtasks")
            .child(subtaskId).child("isDone").setValue(isDone).awaitResult()
    }

    override suspend fun deleteTask(projectId: String, taskId: String) {
        tasksRef.child(projectId).child(taskId).removeValue().awaitResult()
    }

    private fun DataSnapshot.toTaskOrNull(projectId: String): Task? {
        if (value !is Map<*, *>) return null
        val taskId = key.orEmpty()
        return Task(
            id = taskId,
            projectId = projectId,
            title = child("title").safeString(),
            description = child("description").safeString(),
            status = child("status").safeString(),
            priority = child("priority").safeString(),
            deadline = child("deadline").safeLong(),
            assignees = child("assignees").toStringList(),
            attachments = child("attachments").children.mapNotNull { attachmentSnapshot ->
                val attachmentId = attachmentSnapshot.child("id").safeString().takeIf(String::isNotBlank)
                    ?: attachmentSnapshot.key.orEmpty()
                if (attachmentId.isBlank()) return@mapNotNull null
                Attachment(
                    id = attachmentId,
                    label = attachmentSnapshot.child("label").safeString(),
                    url = attachmentSnapshot.child("url").safeString(),
                    type = attachmentSnapshot.child("type").safeString()
                )
            },
            comments = child("comments").children.mapNotNull { commentSnapshot ->
                val commentId = commentSnapshot.child("id").safeString().takeIf(String::isNotBlank)
                    ?: commentSnapshot.key.orEmpty()
                if (commentId.isBlank()) return@mapNotNull null
                commentId to Comment(
                    id = commentId,
                    authorId = commentSnapshot.child("authorId").safeString(),
                    content = commentSnapshot.child("content").safeString(),
                    timestamp = commentSnapshot.child("timestamp").safeLong()
                )
            }.toMap(),
            subtasks = child("subtasks").children.mapNotNull { subtaskSnapshot ->
                val subtaskId = subtaskSnapshot.child("id").safeString().takeIf(String::isNotBlank)
                    ?: subtaskSnapshot.key.orEmpty()
                if (subtaskId.isBlank()) return@mapNotNull null
                subtaskId to Subtask(
                    id = subtaskId,
                    title = subtaskSnapshot.child("title").safeString(),
                    isDone = subtaskSnapshot.child("isDone").safeBoolean()
                )
            }.toMap(),
            history = child("history").children.mapNotNull { historySnapshot ->
                val historyId = historySnapshot.child("id").safeString().takeIf(String::isNotBlank)
                    ?: historySnapshot.key.orEmpty()
                if (historyId.isBlank()) return@mapNotNull null
                historyId to HistoryEntry(
                    id = historyId,
                    actorId = historySnapshot.child("actorId").safeString(),
                    action = historySnapshot.child("action").safeString(),
                    timestamp = historySnapshot.child("timestamp").safeLong()
                )
            }.toMap()
        )
    }

    private fun DataSnapshot.safeString(): String {
        return when (val raw = value) {
            is String -> raw
            else -> ""
        }
    }

    private fun DataSnapshot.toStringList(): List<String> {
        val raw = value
        if (raw is String) return listOf(raw).filter { it.isNotBlank() }
        return children.mapNotNull { child ->
            child.safeString().takeIf(String::isNotBlank)
                ?: child.key?.takeIf { child.safeBoolean() }
        }
    }

    private fun DataSnapshot.safeLong(): Long {
        return when (val raw = value) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun DataSnapshot.safeBoolean(): Boolean {
        return when (val raw = value) {
            is Boolean -> raw
            is String -> raw.equals("true", ignoreCase = true)
            is Number -> raw.toInt() != 0
            else -> false
        }
    }
}
