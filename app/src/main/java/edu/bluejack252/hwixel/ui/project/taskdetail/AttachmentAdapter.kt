package edu.bluejack252.hwixel.ui.project.taskdetail

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack252.hwixel.data.model.Attachment
import edu.bluejack252.hwixel.databinding.ItemAttachmentBinding

class AttachmentAdapter(
    private val onOpenImage: (Attachment) -> Unit
) : ListAdapter<Attachment, AttachmentAdapter.AttachmentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        return AttachmentViewHolder(
            ItemAttachmentBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onOpenImage
        )
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AttachmentViewHolder(
        private val binding: ItemAttachmentBinding,
        private val onOpenImage: (Attachment) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Attachment) {
            binding.attachmentChip.text = item.label.ifBlank { item.url }
            binding.attachmentChip.setOnClickListener {
                if (item.type.equals(TYPE_IMAGE, ignoreCase = true)) {
                    onOpenImage(item)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                    binding.root.context.startActivity(intent)
                }
            }
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

    private companion object {
        const val TYPE_IMAGE = "image"
    }
}
