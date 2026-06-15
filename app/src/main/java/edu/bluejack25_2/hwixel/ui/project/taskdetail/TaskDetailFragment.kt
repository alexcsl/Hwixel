package edu.bluejack25_2.hwixel.ui.project.taskdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.data.ServiceLocator
import edu.bluejack25_2.hwixel.data.model.Task
import edu.bluejack25_2.hwixel.databinding.FragmentTaskDetailBinding
import edu.bluejack25_2.hwixel.util.constants.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskDetailFragment : Fragment() {

    private var _binding: FragmentTaskDetailBinding? = null
    private val binding get() = _binding!!

    private val args: TaskDetailFragmentArgs by navArgs()
    private val currentUserId: String by lazy {
        FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    private val commentsAdapter = CommentsAdapter()
    private val historyAdapter = HistoryAdapter()
    private var historyExpanded = false
    private val attachmentAdapter = AttachmentAdapter { attachment ->
        ImageAttachmentDialogFragment.newInstance(attachment.url)
            .show(childFragmentManager, "image_attachment")
    }
    private val subtaskAdapter = SubtaskToggleAdapter { subtask, isDone ->
        viewModel.toggleSubtask(subtask.id, isDone)
    }

    private val viewModel: TaskDetailViewModel by viewModels {
        TaskDetailViewModelFactory(
            projectId = args.projectId,
            taskId = args.taskId,
            currentUserId = currentUserId,
            taskRepository = ServiceLocator.getTaskRepository(requireContext()),
            projectRepository = ServiceLocator.getProjectRepository(requireContext()),
            userRepository = ServiceLocator.getUserRepository(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.editButton.setOnClickListener {
            val state = viewModel.uiState.value?.task ?: return@setOnClickListener
            findNavController().navigate(
                TaskDetailFragmentDirections.actionTaskDetailFragmentToCreateEditTaskFragment(
                    projectId = args.projectId,
                    taskId = state.id
                )
            )
        }
        binding.attachmentsRecyclerView.adapter = attachmentAdapter
        binding.subtasksRecyclerView.adapter = subtaskAdapter
        binding.commentsRecyclerView.adapter = commentsAdapter
        binding.historyRecyclerView.adapter = historyAdapter
        binding.historyToggleButton.setOnClickListener {
            historyExpanded = !historyExpanded
            renderHistoryToggle()
        }
        renderHistoryToggle()

        binding.sendCommentButton.setOnClickListener {
            val text = binding.commentEditText.text?.toString().orEmpty().trim()
            if (text.isBlank()) {
                Snackbar.make(binding.root, R.string.error_empty_comment, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addComment(text, currentUserId)
            binding.commentEditText.text?.clear()
        }

        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.commentResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isSuccess) {
                Snackbar.make(binding.root, R.string.comment_added, Snackbar.LENGTH_SHORT).show()
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.error_generic)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
            viewModel.consumeCommentResult()
        }
        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isSuccess) {
                Snackbar.make(binding.root, R.string.task_deleted, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.task_delete_failed)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
            viewModel.consumeDeleteResult()
        }
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit_task -> {
                val state = viewModel.uiState.value?.task ?: return false
                findNavController().navigate(
                    TaskDetailFragmentDirections.actionTaskDetailFragmentToCreateEditTaskFragment(
                        projectId = args.projectId,
                        taskId = state.id
                    )
                )
                true
            }
            R.id.action_delete_task -> {
                viewModel.deleteTask()
                true
            }
            else -> false
        }
    }

    private fun render(state: TaskDetailUiState) {
        binding.loadingIndicator.isVisible = state.isLoading
        binding.titleContainer.isVisible = !state.isLoading
        binding.bodyContainer.isVisible = !state.isLoading
        if (state.isLoading) return
        val task = state.task ?: return
        renderTask(task, state.assigneeNames)
        attachmentAdapter.submitList(state.attachments)
        subtaskAdapter.submitList(state.subtasks)
        commentsAdapter.submitList(state.comments)
        historyAdapter.submitList(state.history)
        renderHistoryToggle(state.history.size)
        binding.editButton.isVisible = state.canEditTask
        binding.subtaskOverallProgress.isVisible = state.subtasks.isNotEmpty()
    }

    private fun renderHistoryToggle(count: Int = historyAdapter.itemCount) {
        val label = getString(R.string.task_detail_history_label)
        val countSuffix = if (count > 0) " ($count)" else ""
        val arrow = if (historyExpanded) "  ▾" else "  ▸"
        binding.historyToggleButton.text = label + countSuffix + arrow
        binding.historyRecyclerView.isVisible = historyExpanded && count > 0
    }

    private fun renderTask(task: Task, assigneeNames: List<String>) {
        binding.taskTitleTextView.text = task.title
        binding.statusChip.text = task.status.replace("_", " ").replaceFirstChar { it.uppercase() }
        binding.priorityChip.text = task.priority.replaceFirstChar { it.uppercase() }

        if (task.deadline > 0L) {
            binding.deadlineTextView.text = getString(
                R.string.task_detail_deadline_label,
                dateFormat.format(Date(task.deadline))
            )
            if (task.deadline < System.currentTimeMillis() && task.status != Constants.STATUS_DONE) {
                binding.deadlineTextView.setTextColor(
                    requireContext().getColor(R.color.brand_rose)
                )
            } else {
                binding.deadlineTextView.setTextColor(
                    requireContext().getColor(R.color.text_secondary)
                )
            }
        } else {
            binding.deadlineTextView.text = getString(R.string.task_no_deadline)
        }

        binding.descriptionTextView.text = task.description.ifBlank {
            getString(R.string.task_no_description)
        }

        if (task.assignees.isNotEmpty()) {
            binding.assigneesTextView.isVisible = true
            binding.assigneesTextView.text = getString(
                R.string.task_detail_assignees_label,
                assigneeNames.joinToString(", ")
            )
        } else {
            binding.assigneesTextView.isVisible = false
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
