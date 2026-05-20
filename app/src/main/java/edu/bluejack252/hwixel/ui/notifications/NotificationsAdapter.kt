package edu.bluejack252.hwixel.ui.notifications

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.model.Notification
import edu.bluejack252.hwixel.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsAdapter(
    private val onClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationsAdapter.NotificationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(
        private val binding: ItemNotificationBinding,
        private val onClick: (Notification) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

        fun bind(item: Notification) {
            binding.notificationTitleTextView.setText(
                if (item.type == TYPE_INVITE) R.string.notification_invite_title else R.string.nav_notifications
            )
            binding.notificationMessageTextView.text = item.message
            binding.notificationTimeTextView.text = if (item.timestamp > 0L) {
                dateFormat.format(Date(item.timestamp))
            } else {
                ""
            }
            val style = if (item.isRead) Typeface.NORMAL else Typeface.BOLD
            binding.notificationTitleTextView.setTypeface(null, style)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        const val TYPE_INVITE = "invite"
    }
}
