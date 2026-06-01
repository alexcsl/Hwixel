package edu.bluejack252.hwixel.ui.project.attendance

import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ShareCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.AttendanceSession
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.util.constants.Constants
import java.util.Calendar

class AttendanceFragment : Fragment() {

    private val args: AttendanceFragmentArgs by navArgs()

    private val viewModel: AttendanceViewModel by viewModels {
        AttendanceViewModelFactory(
            repository = ServiceLocator.getAttendanceRepository(),
            projectId = args.projectId
        )
    }

    private lateinit var sessionAdapter: AttendanceSessionAdapter
    private lateinit var memberAdapter: AttendanceMemberAdapter

    private var projectName: String = ""
    private var allSessions: List<AttendanceSession> = emptyList()
    private var projectMembers: List<ProjectMember> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_attendance, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        projectName = args.projectName
        setupAdapters(view)
        setupClickListeners(view)
        observeProjectMembers(view)
        observeViewModel(view)
    }

    private fun observeProjectMembers(view: View) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        var project: Project? = null
        var users: List<User> = emptyList()

        fun publishMembers() {
            val currentProject = project ?: return
            val activeMembers = currentProject.members.mapNotNull { (userId, member) ->
                if (member.status == Constants.MEMBER_STATUS_INACTIVE) return@mapNotNull null
                member.copy(userId = member.userId.ifBlank { userId })
            }.toMutableList()
            if (
                currentProject.createdBy.isNotBlank() &&
                activeMembers.none { member -> member.userId == currentProject.createdBy }
            ) {
                activeMembers.add(
                    0,
                    ProjectMember(
                        userId = currentProject.createdBy,
                        role = Constants.ROLE_TEAM_LEAD,
                        status = Constants.MEMBER_STATUS_ACTIVE
                    )
                )
            }
            projectMembers = activeMembers.distinctBy { member -> member.userId }
            val names = projectMembers.associate { member ->
                val user = users.firstOrNull { it.id == member.userId }
                member.userId to (user?.name?.takeIf { it.isNotBlank() } ?: member.userId)
            }
            val currentRole = currentProject.members[currentUserId]?.role
                ?.takeIf { it.isNotBlank() }
                ?: Constants.ROLE_TEAM_LEAD.takeIf { currentProject.createdBy == currentUserId }
                ?: ""
            val isLead = currentRole == Constants.ROLE_TEAM_LEAD
            sessionAdapter.totalMemberCount = projectMembers.size
            view.findViewById<MaterialButton>(R.id.addSessionButton).isVisible = isLead
            view.findViewById<MaterialButton>(R.id.setReminderButton).isVisible = isLead
            viewModel.setProjectMembers(projectMembers, names, currentRole)
        }

        ServiceLocator.getProjectRepository(requireContext())
            .observeProject(args.projectId)
            .observe(viewLifecycleOwner) { value ->
                project = value
                publishMembers()
        }
        ServiceLocator.getUserRepository(requireContext())
            .observeUsers()
            .observe(viewLifecycleOwner) { value ->
                users = value
                publishMembers()
            }
    }

    private fun setupAdapters(view: View) {
        sessionAdapter = AttendanceSessionAdapter { session ->
            viewModel.toggleSessionSelection(session)
        }
        view.findViewById<RecyclerView>(R.id.sessionsRecyclerView).adapter = sessionAdapter

        memberAdapter = AttendanceMemberAdapter(mutableListOf()) { userId, present ->
            viewModel.selectedSessionId()?.let { sessionId ->
                viewModel.toggleAttendance(sessionId, userId, present)
            }
        }
        view.findViewById<RecyclerView>(R.id.membersAttendanceRecyclerView).adapter = memberAdapter
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<View>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<MaterialButton>(R.id.addSessionButton).setOnClickListener {
            showCreateSessionPicker()
        }

        view.findViewById<MaterialButton>(R.id.setReminderButton).setOnClickListener {
            val nextDate = allSessions.maxByOrNull { it.nextSessionDate }?.nextSessionDate ?: 0L
            if (nextDate > System.currentTimeMillis()) {
                viewModel.scheduleReminder(requireContext().applicationContext, projectName, nextDate)
            } else {
                Snackbar.make(view, getString(R.string.attendance_no_next_session), Snackbar.LENGTH_SHORT).show()
            }
        }

        view.findViewById<MaterialButton>(R.id.exportButton).setOnClickListener {
            val text = viewModel.buildExportText(allSessions, projectName)
            ShareCompat.IntentBuilder(requireActivity())
                .setType("text/plain")
                .setSubject(getString(R.string.attendance_export_title))
                .setText(text)
                .startChooser()
        }
    }

    private fun observeViewModel(view: View) {
        val loading = view.findViewById<LinearProgressIndicator>(R.id.loadingIndicator)
        val emptyView = view.findViewById<TextView>(R.id.emptySessionsTextView)
        val nextSessionText = view.findViewById<TextView>(R.id.nextSessionDateTextView)
        val memberSection = view.findViewById<LinearLayout>(R.id.memberSectionContainer)
        val sessionLabel = view.findViewById<TextView>(R.id.selectedSessionLabel)

        viewModel.sessions.observe(viewLifecycleOwner) { sessions ->
            allSessions = sessions
            sessionAdapter.submitList(sessions)
            emptyView.isVisible = sessions.isEmpty()

            val latestNext = sessions.maxByOrNull { it.nextSessionDate }?.nextSessionDate ?: 0L
            nextSessionText.text = if (latestNext > System.currentTimeMillis()) {
                java.text.SimpleDateFormat("EEEE, d MMM yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(latestNext))
            } else {
                getString(R.string.attendance_no_next_session)
            }
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AttendanceUiState.Loading -> loading.isVisible = true
                is AttendanceUiState.SessionSelected -> {
                    loading.isVisible = false
                    memberSection.isVisible = true
                    val fmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                    sessionLabel.text = getString(
                        R.string.attendance_session_title_format,
                        fmt.format(java.util.Date(state.session.date))
                    )

                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val isLead = viewModel.isCurrentUserLead(currentUserId)
                    val items = projectMembers.map { member ->
                        val userId = member.userId
                        val name = state.memberNames[userId] ?: userId
                        AttendanceMemberItem(
                            userId = userId,
                            name = name,
                            isPresent = state.memberAttendance[userId],
                            attendancePercentage = viewModel.computeMemberAttendancePercentage(allSessions, userId),
                            isEditable = isLead
                        )
                    }
                    memberAdapter.updateItems(items)
                }
                is AttendanceUiState.AttendanceSaved -> {
                    loading.isVisible = false
                    Snackbar.make(view, getString(R.string.attendance_save_success), Snackbar.LENGTH_SHORT).show()
                }
                is AttendanceUiState.ReminderSet -> {
                    Snackbar.make(view, getString(R.string.attendance_reminder_set), Snackbar.LENGTH_SHORT).show()
                }
                is AttendanceUiState.SessionCreated -> {
                    loading.isVisible = false
                    Snackbar.make(view, getString(R.string.attendance_session_created), Snackbar.LENGTH_SHORT).show()
                }
                is AttendanceUiState.Error -> {
                    loading.isVisible = false
                    val msg = if (state.message == "lead_only")
                        getString(R.string.attendance_lead_only)
                    else
                        state.message.ifBlank { getString(R.string.error_generic) }
                    Snackbar.make(view, msg, Snackbar.LENGTH_LONG).show()
                }
                AttendanceUiState.Idle -> {
                    loading.isVisible = false
                    memberSection.isVisible = false
                }
                else -> loading.isVisible = false
            }
        }
    }

    private fun showCreateSessionPicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.attendance_dialog_set_date))
            .build()

        picker.addOnPositiveButtonClickListener { dateMs ->
            showTimePicker(dateMs) { sessionDateTime ->
                showNextSessionPicker(sessionDateTime)
            }
        }
        picker.show(childFragmentManager, "session_date_picker")
    }

    private fun showTimePicker(dateMs: Long, onTimeSelected: (Long) -> Unit) {
        val initial = Calendar.getInstance()
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(initial.get(Calendar.HOUR_OF_DAY))
            .setMinute(initial.get(Calendar.MINUTE))
            .setTitleText(getString(R.string.attendance_dialog_set_time))
            .build()
        picker.addOnPositiveButtonClickListener {
            val selected = Calendar.getInstance().apply {
                timeInMillis = dateMs
                set(Calendar.HOUR_OF_DAY, picker.hour)
                set(Calendar.MINUTE, picker.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onTimeSelected(selected.timeInMillis)
        }
        picker.show(childFragmentManager, "session_time_picker")
    }

    private fun showNextSessionPicker(sessionDate: Long) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.attendance_dialog_set_next_date))
            .build()

        picker.addOnPositiveButtonClickListener { nextDateMs ->
            showTimePicker(nextDateMs) { nextDateTime ->
                viewModel.createSession(sessionDate, nextDateTime)
            }
        }
        picker.show(parentFragmentManager, "next_session_picker")
    }
}
