package edu.bluejack252.hwixel.ui.project.attendance

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
        observeViewModel(view)
    }

    private fun setupAdapters(view: View) {
        sessionAdapter = AttendanceSessionAdapter { session ->
            viewModel.selectSession(session)
        }
        view.findViewById<RecyclerView>(R.id.sessionsRecyclerView).adapter = sessionAdapter

        memberAdapter = AttendanceMemberAdapter(mutableListOf()) { userId, present ->
            val sessions = allSessions
            val selectedSessionId = sessions.firstOrNull()?.id
            selectedSessionId?.let {
                viewModel.toggleAttendance(it, userId, present)
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
                viewModel.scheduleReminder(requireContext(), projectName, nextDate)
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
                    val items = state.memberNames.map { (userId, name) ->
                        AttendanceMemberItem(
                            userId = userId,
                            name = name,
                            isPresent = state.memberAttendance[userId] ?: false,
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
                    Snackbar.make(view, getString(R.string.attendance_save_success), Snackbar.LENGTH_SHORT).show()
                }
                is AttendanceUiState.Error -> {
                    loading.isVisible = false
                    val msg = if (state.message == "lead_only")
                        getString(R.string.attendance_lead_only)
                    else
                        state.message.ifBlank { getString(R.string.error_generic) }
                    Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
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
            showNextSessionPicker(dateMs)
        }
        picker.show(parentFragmentManager, "session_date_picker")
    }

    private fun showNextSessionPicker(sessionDate: Long) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.attendance_dialog_set_next_date))
            .build()

        picker.addOnPositiveButtonClickListener { nextDateMs ->
            viewModel.createSession(sessionDate, nextDateMs)
        }
        picker.addOnNegativeButtonClickListener {
            viewModel.createSession(sessionDate, 0L)
        }
        picker.show(parentFragmentManager, "next_session_picker")
    }
}
