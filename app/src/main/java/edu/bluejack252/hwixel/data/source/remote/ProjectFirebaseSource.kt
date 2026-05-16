package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember

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

    override fun observeProject(projectId: String): LiveData<Project?> {
        val liveData = MutableLiveData<Project?>()
        projectsRef.child(projectId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.getValue(Project::class.java)?.copy(id = snapshot.key.orEmpty())
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = null
            }
        })
        return liveData
    }

    override suspend fun createProject(project: Project): Project {
        val key = project.id.ifBlank { projectsRef.push().key.orEmpty() }
        require(key.isNotBlank()) { "Failed to allocate project id." }
        val projectWithId = project.copy(id = key)
        projectsRef.child(key).setValue(projectWithId).awaitResult()
        return projectWithId
    }

    override suspend fun updateProject(project: Project) {
        projectsRef.child(project.id).setValue(project).awaitResult()
    }

    override suspend fun updateCompletionPercentage(projectId: String, percentage: Float) {
        projectsRef.child(projectId).child("completionPercentage").setValue(percentage).awaitResult()
    }

    override suspend fun addMember(projectId: String, userId: String, member: ProjectMember) {
        projectsRef.child(projectId).child("members").child(userId).setValue(member).awaitResult()
    }

    override suspend fun updateMember(projectId: String, userId: String, member: ProjectMember) {
        projectsRef.child(projectId).child("members").child(userId).setValue(member).awaitResult()
    }

    override suspend fun updateMemberScore(projectId: String, userId: String, score: Float) {
        projectsRef.child(projectId).child("members").child(userId)
            .child("contributionScore")
            .setValue(score)
            .awaitResult()
    }
}
