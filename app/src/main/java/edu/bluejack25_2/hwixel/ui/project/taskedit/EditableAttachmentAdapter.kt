package edu.bluejack25_2.hwixel.ui.project.taskedit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack25_2.hwixel.data.model.Attachment
import edu.bluejack25_2.hwixel.databinding.ItemAttachmentBinding

class EditableAttachmentAdapter(
    private val onRemove: (Attachment) -> Unit
) : ListAdapter<Attachment, EditableAttachmentAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemAttachmentBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onRemove
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAttachmentBinding,
        private val onRemove: (Attachment) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Attachment) {
            binding.attachmentChip.text = item.label.ifBlank { item.url }
            binding.attachmentChip.isCloseIconVisible = true
            binding.attachmentChip.setOnCloseIconClickListener { onRemove(item) }
            binding.attachmentChip.setOnClickListener(null)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Attachment>() {
        override fun areItemsTheSame(oldItem: Attachment, newItem: Attachment): Boolean {
            return oldItem.id == newItem.id && oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: Attachment, newItem: Attachment): Boolean {
            return oldItem == newItem
        }
    }
}
