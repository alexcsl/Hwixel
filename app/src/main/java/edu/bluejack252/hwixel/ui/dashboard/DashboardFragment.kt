package edu.bluejack252.hwixel.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

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
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.loadDashboard(FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
    }

    private fun render(state: DashboardUiState) {
        projectAdapter.submitList(state.projects)
        deadlineAdapter.submitList(state.deadlines)
        binding.pendingTasksTextView.text = getString(
            R.string.dashboard_pending_tasks_format,
            state.pendingTaskCount
        )
        binding.emptyProjectsTextView.isVisible = state.projects.isEmpty()
        binding.emptyDeadlinesTextView.isVisible = state.deadlines.isEmpty()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
