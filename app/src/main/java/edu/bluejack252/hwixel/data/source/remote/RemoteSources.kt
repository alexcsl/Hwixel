package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User

interface AuthRemoteSource {
    val currentUserId: String?
    suspend fun login(email: String, password: String)
    suspend fun register(email: String, password: String): String
    fun logout()
}

interface ProjectRemoteSource {
    fun observeProjects(): LiveData<List<Project>>
    suspend fun createProject(project: Project)
    suspend fun updateProject(project: Project)
}

interface TaskRemoteSource {
    fun observeTasks(projectId: String): LiveData<List<Task>>
    suspend fun createTask(task: Task)
    suspend fun updateTask(task: Task)
}

interface UserRemoteSource {
    fun observeUsers(): LiveData<List<User>>
    suspend fun upsertUser(user: User)
}
