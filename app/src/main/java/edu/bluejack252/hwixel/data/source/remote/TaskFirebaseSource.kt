package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.HistoryEntry
import edu.bluejack252.hwixel.data.model.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

open class TaskFirebaseSource(
    database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : TaskRemoteSource {
    private val tasksRef = database.reference.child("tasks")

    override fun observeAllTasks(): LiveData<List<Task>> {
        val liveData = MutableLiveData<List<Task>>()
        tasksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.children.flatMap { projectSnapshot ->
                    val projectId = projectSnapshot.key.orEmpty()
                    projectSnapshot.children.mapNotNull { taskSnapshot ->
                        taskSnapshot.toTaskOrNull(projectId)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = emptyList()
            }
        })
        return liveData
    }

    override fun observeTasks(projectId: String): LiveData<List<Task>> {
        val liveData = MutableLiveData<List<Task>>()
        tasksRef.child(projectId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.children.mapNotNull { child ->
                    child.toTaskOrNull(projectId)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = emptyList()
            }
        })
        return liveData
    }

    override fun observeTask(projectId: String, taskId: String): LiveData<Task?> {
        val liveData = MutableLiveData<Task?>()
        tasksRef.child(projectId).child(taskId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.toTaskOrNull(projectId)
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = null
            }
        })
        return liveData
    }

    override suspend fun fetchTasksOnce(projectId: String): List<Task> =
        suspendCoroutine { continuation ->
            tasksRef.child(projectId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val tasks = snapshot.children.mapNotNull { child ->
                        child.toTaskOrNull(projectId)
                    }
                    continuation.resume(tasks)
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resumeWithException(error.toException())
                }
            })
        }

    override suspend fun createTask(task: Task) {
        val projectTasksRef = tasksRef.child(task.projectId)
        val key = task.id.ifBlank { projectTasksRef.push().key.orEmpty() }
        projectTasksRef.child(key).setValue(task.copy(id = key)).awaitResult()
    }

    override suspend fun updateTask(task: Task) {
        tasksRef.child(task.projectId).child(task.id).setValue(task).awaitResult()
    }

    override suspend fun updateTaskStatus(projectId: String, taskId: String, status: String) {
        tasksRef.child(projectId).child(taskId).child("status").setValue(status).awaitResult()
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
        return try {
            getValue(Task::class.java)?.copy(
                id = key.orEmpty(),
                projectId = projectId
            )
        } catch (exception: DatabaseException) {
            null
        }
    }
}
