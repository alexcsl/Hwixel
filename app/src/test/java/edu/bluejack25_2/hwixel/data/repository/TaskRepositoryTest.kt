package edu.bluejack25_2.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack25_2.hwixel.data.mapper.toEntity
import edu.bluejack25_2.hwixel.data.model.Comment
import edu.bluejack25_2.hwixel.data.model.HistoryEntry
import edu.bluejack25_2.hwixel.data.model.Project
import edu.bluejack25_2.hwixel.data.model.ProjectMember
import edu.bluejack25_2.hwixel.data.model.Task
import edu.bluejack25_2.hwixel.data.source.local.TaskDao
import edu.bluejack25_2.hwixel.data.source.local.TaskEntity
import edu.bluejack25_2.hwixel.data.source.remote.ProjectRemoteSource
import edu.bluejack25_2.hwixel.data.source.remote.NotificationFirebaseSource
import edu.bluejack25_2.hwixel.data.source.remote.TaskRemoteSource
import edu.bluejack25_2.hwixel.util.constants.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepositoryTest {
    private val firebaseSource = FakeTaskRemoteSource()
    private val localDao = FakeTaskDao()
    private val projectSource = FakeProjectRemoteSource()
    private val repository = TaskRepositoryImpl(firebaseSource, localDao, projectSource)

    @Test
    fun createTaskWritesToFirebaseAndLocalCache() = runTest {
        val task = Task(id = "task-1", projectId = "project-1", title = "Prototype")

        val result = repository.createTask(task)

        assertTrue(result.isSuccess)
        assertEquals(task, firebaseSource.createdTask)
        assertEquals(task.id, localDao.upsertedTask?.id)
    }

    @Test
    fun updateTaskWritesToFirebaseAndLocalCache() = runTest {
        val task = Task(id = "task-1", projectId = "project-1", title = "Prototype v2")

        val result = repository.updateTask(task)

        assertTrue(result.isSuccess)
        assertEquals(task, firebaseSource.updatedTask)
        assertEquals(task.id, localDao.upsertedTask?.id)
    }

    @Test
    fun createTaskRecomputesCompletionPercentageAfterAddingTodoTask() = runTest {
        firebaseSource.tasksForProject = listOf(
            Task(id = "done", projectId = "project-1", status = Constants.STATUS_DONE),
            Task(id = "new", projectId = "project-1", status = Constants.STATUS_TODO)
        )

        val result = repository.createTask(
            Task(id = "new", projectId = "project-1", status = Constants.STATUS_TODO)
        )

        assertTrue(result.isSuccess)
        assertEquals(50f, projectSource.updatedCompletionPercentage)
    }

    @Test
    fun createTaskNotifiesAssignedUsers() = runTest {
        val notificationSource = FakeNotificationSource()
        val repo = TaskRepositoryImpl(firebaseSource, localDao, projectSource, notificationSource)
        val task = Task(
            id = "task-1",
            projectId = "project-1",
            title = "Prototype",
            assignees = listOf("u1", "u2")
        )

        val result = repo.createTask(task)

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("u1" to "project-1|task-1", "u2" to "project-1|task-1"),
            notificationSource.writes.map { it.userId to it.referenceId }
        )
    }

    @Test
    fun updateTaskNotifiesAssignedUsersWhenDeadlineChanges() = runTest {
        val notificationSource = FakeNotificationSource()
        val repo = TaskRepositoryImpl(firebaseSource, localDao, projectSource, notificationSource)
        localDao.taskToReturn = Task(
            id = "task-1",
            projectId = "project-1",
            title = "Prototype",
            deadline = 1_000L,
            assignees = listOf("u1", "u2")
        ).toEntity()
        val updated = Task(
            id = "task-1",
            projectId = "project-1",
            title = "Prototype",
            deadline = 2_000L,
            assignees = listOf("u1", "u2")
        )

        val result = repo.updateTask(updated)

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("u1" to "deadline", "u2" to "deadline"),
            notificationSource.writes.map { it.userId to it.type }
        )
    }

    @Test
    fun updateTaskDoesNotNotifyDeadlineWhenDeadlineIsUnchanged() = runTest {
        val notificationSource = FakeNotificationSource()
        val repo = TaskRepositoryImpl(firebaseSource, localDao, projectSource, notificationSource)
        localDao.taskToReturn = Task(
            id = "task-1",
            projectId = "project-1",
            deadline = 1_000L,
            assignees = listOf("u1")
        ).toEntity()
        val updated = Task(
            id = "task-1",
            projectId = "project-1",
            deadline = 1_000L,
            assignees = listOf("u1")
        )

        val result = repo.updateTask(updated)

        assertTrue(result.isSuccess)
        assertTrue(notificationSource.writes.isEmpty())
    }

    @Test
    fun statusChangeAwayFromDoneRecomputesCompletionPercentage() = runTest {
        firebaseSource.tasksForProject = listOf(
            Task(id = "task-1", projectId = "project-1", status = Constants.STATUS_TODO),
            Task(id = "task-2", projectId = "project-1", status = Constants.STATUS_DONE)
        )

        val result = repository.updateTaskStatus(
            projectId = "project-1",
            taskId = "task-1",
            newStatus = Constants.STATUS_TODO,
            actorId = "u1"
        )

        assertTrue(result.isSuccess)
        assertEquals(50f, projectSource.updatedCompletionPercentage)
    }

    private class FakeTaskRemoteSource : TaskRemoteSource {
        var createdTask: Task? = null
        var updatedTask: Task? = null
        var tasksForProject: List<Task> = emptyList()

        override fun observeAllTasks(): LiveData<List<Task>> = MutableLiveData(emptyList())

        override fun observeTasks(projectId: String): LiveData<List<Task>> = MutableLiveData(emptyList())

        override fun observeTask(projectId: String, taskId: String): LiveData<Task?> = MutableLiveData(null)

        override suspend fun fetchTasksOnce(projectId: String): List<Task> {
            return tasksForProject.filter { it.projectId == projectId }
        }

        override suspend fun createTask(task: Task): Task {
            createdTask = task
            return task
        }

        override suspend fun updateTask(task: Task) {
            updatedTask = task
        }

        override suspend fun updateTaskStatus(
            projectId: String,
            taskId: String,
            status: String,
            completedAt: Long
        ) = Unit

        override suspend fun addHistoryEntry(projectId: String, taskId: String, entry: HistoryEntry) = Unit

        override suspend fun addComment(
            projectId: String,
            taskId: String,
            comment: Comment
        ) = Unit

        override suspend fun updateSubtask(projectId: String, taskId: String, subtaskId: String, isDone: Boolean) = Unit

        override suspend fun deleteTask(projectId: String, taskId: String) = Unit
    }

    private class FakeNotificationSource : NotificationFirebaseSource(null) {
        val writes = mutableListOf<Write>()

        override suspend fun writeNotification(
            userId: String,
            type: String,
            message: String,
            referenceId: String
        ) {
            writes.add(Write(userId, type, message, referenceId))
        }
    }

    private data class Write(
        val userId: String,
        val type: String,
        val message: String,
        val referenceId: String
    )

    private class FakeProjectRemoteSource : ProjectRemoteSource {
        var updatedCompletionPercentage: Float? = null

        override fun observeProjects(): LiveData<List<Project>> = MutableLiveData(emptyList())

        override fun observeProject(projectId: String): LiveData<Project?> = MutableLiveData(null)

        override suspend fun createProject(project: Project): Project = project

        override suspend fun updateProject(project: Project) = Unit

        override suspend fun updateCompletionPercentage(projectId: String, percentage: Float) {
            updatedCompletionPercentage = percentage
        }

        override suspend fun addMember(projectId: String, userId: String, member: ProjectMember) = Unit

        override suspend fun updateMember(projectId: String, userId: String, member: ProjectMember) = Unit

        override suspend fun updateMemberScore(projectId: String, userId: String, score: Float) = Unit
    }

    private class FakeTaskDao : TaskDao {
        var upsertedTask: TaskEntity? = null
        var taskToReturn: TaskEntity? = null

        override fun observeAll(): LiveData<List<TaskEntity>> {
            return MutableLiveData(emptyList())
        }

        override fun observeByProject(projectId: String): LiveData<List<TaskEntity>> {
            return MutableLiveData(emptyList())
        }

        override suspend fun getById(taskId: String): TaskEntity? = taskToReturn

        override suspend fun upsert(task: TaskEntity) {
            upsertedTask = task
        }

        override suspend fun delete(task: TaskEntity) = Unit
    }
}
