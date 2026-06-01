package edu.bluejack252.hwixel.ui.dashboard

import android.app.DatePickerDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
import edu.bluejack252.hwixel.databinding.DialogCreateProjectBinding
import edu.bluejack252.hwixel.databinding.FragmentDashboardBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var selectedDueDate: Long = 0L
    private var secondsTicker: CountDownTimer? = null
    private var activeDeadlineMillis: Long = 0L

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

        binding.profileAvatarButton.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView)
                ?.selectedItemId = R.id.profileFragment
        }
        binding.notificationBellButton.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView)
                ?.selectedItemId = R.id.notificationsFragment
        }

        setupGreeting()
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

    private fun setupGreeting() {
        val user = FirebaseAuth.getInstance().currentUser
        val displayName = user?.displayName?.takeIf { it.isNotBlank() }
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

        binding.pendingTasksTextView.text = resources.getQuantityString(
            R.plurals.dashboard_pending_tasks,
            state.pendingTaskCount,
            state.pendingTaskCount
        )
        binding.activeProjectsStatTextView.text = state.projects.size.toString()
        binding.completedTasksTextView.text = state.completedTaskCount.toString()

        binding.greetingSubtitleTextView.text = getString(
            R.string.dashboard_tasks_due_format,
            state.pendingTaskCount
        )

        binding.emptyProjectsTextView.isVisible = !state.isLoading && state.projects.isEmpty()
        binding.emptyDeadlinesTextView.isVisible = !state.isLoading && state.deadlines.isEmpty()

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

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.create_project_title)
            .setView(dialogView.root)
            .setPositiveButton(R.string.btn_create, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogView.projectNameInput.editText?.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    dialogView.projectNameInput.error = getString(R.string.error_empty_project_name)
                    return@setOnClickListener
                }
                dialogView.projectNameInput.error = null
                val description = dialogView.projectDescriptionInput.editText?.text?.toString().orEmpty().trim()
                val goals = dialogView.projectGoalsInput.editText?.text?.toString().orEmpty().trim()
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                viewModel.createProject(name, description, goals, selectedDueDate, uid)
                dialog.dismiss()
            }
        }
        dialog.show()
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
