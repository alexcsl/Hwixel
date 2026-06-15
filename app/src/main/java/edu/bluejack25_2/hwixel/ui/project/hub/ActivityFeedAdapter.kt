package edu.bluejack25_2.hwixel.ui.project.hub

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack25_2.hwixel.databinding.ItemActivityFeedBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityFeedAdapter :
    ListAdapter<ActivityUi, ActivityFeedAdapter.ActivityViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        return ActivityViewHolder(
            ItemActivityFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ActivityViewHolder(
        private val binding: ItemActivityFeedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

        fun bind(item: ActivityUi) {
            binding.feedActionTextView.text = "${item.actorName}: ${item.action}"
            binding.feedTimeTextView.text = dateFormat.format(Date(item.timestamp))
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ActivityUi>() {
        override fun areItemsTheSame(oldItem: ActivityUi, newItem: ActivityUi) =
            oldItem.timestamp == newItem.timestamp && oldItem.actorName == newItem.actorName

        override fun areContentsTheSame(oldItem: ActivityUi, newItem: ActivityUi) =
            oldItem == newItem
    }
}
