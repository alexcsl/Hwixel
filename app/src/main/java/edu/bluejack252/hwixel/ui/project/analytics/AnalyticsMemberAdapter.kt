package edu.bluejack252.hwixel.ui.project.analytics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.databinding.ItemAnalyticsMemberBinding

class AnalyticsMemberAdapter(
    private val onMemberClick: (MemberAnalyticsUi) -> Unit
) : ListAdapter<MemberAnalyticsUi, AnalyticsMemberAdapter.MemberViewHolder>(DiffCallback) {

    var selectedMemberId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        return MemberViewHolder(
            ItemAnalyticsMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1, getItem(position).userId == selectedMemberId, onMemberClick)
    }

    class MemberViewHolder(
        private val binding: ItemAnalyticsMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: MemberAnalyticsUi,
            rank: Int,
            selected: Boolean,
            onMemberClick: (MemberAnalyticsUi) -> Unit
        ) {
            val ctx = binding.root.context
            binding.memberNameTextView.text = ctx.getString(
                R.string.analytics_member_rank_format,
                rank,
                item.name
            )
            binding.memberBreakdownTextView.text = ctx.getString(
                R.string.analytics_member_breakdown_format,
                item.assignedCount,
                item.completedCount,
                item.overdueCount
            )
            binding.memberScoreTextView.text = ctx.getString(
                R.string.analytics_score_format,
                item.contributionScore
            )
            binding.memberProgressIndicator.progress = (item.completionRatio * 100).toInt()
            binding.memberScoreTextView.setChipBackgroundColorResource(scoreColor(item.contributionScore))
            binding.root.strokeWidth = if (selected) 4 else 1
            binding.root.strokeColor = ContextCompat.getColor(
                ctx,
                if (selected) R.color.analytics_selected_stroke else R.color.analytics_card_stroke
            )
            binding.root.setOnClickListener { onMemberClick(item) }
        }

        private fun scoreColor(score: Float): Int {
            return when {
                score >= 80f -> R.color.analytics_healthy
                score >= 50f -> R.color.analytics_mild
                else -> R.color.analytics_severe
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MemberAnalyticsUi>() {
        override fun areItemsTheSame(oldItem: MemberAnalyticsUi, newItem: MemberAnalyticsUi): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: MemberAnalyticsUi, newItem: MemberAnalyticsUi): Boolean {
            return oldItem == newItem
        }
    }
}
