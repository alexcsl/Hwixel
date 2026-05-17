package edu.bluejack252.hwixel.ui.project.attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.model.AttendanceSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceSessionAdapter(
    private val onSessionClick: (AttendanceSession) -> Unit
) : ListAdapter<AttendanceSession, AttendanceSessionAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<AttendanceSession>() {
        override fun areItemsTheSame(a: AttendanceSession, b: AttendanceSession) = a.id == b.id
        override fun areContentsTheSame(a: AttendanceSession, b: AttendanceSession) = a == b
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateView: TextView = itemView.findViewById(R.id.sessionDateTextView)
        private val summaryView: TextView = itemView.findViewById(R.id.sessionSummaryTextView)

        fun bind(session: AttendanceSession) {
            val fmt = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault())
            dateView.text = fmt.format(Date(session.date))
            val presentCount = session.records.values.count { it }
            val totalCount = session.records.size
            summaryView.text = itemView.context.getString(
                R.string.attendance_session_summary_format,
                presentCount,
                totalCount
            )
            itemView.setOnClickListener { onSessionClick(session) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
