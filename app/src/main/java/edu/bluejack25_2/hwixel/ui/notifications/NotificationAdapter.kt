package edu.bluejack25_2.hwixel.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.data.model.Notification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationAdapter(
    private val onClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.notifIcon)
        val message: TextView = view.findViewById(R.id.notifMessageTextView)
        val timestamp: TextView = view.findViewById(R.id.notifTimestampTextView)
        val unreadDot: View = view.findViewById(R.id.notifUnreadDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notif = getItem(position)
        holder.message.text = notif.message
        holder.timestamp.text = formatTime(notif.timestamp)
        holder.unreadDot.isVisible = !notif.isRead
        holder.icon.setImageResource(
            when (notif.type) {
                "task_assigned" -> R.drawable.ic_check_circle
                "mention" -> R.drawable.ic_person
                "deadline" -> R.drawable.ic_reminder
                "eval_open", "eval_close" -> R.drawable.ic_attendance
                "invite" -> R.drawable.ic_person_add
                else -> R.drawable.ic_notifications_24
            }
        )
        holder.itemView.alpha = if (notif.isRead) 0.65f else 1f
        holder.itemView.setOnClickListener { onClick(notif) }
    }

    private fun formatTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(old: Notification, new: Notification) = old.id == new.id
        override fun areContentsTheSame(old: Notification, new: Notification) = old == new
    }
}
