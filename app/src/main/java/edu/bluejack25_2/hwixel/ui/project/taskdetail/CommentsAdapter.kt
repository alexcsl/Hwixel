package edu.bluejack25_2.hwixel.ui.project.taskdetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack25_2.hwixel.databinding.ItemCommentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentsAdapter : ListAdapter<CommentUi, CommentsAdapter.CommentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        return CommentViewHolder(
            ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

        fun bind(comment: CommentUi) {
            binding.authorInitialTextView.text = comment.authorName.firstOrNull()?.uppercase() ?: "?"
            binding.authorNameTextView.text = comment.authorName
            binding.commentTextView.text = comment.content
            binding.timestampTextView.text = dateFormat.format(Date(comment.timestamp))
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CommentUi>() {
        override fun areItemsTheSame(oldItem: CommentUi, newItem: CommentUi) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CommentUi, newItem: CommentUi) = oldItem == newItem
    }
}
