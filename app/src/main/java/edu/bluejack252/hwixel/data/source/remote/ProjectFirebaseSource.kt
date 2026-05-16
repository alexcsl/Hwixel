package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.Project

open class ProjectFirebaseSource(
    database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : ProjectRemoteSource {
    private val projectsRef = database.reference.child("projects")

    override fun observeProjects(): LiveData<List<Project>> {
        val liveData = MutableLiveData<List<Project>>()
        projectsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.children.mapNotNull { child ->
                    child.getValue(Project::class.java)?.copy(id = child.key.orEmpty())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = emptyList()
            }
        })
        return liveData
    }

    override suspend fun createProject(project: Project) {
        val key = project.id.ifBlank { projectsRef.push().key.orEmpty() }
        projectsRef.child(key).setValue(project.copy(id = key)).awaitResult()
    }

    override suspend fun updateProject(project: Project) {
        projectsRef.child(project.id).setValue(project).awaitResult()
    }
}
