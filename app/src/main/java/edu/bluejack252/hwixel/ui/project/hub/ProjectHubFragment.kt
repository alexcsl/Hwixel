package edu.bluejack252.hwixel.ui.project.hub

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
import androidx.navigation.fragment.navArgs
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.databinding.DialogCreateProjectBinding
import edu.bluejack252.hwixel.databinding.FragmentProjectHubBinding
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ProjectHubFragment : Fragment() {

    private var _binding: FragmentProjectHubBinding? = null
    private val binding get() = _binding!!

    private val args: ProjectHubFragmentArgs by navArgs()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var selectedDueDate: Long = 0L
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
        binding.activityRecyclerView.adapter = activityFeedAdapter
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.createProjectResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isSuccess) {
                Snackbar.make(binding.root, R.string.project_created, Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, R.string.project_create_failed, Snackbar.LENGTH_SHORT).show()
            }
            viewModel.consumeCreateResult()
        }
        binding.addProjectFab.setOnClickListener { showCreateProjectDialog() }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupViewPager() {
        val pagerAdapter = ProjectPagerAdapter(childFragmentManager, lifecycle, args.projectId)
        binding.viewPager.adapter = pagerAdapter
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
        val project = state.project ?: return
        renderHeader(project)
        activityFeedAdapter.submitList(state.recentActivity)
        binding.emptyActivityTextView.isVisible = state.recentActivity.isEmpty()
    }

    private fun renderHeader(project: Project) {
        binding.projectNameTextView.text = project.name
        binding.projectGoalsTextView.text = project.goals
        binding.projectGoalsTextView.isVisible = project.goals.isNotBlank()
        binding.projectDescriptionTextView.text = project.description
        binding.projectDescriptionTextView.isVisible = project.description.isNotBlank()
        binding.completionProgressBar.progress = project.completionPercentage.roundToInt().coerceIn(0, 100)
        binding.completionTextView.text = getString(R.string.hub_completion_format, project.completionPercentage)

        val now = System.currentTimeMillis()
        if (project.dueDate > 0L) {
            val dateStr = dateFormat.format(Date(project.dueDate))
            binding.projectDueDateTextView.text = getString(R.string.hub_due_date_format, dateStr)
            if (project.dueDate < now) {
                binding.projectDueDateTextView.setTextColor(
                    requireContext().getColor(android.R.color.holo_red_light)
                )
            } else {
                binding.projectDueDateTextView.setTextColor(
                    requireContext().getColor(android.R.color.darker_gray)
                )
            }
        } else {
            binding.projectDueDateTextView.isVisible = false
        }
    }

    private fun showCreateProjectDialog() {
        val dialogView = DialogCreateProjectBinding.inflate(layoutInflater)
        selectedDueDate = 0L
        dialogView.pickDueDateButton.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    cal.set(year, month, day, 0, 0, 0)
                    selectedDueDate = cal.timeInMillis
                    dialogView.pickDueDateButton.text = dateFormat.format(cal.time)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.create_project_title)
            .setView(dialogView.root)
            .setPositiveButton(R.string.btn_create) { _, _ ->
                val name = dialogView.projectNameEditText.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    Snackbar.make(binding.root, R.string.error_empty_project_name, Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val description = dialogView.projectDescriptionEditText.text?.toString().orEmpty().trim()
                val goals = dialogView.projectGoalsEditText.text?.toString().orEmpty().trim()
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
