package edu.bluejack252.hwixel.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Notification
import edu.bluejack252.hwixel.util.constants.NotificationTypes

class NotificationsFragment : Fragment() {

    private val viewModel: NotificationsViewModel by viewModels {
        NotificationsViewModelFactory(
            repository = ServiceLocator.getNotificationRepository(),
            settingsRepository = ServiceLocator.getProfileSettingsRepository(requireContext()),
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        )
    }

    private lateinit var adapter: NotificationAdapter
    private var allNotifications: List<Notification> = emptyList()
    private var activeFilter: String = FILTER_ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_notifications, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotificationAdapter { notif -> handleNotifTap(notif) }
        view.findViewById<RecyclerView>(R.id.notificationsRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NotificationsFragment.adapter
        }

        val markAllButton = view.findViewById<MaterialButton>(R.id.markAllReadButton)
        markAllButton.setOnClickListener { viewModel.markAllRead() }

        val loading = view.findViewById<LinearProgressIndicator>(R.id.notifLoadingIndicator)
        val emptyText = view.findViewById<TextView>(R.id.emptyNotificationsTextView)
        val unreadBadge = view.findViewById<TextView>(R.id.unreadCountBadge)

        val filterChipGroup = view.findViewById<ChipGroup>(R.id.notifFilterChipGroup)
        filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            activeFilter = when (checkedIds.firstOrNull()) {
                R.id.chipFilterMentions -> NotificationTypes.TYPE_MENTION
                R.id.chipFilterDeadlines -> NotificationTypes.TYPE_DEADLINE
                R.id.chipFilterReviews -> FILTER_REVIEWS
                else -> FILTER_ALL
            }
            applyFilter(emptyText, markAllButton)
        }

        viewModel.notifications.observe(viewLifecycleOwner) { list ->
            allNotifications = list
            applyFilter(emptyText, markAllButton)
        }

        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                unreadBadge.isVisible = true
                unreadBadge.text = count.toString()
            } else {
                unreadBadge.isVisible = false
            }
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is NotificationsUiState.Loading -> loading.isVisible = true
                is NotificationsUiState.MarkedAllRead -> {
                    loading.isVisible = false
                    Snackbar.make(view, getString(R.string.notif_marked_all_read), Snackbar.LENGTH_SHORT).show()
                }
                is NotificationsUiState.Error -> {
                    loading.isVisible = false
                    Snackbar.make(view, getString(R.string.error_generic), Snackbar.LENGTH_SHORT).show()
                }
                else -> loading.isVisible = false
            }
        }
    }

    private fun applyFilter(emptyText: TextView, markAllButton: MaterialButton) {
        val filtered = when (activeFilter) {
            NotificationTypes.TYPE_MENTION -> allNotifications.filter { it.type == NotificationTypes.TYPE_MENTION }
            NotificationTypes.TYPE_DEADLINE -> allNotifications.filter { it.type == NotificationTypes.TYPE_DEADLINE }
            FILTER_REVIEWS -> allNotifications.filter {
                it.type == NotificationTypes.TYPE_EVAL_OPEN || it.type == NotificationTypes.TYPE_EVAL_CLOSE
            }
            else -> allNotifications
        }
        adapter.submitList(filtered)
        emptyText.isVisible = filtered.isEmpty()
        markAllButton.isVisible = allNotifications.any { !it.isRead }
    }

    companion object {
        private const val FILTER_ALL = "all"
        private const val FILTER_REVIEWS = "reviews"
    }

    private fun handleNotifTap(notif: Notification) {
        viewModel.markRead(notif.id)
        when (notif.type) {
            NotificationTypes.TYPE_TASK_ASSIGNED, NotificationTypes.TYPE_MENTION, NotificationTypes.TYPE_DEADLINE -> {
                val parts = notif.referenceId.split("|")
                if (parts.size == 2) {
                    val action = NotificationsFragmentDirections
                        .actionNotificationsFragmentToTaskDetailFragment(
                            projectId = parts[0],
                            taskId = parts[1]
                        )
                    findNavController().navigate(action)
                }
            }
            NotificationTypes.TYPE_EVAL_OPEN, NotificationTypes.TYPE_EVAL_CLOSE, NotificationTypes.TYPE_INVITE -> {
                val action = NotificationsFragmentDirections
                    .actionNotificationsFragmentToProjectHubFragment(
                        projectId = notif.referenceId
                    )
                findNavController().navigate(action)
            }
        }
    }
}
