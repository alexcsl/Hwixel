package edu.bluejack25_2.hwixel.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.databinding.ItemDashboardProjectBinding
import kotlin.math.roundToInt

class DashboardProjectAdapter(
    private val onProjectClick: (DashboardProjectUi) -> Unit
) : ListAdapter<DashboardProjectUi, DashboardProjectAdapter.ProjectViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        return ProjectViewHolder(
            ItemDashboardProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(getItem(position), onProjectClick)
    }

    class ProjectViewHolder(
        private val binding: ItemDashboardProjectBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DashboardProjectUi, onProjectClick: (DashboardProjectUi) -> Unit) {
            val context = binding.root.context
            binding.projectNameTextView.text = item.name
            binding.projectRoleTextView.text = context.getString(
                R.string.dashboard_project_role_format,
                item.role.ifBlank { context.getString(R.string.dashboard_unknown_role) }
            )
            binding.projectCompletionTextView.text = context.getString(
                R.string.dashboard_project_completion_format,
                item.completionPercentage
            )
            binding.projectProgressIndicator.progress = item.completionPercentage.roundToInt().coerceIn(0, 100)
            binding.root.setOnClickListener { onProjectClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DashboardProjectUi>() {
        override fun areItemsTheSame(oldItem: DashboardProjectUi, newItem: DashboardProjectUi): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DashboardProjectUi, newItem: DashboardProjectUi): Boolean {
            return oldItem == newItem
        }
    }
}
