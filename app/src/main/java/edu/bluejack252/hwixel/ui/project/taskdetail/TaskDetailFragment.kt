package edu.bluejack252.hwixel.ui.project.taskdetail

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
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.databinding.FragmentTaskDetailBinding
import edu.bluejack252.hwixel.util.constants.Constants
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
    private val attachmentAdapter = AttachmentAdapter()
    private val subtaskAdapter = SubtaskToggleAdapter { subtask, isDone ->
        viewModel.toggleSubtask(subtask.id, isDone)
    }

    private val viewModel: TaskDetailViewModel by viewModels {
        TaskDetailViewModelFactory(
            projectId = args.projectId,
            taskId = args.taskId,
            taskRepository = ServiceLocator.getTaskRepository(requireContext()),
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
            }
            viewModel.consumeCommentResult()
        }
        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isSuccess) {
                Snackbar.make(binding.root, R.string.task_deleted, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
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
        val task = state.task ?: return
        renderTask(task, state.assigneeNames)
        attachmentAdapter.submitList(state.attachments)
        subtaskAdapter.submitList(state.subtasks)
        commentsAdapter.submitList(state.comments)
        historyAdapter.submitList(state.history)
        binding.subtaskOverallProgress.isVisible = state.subtasks.isNotEmpty()
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
                    requireContext().getColor(android.R.color.holo_red_light)
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
