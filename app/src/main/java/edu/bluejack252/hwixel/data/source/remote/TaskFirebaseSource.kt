package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.Task

open class TaskFirebaseSource(
    database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : TaskRemoteSource {
    private val tasksRef = database.reference.child("tasks")

    override fun observeTasks(projectId: String): LiveData<List<Task>> {
        val liveData = MutableLiveData<List<Task>>()
        tasksRef.child(projectId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.children.mapNotNull { child ->
                    child.getValue(Task::class.java)?.copy(
                        id = child.key.orEmpty(),
                        projectId = projectId
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = emptyList()
            }
        })
        return liveData
    }

    override suspend fun createTask(task: Task) {
        val projectTasksRef = tasksRef.child(task.projectId)
        val key = task.id.ifBlank { projectTasksRef.push().key.orEmpty() }
        projectTasksRef.child(key).setValue(task.copy(id = key)).awaitResult()
    }

    override suspend fun updateTask(task: Task) {
        tasksRef.child(task.projectId).child(task.id).setValue(task).awaitResult()
    }
}
