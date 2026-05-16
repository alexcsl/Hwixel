package edu.bluejack252.hwixel.ui.project.members

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.databinding.ItemMemberCardBinding
import edu.bluejack252.hwixel.util.constants.Constants

class MembersAdapter(
    private val onRoleClick: (MemberUi) -> Unit,
    private val onStatusToggle: (MemberUi) -> Unit
) : ListAdapter<MemberUi, MembersAdapter.MemberViewHolder>(DiffCallback) {

    var isTeamLead: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        return MemberViewHolder(
            ItemMemberCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position), isTeamLead, onRoleClick, onStatusToggle)
    }

    class MemberViewHolder(
        private val binding: ItemMemberCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: MemberUi,
            isTeamLead: Boolean,
            onRoleClick: (MemberUi) -> Unit,
            onStatusToggle: (MemberUi) -> Unit
        ) {
            val ctx = binding.root.context
            binding.memberNameTextView.text = item.name
            binding.memberRoleTextView.text = ctx.getString(R.string.member_role_label, item.role)
            binding.memberScoreTextView.text = ctx.getString(R.string.member_score_label, item.contributionScore)

            val isActive = item.status == Constants.MEMBER_STATUS_ACTIVE
            binding.memberStatusChip.text = if (isActive)
                ctx.getString(R.string.member_status_active)
            else
                ctx.getString(R.string.member_status_inactive)

            binding.memberCard.alpha = if (isActive) 1f else 0.5f

            binding.memberRoleTextView.setOnClickListener {
                if (isTeamLead) onRoleClick(item)
            }

            binding.memberStatusChip.setOnClickListener {
                if (isTeamLead) onStatusToggle(item)
            }

            val hasWhatsapp = item.phone.isNotBlank()
            binding.whatsappButton.isVisible = hasWhatsapp
            if (hasWhatsapp) {
                binding.whatsappButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${item.phone}"))
                    ctx.startActivity(intent)
                }
            }

            val hasEmail = item.email.isNotBlank()
            binding.emailButton.isVisible = hasEmail
            if (hasEmail) {
                binding.emailButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:${item.email}"))
                    ctx.startActivity(intent)
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MemberUi>() {
        override fun areItemsTheSame(oldItem: MemberUi, newItem: MemberUi) =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: MemberUi, newItem: MemberUi) = oldItem == newItem
    }
}
