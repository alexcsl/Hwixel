package edu.bluejack25_2.hwixel.ui.project.taskdetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack25_2.hwixel.data.model.Subtask
import edu.bluejack25_2.hwixel.databinding.ItemSubtaskToggleBinding

class SubtaskToggleAdapter(
    private val onToggle: (Subtask, Boolean) -> Unit
) : ListAdapter<Subtask, SubtaskToggleAdapter.SubtaskViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtaskViewHolder {
        return SubtaskViewHolder(
            ItemSubtaskToggleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SubtaskViewHolder, position: Int) {
        holder.bind(getItem(position), onToggle)
    }

    class SubtaskViewHolder(
        private val binding: ItemSubtaskToggleBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Subtask, onToggle: (Subtask, Boolean) -> Unit) {
            binding.subtaskCheckBox.setOnCheckedChangeListener(null)
            binding.subtaskCheckBox.text = item.title
            binding.subtaskCheckBox.isChecked = item.isDone
            binding.subtaskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item, isChecked)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Subtask>() {
        override fun areItemsTheSame(oldItem: Subtask, newItem: Subtask): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Subtask, newItem: Subtask): Boolean {
            return oldItem == newItem
        }
    }
}
