package edu.bluejack252.hwixel.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Notification
import edu.bluejack252.hwixel.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val adapter = NotificationsAdapter(::handleNotifTap)

    private val viewModel: NotificationsViewModel by viewModels {
        NotificationsViewModelFactory(ServiceLocator.getNotificationRepository())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.notificationsRecyclerView.adapter = adapter
        binding.markAllReadButton.setOnClickListener { viewModel.markAllRead() }
        viewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            adapter.submitList(notifications)
            binding.emptyNotificationsTextView.isVisible = notifications.isEmpty()
            binding.markAllReadButton.isVisible = notifications.any { !it.isRead }
        }
        viewModel.load(FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
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
