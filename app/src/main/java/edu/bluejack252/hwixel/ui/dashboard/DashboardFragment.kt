package edu.bluejack252.hwixel.ui.dashboard

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.databinding.DialogCreateProjectBinding
import edu.bluejack252.hwixel.databinding.FragmentDashboardBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var selectedDueDate: Long = 0L

    private val projectAdapter = DashboardProjectAdapter { project ->
        val action = DashboardFragmentDirections.actionDashboardFragmentToProjectHubFragment(project.id)
        findNavController().navigate(action)
    }
    private val deadlineAdapter = DashboardDeadlineAdapter()

    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(
            projectRepository = ServiceLocator.getProjectRepository(requireContext()),
            taskRepository = ServiceLocator.getTaskRepository(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.projectsRecyclerView.adapter = projectAdapter
        binding.deadlinesRecyclerView.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.deadlinesRecyclerView.adapter = deadlineAdapter
        binding.addProjectFab.setOnClickListener { showCreateProjectDialog() }
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.createProjectResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            val message = if (result.isSuccess) R.string.project_created else R.string.project_create_failed
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            viewModel.consumeCreateResult()
        }
        viewModel.loadDashboard(FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
    }

    private fun render(state: DashboardUiState) {
        projectAdapter.submitList(state.projects)
        deadlineAdapter.submitList(state.deadlines)
        binding.pendingTasksTextView.text = resources.getQuantityString(
            R.plurals.dashboard_pending_tasks,
            state.pendingTaskCount,
            state.pendingTaskCount
        )
        binding.emptyProjectsTextView.isVisible = state.projects.isEmpty()
        binding.emptyDeadlinesTextView.isVisible = state.deadlines.isEmpty()
    }

    private fun showCreateProjectDialog() {
        val dialogView = DialogCreateProjectBinding.inflate(layoutInflater)
        selectedDueDate = 0L
        dialogView.projectDeadlineButton.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    cal.set(year, month, day, 0, 0, 0)
                    selectedDueDate = cal.timeInMillis
                    dialogView.projectDeadlineButton.text = dateFormat.format(cal.time)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.create_project_title)
            .setView(dialogView.root)
            .setPositiveButton(R.string.btn_create) { _, _ ->
                val name = dialogView.projectNameInput.editText?.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    Snackbar.make(binding.root, R.string.error_empty_project_name, Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val description = dialogView.projectDescriptionInput.editText?.text?.toString().orEmpty().trim()
                val goals = dialogView.projectGoalsInput.editText?.text?.toString().orEmpty().trim()
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                viewModel.createProject(name, description, goals, selectedDueDate, uid)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
