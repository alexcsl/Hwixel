package edu.bluejack252.hwixel.ui.project.taskdetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack252.hwixel.databinding.ItemHistoryEntryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : ListAdapter<HistoryUi, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        return HistoryViewHolder(
            ItemHistoryEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        private val binding: ItemHistoryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

        fun bind(entry: HistoryUi) {
            binding.historyActionTextView.text = "${entry.actorName}: ${entry.action}"
            binding.historyTimestampTextView.text = dateFormat.format(Date(entry.timestamp))
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<HistoryUi>() {
        override fun areItemsTheSame(oldItem: HistoryUi, newItem: HistoryUi) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: HistoryUi, newItem: HistoryUi) =
            oldItem == newItem
    }
}
