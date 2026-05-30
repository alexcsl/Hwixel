package edu.bluejack252.hwixel.ui.project.hub

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.tabs.TabLayoutMediator
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.databinding.FragmentProjectHubBinding
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ProjectHubFragment : Fragment() {

    private var _binding: FragmentProjectHubBinding? = null
    private val binding get() = _binding!!

    private val args: ProjectHubFragmentArgs by navArgs()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val activityFeedAdapter = ActivityFeedAdapter()

    private val viewModel: ProjectHubViewModel by viewModels {
        ProjectHubViewModelFactory(
            projectId = args.projectId,
            projectRepository = ServiceLocator.getProjectRepository(requireContext()),
            taskRepository = ServiceLocator.getTaskRepository(requireContext()),
            userRepository = ServiceLocator.getUserRepository(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        setupViewPager()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.createProjectResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isSuccess) {
                Snackbar.make(binding.root, R.string.project_created, Snackbar.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Failed to create project", result.exceptionOrNull())
                val message = result.exceptionOrNull()?.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.project_create_failed_format, it) }
                    ?: getString(R.string.project_create_failed)
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
            viewModel.consumeCreateResult()
        }
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.attendanceButton.setOnClickListener {
            val project = viewModel.uiState.value?.project ?: return@setOnClickListener
            val action = ProjectHubFragmentDirections
                .actionProjectHubFragmentToAttendanceFragment(
                    projectId = args.projectId,
                    projectName = project.name
                )
            findNavController().navigate(action)
        }
        binding.peerEvalButton.setOnClickListener {
            val (ids, names, roles) = viewModel.getMembersForNav()
            val action = ProjectHubFragmentDirections
                .actionProjectHubFragmentToPeerEvalFragment(
                    projectId = args.projectId,
                    memberIds = ids,
                    memberNames = names,
                    memberRoles = roles
                )
            findNavController().navigate(action)
        }
        binding.filesButton.setOnClickListener {
            val action = ProjectHubFragmentDirections
                .actionProjectHubFragmentToFileRepoFragment(projectId = args.projectId)
            findNavController().navigate(action)
        }
    }

    private fun setupViewPager() {
        val pagerAdapter = ProjectPagerAdapter(childFragmentManager, lifecycle, args.projectId)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.hub_tab_tasks)
                1 -> getString(R.string.hub_tab_analytics)
                2 -> getString(R.string.hub_tab_members)
                else -> ""
            }
        }.attach()
    }

    private fun render(state: ProjectHubUiState) {
        if (state.isLoading || state.project == null) return
        renderHeader(state.project)
        activityFeedAdapter.submitList(state.recentActivity)
    }

    private fun renderHeader(project: Project) {
        binding.projectNameTextView.text = project.name
        binding.projectGoalsTextView.text = project.goals
        binding.projectGoalsTextView.isVisible = project.goals.isNotBlank()
        binding.projectDescriptionTextView.text = project.description
        binding.projectDescriptionTextView.isVisible = project.description.isNotBlank()
        binding.completionProgressBar.progress = project.completionPercentage.roundToInt().coerceIn(0, 100)
        binding.completionPercentageTextView.text = getString(R.string.hub_completion_format, project.completionPercentage)

        val now = System.currentTimeMillis()
        if (project.dueDate > 0L) {
            val dateStr = dateFormat.format(Date(project.dueDate))
            binding.dueDateTextView.text = getString(R.string.hub_due_date_format, dateStr)
            if (project.dueDate < now) {
                binding.dueDateTextView.setTextColor(
                    requireContext().getColor(android.R.color.holo_red_light)
                )
            } else {
                binding.dueDateTextView.setTextColor(
                    requireContext().getColor(android.R.color.darker_gray)
                )
            }
        } else {
            binding.dueDateTextView.isVisible = false
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "ProjectHubFragment"
    }
}
