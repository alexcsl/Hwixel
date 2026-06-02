package edu.bluejack252.hwixel.ui.dashboard

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.databinding.FragmentDashboardBinding
import kotlin.math.max

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var secondsTicker: CountDownTimer? = null
    private var activeDeadlineMillis: Long = 0L
    private var seeAllProjectId: String? = null

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
        binding.addProjectFab.setOnClickListener { showCreateProjectBottomSheet() }

        binding.profileAvatarButton.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView)
                ?.selectedItemId = R.id.profileFragment
        }
        binding.notificationBellButton.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView)
                ?.selectedItemId = R.id.notificationsFragment
        }
        binding.deadlinesSeeAllButton.setOnClickListener {
            val projectId = seeAllProjectId ?: return@setOnClickListener
            val action = DashboardFragmentDirections
                .actionDashboardFragmentToProjectHubFragment(projectId)
            findNavController().navigate(action)
        }

        setupGreeting()
        observeNotificationBell()
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
        viewModel.loadDashboard(FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
    }

    private fun observeNotificationBell() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ServiceLocator.getNotificationRepository()
            .observeNotifications(uid)
            .observe(viewLifecycleOwner) { notifications ->
                val unread = notifications.count { !it.isRead }
                binding.bellUnreadBadge.isVisible = unread > 0
                if (unread > 0) {
                    binding.bellUnreadBadge.text = if (unread > 9) "9+" else unread.toString()
                }
            }
    }

    private fun setupGreeting() {
        // Seed from Firebase display name, then keep in sync with the stored profile.
        applyGreeting(FirebaseAuth.getInstance().currentUser?.displayName)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ServiceLocator.getUserRepository(requireContext())
            .observeUser(uid)
            .observe(viewLifecycleOwner) { user ->
                applyGreeting(user?.name)
            }
    }

    private fun applyGreeting(rawName: String?) {
        val displayName = rawName?.takeIf { it.isNotBlank() }
        val firstName = displayName?.split(" ")?.firstOrNull() ?: getString(R.string.profile_unknown_name)
        binding.greetingTextView.text = getString(R.string.dashboard_greeting_format, firstName)
        val initials = displayName
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.take(2)
            ?.joinToString("") { it.first().uppercase() }
            ?: "?"
        binding.greetingInitialsTextView.text = initials
    }

    private fun render(state: DashboardUiState) {
        binding.loadingIndicator.isVisible = state.isLoading
        projectAdapter.submitList(state.projects)
        deadlineAdapter.submitList(state.deadlines)

        binding.pendingTasksTextView.text = state.pendingTaskCount.toString()
        binding.activeProjectsStatTextView.text = state.projects.size.toString()
        binding.completedTasksTextView.text = state.completedTaskCount.toString()

        binding.greetingSubtitleTextView.text = getString(
            R.string.dashboard_tasks_due_format,
            state.pendingTaskCount
        )

        binding.emptyProjectsTextView.isVisible = !state.isLoading && state.projects.isEmpty()
        binding.emptyDeadlinesTextView.isVisible = !state.isLoading && state.deadlines.isEmpty()
        binding.deadlinesSeeAllButton.isVisible = state.deadlines.isNotEmpty()
        seeAllProjectId = state.deadlines.firstOrNull()?.projectId

        val nearest = state.deadlines.firstOrNull()
        binding.featuredDeadlineContainer.isVisible = nearest != null
        if (nearest != null) {
            binding.featuredDeadlineTitleTextView.text = nearest.taskTitle
            binding.featuredDeadlineProjectTextView.text = nearest.projectName
            binding.featuredDeadlineDaysTextView.text = nearest.countdown.days.toString().padStart(2, '0')
            binding.featuredDeadlineHoursTextView.text = nearest.countdown.hours.toString().padStart(2, '0')
            binding.featuredDeadlineMinsTextView.text = nearest.countdown.minutes.toString().padStart(2, '0')
            if (activeDeadlineMillis != nearest.deadline) {
                activeDeadlineMillis = nearest.deadline
                startSecondsTicker(nearest.deadline)
            }
        } else {
            secondsTicker?.cancel()
            activeDeadlineMillis = 0L
        }
    }

    private fun startSecondsTicker(deadlineMillis: Long) {
        secondsTicker?.cancel()
        val remaining = max(0L, deadlineMillis - System.currentTimeMillis())
        secondsTicker = object : CountDownTimer(remaining, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1_000L) % 60L
                _binding?.featuredDeadlineSecsTextView?.text = secs.toString().padStart(2, '0')
            }
            override fun onFinish() {
                _binding?.featuredDeadlineSecsTextView?.text = "00"
            }
        }.start()
    }

    private fun showCreateProjectBottomSheet() {
        val sheet = CreateProjectBottomSheetFragment.newInstance()
        sheet.onProjectCreate = { name, description, goals, dueDate ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            viewModel.createProject(name, description, goals, dueDate, uid)
        }
        sheet.show(parentFragmentManager, CreateProjectBottomSheetFragment.TAG)
    }

    override fun onDestroyView() {
        secondsTicker?.cancel()
        secondsTicker = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "DashboardFragment"
    }
}
