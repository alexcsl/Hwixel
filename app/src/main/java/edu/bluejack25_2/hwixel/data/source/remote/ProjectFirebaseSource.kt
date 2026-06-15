package edu.bluejack25_2.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack25_2.hwixel.data.model.Project
import edu.bluejack25_2.hwixel.data.model.ProjectMember
import edu.bluejack25_2.hwixel.util.constants.Constants
import edu.bluejack25_2.hwixel.util.constants.NotificationTypes
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

open class ProjectFirebaseSource(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : ProjectRemoteSource {
    private val projectsRef = database.reference.child("projects")
    private val userProjectsRef = database.reference.child("userProjects")
    private val notificationsRef = database.reference.child("notifications")

    override fun observeProjects(): LiveData<List<Project>> {
        val liveData = MutableLiveData<List<Project>>()
        projectsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.children.mapNotNull { child ->
                    child.toProjectOrNull()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = emptyList()
            }
        })
        return liveData
    }

    override fun observeProjectsForUser(userId: String): LiveData<List<Project>> {
        val liveData = MutableLiveData<List<Project>>()
        if (userId.isBlank()) {
            liveData.value = emptyList()
            return liveData
        }
        val observedProjectIds = linkedSetOf<String>()
        val projectsById = linkedMapOf<String, Project>()

        fun publish() {
            liveData.value = projectsById.values.sortedBy { project -> project.name.lowercase() }
        }

        fun observeProjectId(projectId: String) {
            if (projectId.isBlank() || !observedProjectIds.add(projectId)) return
            projectsRef.child(projectId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val project = snapshot.toProjectOrNull()
                        ?.takeIf { it.members.containsKey(userId) }
                    if (project == null) {
                        projectsById.remove(projectId)
                    } else {
                        projectsById[project.id] = project
                    }
                    publish()
                }

                override fun onCancelled(error: DatabaseError) {
                    publish()
                }
            })
        }

        userProjectsRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val projectIds = snapshot.children.mapNotNull { child ->
                    child.key?.takeIf { it.isNotBlank() && child.getValue(Boolean::class.java) != false }
                }
                if (projectIds.isEmpty()) {
                    loadProjectsByMembership(userId, liveData)
                    return
                }
                projectIds.forEach(::observeProjectId)
            }

            override fun onCancelled(error: DatabaseError) {
                loadProjectsByMembership(userId, liveData)
            }
        })

        notificationsRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.mapNotNull { child ->
                    val type = child.child("type").getValue(String::class.java)
                    val referenceId = child.child("referenceId").getValue(String::class.java).orEmpty()
                    referenceId.takeIf { type == NotificationTypes.TYPE_INVITE && it.isNotBlank() }
                }.forEach(::observeProjectId)
            }

            override fun onCancelled(error: DatabaseError) {
                publish()
            }
        })
        loadProjectIdsByMembership(userId, ::observeProjectId)
        return liveData
    }

    private fun loadProjectsByMembership(userId: String, liveData: MutableLiveData<List<Project>>) {
        projectsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val projects = snapshot.children.mapNotNull { child ->
                    child.toProjectOrNull()
                        ?.takeIf { project -> project.members.containsKey(userId) }
                }.sortedBy { project -> project.name.lowercase() }
                liveData.value = projects
                val missingIndexUpdates = projects.associate { project ->
                    "userProjects/$userId/${project.id}" to true
                }
                if (missingIndexUpdates.isNotEmpty()) {
                    database.reference.updateChildren(missingIndexUpdates)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = emptyList()
            }
        })
    }

    private fun loadProjectIdsByMembership(userId: String, onProjectId: (String) -> Unit) {
        projectsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.mapNotNull { child ->
                    child.toProjectOrNull()
                        ?.takeIf { project -> project.members.containsKey(userId) }
                        ?.id
                }.forEach(onProjectId)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    override fun observeProject(projectId: String): LiveData<Project?> {
        val liveData = MutableLiveData<Project?>()
        projectsRef.child(projectId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveData.value = snapshot.toProjectOrNull()
            }

            override fun onCancelled(error: DatabaseError) {
                liveData.value = null
            }
        })
        return liveData
    }

    override suspend fun fetchProjectOnce(projectId: String): Project? =
        suspendCoroutine { continuation ->
            projectsRef.child(projectId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    continuation.resume(snapshot.toProjectOrNull())
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resumeWithException(error.toException())
                }
            })
        }

    override suspend fun createProject(project: Project): Project {
        val key = project.id.ifBlank { projectsRef.push().key.orEmpty() }
        require(key.isNotBlank()) { "Failed to allocate project id." }
        val projectWithId = project.copy(id = key)
        projectsRef.child(key).setValue(projectWithId).awaitResult()
        writeIndexBestEffort(projectWithId.members.keys.associate { userId ->
            "userProjects/$userId/$key" to true
        })
        return projectWithId
    }

    override suspend fun updateProject(project: Project) {
        projectsRef.child(project.id).setValue(project).awaitResult()
    }

    override suspend fun updateCompletionPercentage(projectId: String, percentage: Float) {
        val previousProject = fetchProjectOnce(projectId)
        projectsRef.child(projectId).child("completionPercentage").setValue(percentage).awaitResult()
        val previousPercentage = previousProject?.completionPercentage ?: 0f
        when {
            previousPercentage < 100f && percentage >= 100f -> adjustCompletedProjects(previousProject, 1)
            previousPercentage >= 100f && percentage < 100f -> adjustCompletedProjects(previousProject, -1)
        }
    }

    override suspend fun addMember(projectId: String, userId: String, member: ProjectMember) {
        projectsRef.child(projectId).child("members").child(userId).setValue(member).awaitResult()
        writeIndexBestEffort(mapOf("userProjects/$userId/$projectId" to true))
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

    private suspend fun writeIndexBestEffort(indexUpdates: Map<String, Any>) {
        if (indexUpdates.isEmpty()) return
        runCatching {
            database.reference.updateChildren(indexUpdates).awaitResult()
        }
    }

    private suspend fun adjustCompletedProjects(project: Project?, delta: Int) {
        project?.members.orEmpty()
            .filterValues { member -> member.status != Constants.MEMBER_STATUS_INACTIVE }
            .keys
            .forEach { userId ->
                runCatching {
                    val completedRef = database.reference
                        .child("users")
                        .child(userId)
                        .child("totalProjectsCompleted")
                    val current = completedRef.readInt()
                    completedRef.setValue((current + delta).coerceAtLeast(0)).awaitResult()
                }
            }
    }

    private suspend fun com.google.firebase.database.DatabaseReference.readInt(): Int =
        suspendCoroutine { continuation ->
            addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    continuation.resume((snapshot.value as? Number)?.toInt() ?: 0)
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resumeWithException(error.toException())
                }
            })
        }

    private fun DataSnapshot.toProject(): Project? {
        val projectId = key.orEmpty()
        if (value !is Map<*, *>) return null
        return Project(
            id = projectId,
            name = child("name").safeString(),
            description = child("description").safeString(),
            goals = child("goals").safeString(),
            dueDate = child("dueDate").safeLong(),
            createdBy = child("createdBy").safeString(),
            completionPercentage = child("completionPercentage").safeFloat(),
            members = child("members").children.mapNotNull { memberSnapshot ->
                val memberId = memberSnapshot.key.orEmpty()
                if (memberId.isBlank()) return@mapNotNull null
                memberId to ProjectMember(
                    userId = memberSnapshot.child("userId").safeString().takeIf { it.isNotBlank() } ?: memberId,
                    role = memberSnapshot.child("role").safeString(),
                    status = memberSnapshot.child("status").safeString(),
                    contributionScore = memberSnapshot.child("contributionScore").safeFloat()
                )
            }.toMap()
        )
    }

    private fun DataSnapshot.toProjectOrNull(): Project? {
        return try {
            toProject()
        } catch (exception: RuntimeException) {
            null
        }
    }

    private fun DataSnapshot.safeString(): String {
        return when (val raw = value) {
            is String -> raw
            else -> ""
        }
    }

    private fun DataSnapshot.safeLong(): Long {
        return when (val raw = value) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun DataSnapshot.safeFloat(): Float {
        return when (val raw = value) {
            is Number -> raw.toFloat()
            is String -> raw.toFloatOrNull() ?: 0f
            else -> 0f
        }
    }

}
