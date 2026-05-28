package edu.bluejack252.hwixel.ui.project.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.databinding.FragmentTaskBoardBinding
import edu.bluejack252.hwixel.ui.project.hub.ProjectHubFragmentDirections
import edu.bluejack252.hwixel.ui.project.hub.ProjectPagerAdapter
import edu.bluejack252.hwixel.ui.project.hub.fillViewPagerPage
import edu.bluejack252.hwixel.util.constants.Constants

class TaskBoardFragment : Fragment() {

    private var _binding: FragmentTaskBoardBinding? = null
    private val binding get() = _binding!!

    private val projectId: String by lazy {
        arguments?.getString(ProjectPagerAdapter.ARG_PROJECT_ID).orEmpty()
    }
    private val currentUserId: String by lazy {
        FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }

    private val listAdapter = TaskCardAdapter(
        onTaskClick = ::navigateToDetail,
        onStatusChange = ::showStatusDialog
    )

    private val todoAdapter = TaskCardAdapter(::navigateToDetail, ::showStatusDialog)
    private val inProgressAdapter = TaskCardAdapter(::navigateToDetail, ::showStatusDialog)
    private val reviewAdapter = TaskCardAdapter(::navigateToDetail, ::showStatusDialog)
    private val doneAdapter = TaskCardAdapter(::navigateToDetail, ::showStatusDialog)

    private val viewModel: TaskBoardViewModel by viewModels {
        TaskBoardViewModelFactory(
            projectId = projectId,
            taskRepository = ServiceLocator.getTaskRepository(requireContext()),
            userRepository = ServiceLocator.getUserRepository(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskBoardBinding.inflate(inflater, container, false)
        return binding.root.fillViewPagerPage()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupListView()
        setupKanbanView()
        setupToggle()
        setupFab()
        setupFilter()
        observeCreateTaskPermission()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.statusUpdateResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.error_generic)
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show()
            }
            viewModel.consumeStatusResult()
        }
    }

    private fun setupListView() {
        binding.tasksListRecyclerView.adapter = listAdapter
    }

    private fun setupKanbanView() {
        fun bindColumn(columnBinding: edu.bluejack252.hwixel.databinding.LayoutKanbanColumnBinding, title: String, adapter: TaskCardAdapter) {
            columnBinding.columnTitleTextView.text = title
            columnBinding.columnRecyclerView.adapter = adapter
        }
        bindColumn(binding.columnTodo, getString(R.string.task_column_todo), todoAdapter)
        bindColumn(binding.columnInProgress, getString(R.string.task_column_in_progress), inProgressAdapter)
        bindColumn(binding.columnReview, getString(R.string.task_column_review), reviewAdapter)
        bindColumn(binding.columnDone, getString(R.string.task_column_done), doneAdapter)
    }

    private fun setupToggle() {
        binding.viewToggleGroup.check(binding.listButton.id)
        binding.viewToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                binding.listButton.id -> viewModel.setViewMode(ViewMode.LIST)
                binding.kanbanButton.id -> viewModel.setViewMode(ViewMode.KANBAN)
            }
        }
    }

    private fun setupFab() {
        binding.addTaskFab.isVisible = false
        binding.addTaskFab.setOnClickListener {
            requireParentFragment().findNavController().navigate(
                ProjectHubFragmentDirections.actionProjectHubFragmentToCreateEditTaskFragment(
                    projectId = projectId,
                    taskId = ""
                )
            )
        }
    }

    private fun observeCreateTaskPermission() {
        ServiceLocator.getProjectRepository(requireContext())
            .observeProject(projectId)
            .observe(viewLifecycleOwner) { project ->
                val role = project?.members?.get(currentUserId)?.role
                    ?: Constants.ROLE_TEAM_LEAD.takeIf { project?.createdBy == currentUserId }
                binding.addTaskFab.isVisible = role == Constants.ROLE_TEAM_LEAD
            }
    }

    private fun setupFilter() {
        binding.filterButton.setOnClickListener {
            val state = viewModel.uiState.value ?: return@setOnClickListener
            val sheet = FilterBottomSheet()
            sheet.setMembers(state.memberNames)
            sheet.setCurrentFilter(state.filter)
            sheet.onFilterApplied = { filter -> viewModel.setFilter(filter) }
            sheet.onFilterReset = { viewModel.resetFilter() }
            sheet.show(childFragmentManager, FilterBottomSheet.TAG)
        }
    }

    private fun render(state: TaskBoardUiState) {
        val isKanban = state.viewMode == ViewMode.KANBAN
        binding.tasksListRecyclerView.isVisible = !isKanban
        binding.kanbanScrollView.isVisible = isKanban

        if (isKanban) {
            todoAdapter.submitList(state.filteredTasks.filter { it.status == Constants.STATUS_TODO })
            inProgressAdapter.submitList(state.filteredTasks.filter { it.status == Constants.STATUS_IN_PROGRESS })
            reviewAdapter.submitList(state.filteredTasks.filter { it.status == Constants.STATUS_REVIEW })
            doneAdapter.submitList(state.filteredTasks.filter { it.status == Constants.STATUS_DONE })
        } else {
            listAdapter.submitList(state.filteredTasks)
        }
    }

    private fun navigateToDetail(task: Task) {
        requireParentFragment().findNavController().navigate(
            ProjectHubFragmentDirections.actionProjectHubFragmentToTaskDetailFragment(
                projectId = projectId,
                taskId = task.id
            )
        )
    }

    private fun showStatusDialog(task: Task) {
        val statuses = arrayOf(
            getString(R.string.task_column_todo),
            getString(R.string.task_column_in_progress),
            getString(R.string.task_column_review),
            getString(R.string.task_column_done)
        )
        val statusValues = arrayOf(
            Constants.STATUS_TODO,
            Constants.STATUS_IN_PROGRESS,
            Constants.STATUS_REVIEW,
            Constants.STATUS_DONE
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.task_change_status)
            .setItems(statuses) { _, which ->
                viewModel.updateTaskStatus(task.id, statusValues[which], currentUserId)
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
