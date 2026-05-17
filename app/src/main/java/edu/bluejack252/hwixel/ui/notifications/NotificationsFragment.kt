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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Notification

class NotificationsFragment : Fragment() {

    private val viewModel: NotificationsViewModel by viewModels {
        NotificationsViewModelFactory(
            repository = ServiceLocator.getNotificationRepository(),
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        )
    }

    private lateinit var adapter: NotificationAdapter

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

        view.findViewById<MaterialButton>(R.id.markAllReadButton).setOnClickListener {
            viewModel.markAllRead()
        }

        val loading = view.findViewById<LinearProgressIndicator>(R.id.notifLoadingIndicator)
        val emptyText = view.findViewById<TextView>(R.id.emptyNotificationsTextView)
        val unreadBadge = view.findViewById<TextView>(R.id.unreadCountBadge)

        viewModel.notifications.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            emptyText.isVisible = list.isEmpty()
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

    private fun handleNotifTap(notif: Notification) {
        viewModel.markRead(notif.id)
        when (notif.type) {
            "task_assigned", "mention", "deadline" -> {
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
            "eval_open", "eval_close", "invite" -> {
                val action = NotificationsFragmentDirections
                    .actionNotificationsFragmentToProjectHubFragment(
                        projectId = notif.referenceId
                    )
                findNavController().navigate(action)
            }
        }
    }
}
