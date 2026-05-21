package edu.bluejack252.hwixel.ui.project.attendance

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import edu.bluejack252.hwixel.R

data class AttendanceMemberItem(
    val userId: String,
    val name: String,
    val isPresent: Boolean,
    val attendancePercentage: Float,
    val isEditable: Boolean
)

class AttendanceMemberAdapter(
    private val items: MutableList<AttendanceMemberItem>,
    private val onToggle: (userId: String, present: Boolean) -> Unit
) : RecyclerView.Adapter<AttendanceMemberAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val initialView: TextView = itemView.findViewById(R.id.memberInitialTextView)
        val nameView: TextView = itemView.findViewById(R.id.memberNameTextView)
        val percentageView: TextView = itemView.findViewById(R.id.attendancePercentageTextView)
        val presentButton: MaterialButton = itemView.findViewById(R.id.presentButton)
        val absentButton: MaterialButton = itemView.findViewById(R.id.absentButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.initialView.text = item.name.firstOrNull()?.uppercase() ?: "?"
        holder.nameView.text = item.name
        holder.percentageView.text = holder.itemView.context.getString(
            R.string.attendance_member_percentage_format,
            item.attendancePercentage
        )

        val ctx = holder.itemView.context
        val presentBg = if (item.isPresent) ctx.getColor(R.color.attendance_present) else ctx.getColor(R.color.glass_fill)
        val absentBg = if (!item.isPresent) ctx.getColor(R.color.attendance_absent) else ctx.getColor(R.color.glass_fill)
        holder.presentButton.backgroundTintList = ColorStateList.valueOf(presentBg)
        holder.absentButton.backgroundTintList = ColorStateList.valueOf(absentBg)
        holder.presentButton.setTextColor(ctx.getColor(if (item.isPresent) R.color.white else R.color.text_secondary))
        holder.absentButton.setTextColor(ctx.getColor(if (!item.isPresent) R.color.white else R.color.text_secondary))

        if (item.isEditable) {
            holder.presentButton.isEnabled = true
            holder.absentButton.isEnabled = true
            holder.presentButton.setOnClickListener { onToggle(item.userId, true) }
            holder.absentButton.setOnClickListener { onToggle(item.userId, false) }
        } else {
            holder.presentButton.isEnabled = false
            holder.absentButton.isEnabled = false
            holder.presentButton.setOnClickListener(null)
            holder.absentButton.setOnClickListener(null)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<AttendanceMemberItem>) {
        val callback = object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos].userId == newItems[newPos].userId
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos] == newItems[newPos]
        }
        val diff = DiffUtil.calculateDiff(callback)
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }
}
