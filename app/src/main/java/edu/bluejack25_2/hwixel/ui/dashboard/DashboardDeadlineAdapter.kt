package edu.bluejack25_2.hwixel.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.databinding.ItemDashboardDeadlineBinding

class DashboardDeadlineAdapter :
    ListAdapter<DashboardDeadlineUi, DashboardDeadlineAdapter.DeadlineViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeadlineViewHolder {
        return DeadlineViewHolder(
            ItemDashboardDeadlineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: DeadlineViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeadlineViewHolder(
        private val binding: ItemDashboardDeadlineBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DashboardDeadlineUi) {
            val context = binding.root.context
            binding.taskTitleTextView.text = item.taskTitle
            binding.projectNameTextView.text = item.projectName
            binding.countdownTextView.text = context.getString(
                R.string.dashboard_countdown_format,
                item.countdown.days,
                item.countdown.hours,
                item.countdown.minutes,
                item.countdown.seconds
            )
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DashboardDeadlineUi>() {
        override fun areItemsTheSame(oldItem: DashboardDeadlineUi, newItem: DashboardDeadlineUi): Boolean {
            return oldItem.taskId == newItem.taskId
        }

        override fun areContentsTheSame(oldItem: DashboardDeadlineUi, newItem: DashboardDeadlineUi): Boolean {
            return oldItem == newItem
        }
    }
}
