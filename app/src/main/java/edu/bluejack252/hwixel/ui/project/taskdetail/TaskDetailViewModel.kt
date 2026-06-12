package edu.bluejack252.hwixel.ui.project.taskdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.Comment
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.UserRepository
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.launch

class TaskDetailViewModel(
    private val projectId: String,
    private val taskId: String,
    private val currentUserId: String,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MediatorLiveData<TaskDetailUiState>()
    val uiState: LiveData<TaskDetailUiState> = _uiState

    private val _commentResult = MutableLiveData<Result<Unit>?>()
    val commentResult: LiveData<Result<Unit>?> = _commentResult

    private val _deleteResult = MutableLiveData<Result<Unit>?>()
    val deleteResult: LiveData<Result<Unit>?> = _deleteResult

    private var currentTask: Task? = null
    private var currentProject: Project? = null
    private var users: List<User> = emptyList()

    init {
        _uiState.value = TaskDetailUiState(isLoading = true)
        _uiState.addSource(taskRepository.observeTask(projectId, taskId)) { task ->
            currentTask = task
            publishState()
        }
        _uiState.addSource(projectRepository.observeProject(projectId)) { project ->
            currentProject = project
            publishState()
        }
        _uiState.addSource(userRepository.observeUsers()) { userList ->
            users = userList
            publishState()
        }
    }

    private fun publishState() {
        val task = currentTask ?: return
        _uiState.value = TaskDetailUiState(
            task = task,
            assigneeNames = task.assignees.map(::displayName),
            attachments = task.attachments,
            subtasks = task.subtasks.values.sortedBy { it.id },
            comments = task.comments.values.sortedBy { it.timestamp }.map { comment ->
                CommentUi(
                    id = comment.id,
                    authorName = displayName(comment.authorId),
                    content = comment.content,
                    timestamp = comment.timestamp
                )
            },
            history = task.history.values.sortedByDescending { it.timestamp }.map { entry ->
                HistoryUi(
                    id = entry.id,
                    actorName = displayName(entry.actorId),
                    action = entry.action,
                    timestamp = entry.timestamp
                )
            },
            canEditTask = currentProject?.members?.get(currentUserId)?.role == Constants.ROLE_TEAM_LEAD,
            isLoading = false
        )
    }

    private fun displayName(userId: String): String {
        return users.firstOrNull { it.id == userId }?.name?.takeIf { it.isNotBlank() }
            ?: UNKNOWN_MEMBER_NAME
    }

    private companion object {
        const val UNKNOWN_MEMBER_NAME = "Unknown member"
    }

    fun addComment(content: String, authorId: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val comment = Comment(
                authorId = authorId,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            _commentResult.value = taskRepository.addComment(
                projectId = projectId,
                taskId = taskId,
                comment = comment,
                mentionedUserIds = resolveMentionedUserIds(content, authorId)
            )
        }
    }

    private fun resolveMentionedUserIds(content: String, authorId: String): List<String> {
        val projectMemberIds = currentProject?.members?.keys.orEmpty()
        val eligibleUsers = users
            .asSequence()
            .filter { user -> projectMemberIds.isEmpty() || user.id in projectMemberIds }
            .filter { user -> user.id != authorId }
        if (containsAllMention(content)) {
            return eligibleUsers.map { it.id }.distinct().toList()
        }
        return eligibleUsers
            .filter { user -> user.name.isNotBlank() }
            .filter { user -> containsNameMention(content, user.name) || containsCompactNameMention(content, user.name) }
            .map { user -> user.id }
            .distinct()
            .toList()
    }

    private fun containsAllMention(content: String): Boolean =
        Regex("(?i)(^|\\s)@all(?=\\s|$|[.,!?;:])").containsMatchIn(content)

    private fun containsNameMention(content: String, name: String): Boolean {
        val normalizedName = name.trim().replace(Regex("\\s+"), " ")
        val pattern = Regex(
            pattern = "(?i)(^|\\s)@${Regex.escape(normalizedName)}(?=\\s|$|[.,!?;:])"
        )
        return pattern.containsMatchIn(content.trim().replace(Regex("\\s+"), " "))
    }

    private fun containsCompactNameMention(content: String, name: String): Boolean {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val firstName = parts.firstOrNull().orEmpty()
        val compactName = parts.joinToString("")
        return listOf(firstName, compactName)
            .filter { it.isNotBlank() }
            .any { mention ->
                Regex("(?i)(^|\\s)@${Regex.escape(mention)}(?=\\s|$|[.,!?;:])")
                    .containsMatchIn(content)
            }
    }

    fun deleteTask() {
        val task = currentTask ?: return
        viewModelScope.launch {
            _deleteResult.value = taskRepository.deleteTask(task)
        }
    }

    fun toggleSubtask(subtaskId: String, isDone: Boolean) {
        val subtaskTitle = currentTask?.subtasks?.get(subtaskId)?.title.orEmpty()
        currentTask?.let { task ->
            task.subtasks[subtaskId]?.let { subtask ->
                currentTask = task.copy(
                    subtasks = task.subtasks + (subtaskId to subtask.copy(isDone = isDone))
                )
                publishState()
            }
        }
        viewModelScope.launch {
            taskRepository.updateSubtask(projectId, taskId, subtaskId, isDone)
            if (currentUserId.isNotBlank()) {
                val label = subtaskTitle.ifBlank { "a subtask" }
                val action = if (isDone) "checked subtask: $label" else "unchecked subtask: $label"
                taskRepository.addActivity(projectId, taskId, currentUserId, action)
            }
        }
    }

    fun consumeCommentResult() { _commentResult.value = null }
    fun consumeDeleteResult() { _deleteResult.value = null }
}
