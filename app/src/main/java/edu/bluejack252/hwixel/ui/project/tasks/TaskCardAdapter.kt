package edu.bluejack252.hwixel.ui.project.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.databinding.ItemTaskCardBinding
import edu.bluejack252.hwixel.util.constants.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class TaskCardAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onStatusChange: (Task) -> Unit
) : ListAdapter<Task, TaskCardAdapter.TaskViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        return TaskViewHolder(
            ItemTaskCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position), onTaskClick, onStatusChange)
    }

    class TaskViewHolder(
        private val binding: ItemTaskCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        fun bind(task: Task, onTaskClick: (Task) -> Unit, onStatusChange: (Task) -> Unit) {
            val ctx = binding.root.context
            binding.taskTitleTextView.text = task.title
            binding.statusChip.text = task.status.replace("_", " ").replaceFirstChar { it.uppercase() }
            binding.priorityChip.text = task.priority.replaceFirstChar { it.uppercase() }

            val priorityColor = when (task.priority) {
                Constants.PRIORITY_HIGH -> ctx.getColor(android.R.color.holo_red_light)
                Constants.PRIORITY_MEDIUM -> ctx.getColor(android.R.color.holo_orange_light)
                else -> ctx.getColor(android.R.color.holo_green_light)
            }
            binding.priorityChip.chipBackgroundColor =
                android.content.res.ColorStateList.valueOf(priorityColor)

            if (task.deadline > 0L) {
                binding.deadlineTextView.text = dateFormat.format(Date(task.deadline))
                binding.deadlineTextView.isVisible = true
            } else {
                binding.deadlineTextView.isVisible = false
            }

            val totalSubtasks = task.subtasks.size
            val doneSubtasks = task.subtasks.values.count { it.isDone }
            if (totalSubtasks > 0) {
                binding.subtaskProgressTextView.text = ctx.getString(
                    R.string.task_subtask_progress, doneSubtasks, totalSubtasks
                )
                binding.subtaskProgressBar.progress =
                    ((doneSubtasks.toFloat() / totalSubtasks) * 100).roundToInt()
                binding.subtaskProgressTextView.isVisible = true
                binding.subtaskProgressBar.isVisible = true
            } else {
                binding.subtaskProgressTextView.isVisible = false
                binding.subtaskProgressBar.isVisible = false
            }

            binding.taskCard.setOnClickListener { onTaskClick(task) }
            binding.statusChip.setOnClickListener { onStatusChange(task) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }
}
