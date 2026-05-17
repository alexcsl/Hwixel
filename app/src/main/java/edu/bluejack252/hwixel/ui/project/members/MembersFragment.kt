package edu.bluejack252.hwixel.ui.project.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.databinding.DialogInviteMemberBinding
import edu.bluejack252.hwixel.databinding.FragmentMembersBinding
import edu.bluejack252.hwixel.ui.project.hub.ProjectPagerAdapter
import edu.bluejack252.hwixel.util.constants.Constants

class MembersFragment : Fragment() {

    private var _binding: FragmentMembersBinding? = null
    private val binding get() = _binding!!

    private val projectId: String by lazy {
        arguments?.getString(ProjectPagerAdapter.ARG_PROJECT_ID).orEmpty()
    }
    private val currentUserId: String by lazy {
        FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }

    private val adapter = MembersAdapter(
        onRoleClick = ::showRoleDialog,
        onStatusToggle = { member -> viewModel.toggleMemberStatus(member.userId) }
    )

    private val viewModel: MembersViewModel by viewModels {
        MembersViewModelFactory(
            projectId = projectId,
            currentUserId = currentUserId,
            projectRepository = ServiceLocator.getProjectRepository(requireContext()),
            userRepository = ServiceLocator.getUserRepository(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.membersRecyclerView.adapter = adapter
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.actionResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            val msgRes = when (result) {
                "invite_success" -> R.string.invite_success
                "user_not_found" -> R.string.invite_user_not_found
                "no_permission" -> R.string.error_no_permission
                "role_updated" -> R.string.member_role_updated
                "status_toggled" -> R.string.member_status_toggled
                else -> R.string.error_generic
            }
            Snackbar.make(binding.root, msgRes, Snackbar.LENGTH_SHORT).show()
            viewModel.consumeActionResult()
        }
        binding.inviteMemberButton.setOnClickListener { showInviteDialog() }
    }

    private fun render(state: MembersUiState) {
        adapter.isTeamLead = state.currentUserRole == Constants.ROLE_TEAM_LEAD
        adapter.submitList(state.members)
        binding.emptyMembersTextView.isVisible = state.members.isEmpty() && !state.isLoading
    }

    private fun showRoleDialog(member: MemberUi) {
        val roles = arrayOf(
            Constants.ROLE_TEAM_LEAD,
            Constants.ROLE_EDITOR,
            Constants.ROLE_RESEARCHER,
            Constants.ROLE_DESIGNER,
            Constants.ROLE_DEVELOPER,
            Constants.ROLE_OTHER
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.task_detail_edit)
            .setItems(roles) { _, which ->
                viewModel.updateMemberRole(member.userId, roles[which])
            }
            .show()
    }

    private fun showInviteDialog() {
        val dialogView = DialogInviteMemberBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.invite_dialog_title)
            .setView(dialogView.root)
            .setPositiveButton(R.string.btn_invite) { _, _ ->
                val email = dialogView.inviteEmailInput.editText?.text?.toString().orEmpty().trim()
                if (email.isNotBlank()) viewModel.inviteMember(email)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
