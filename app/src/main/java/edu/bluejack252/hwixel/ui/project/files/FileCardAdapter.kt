package edu.bluejack252.hwixel.ui.project.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.model.FileLink

class FileCardAdapter(
    private val onOpen: (FileLink) -> Unit,
    private val onDelete: (FileLink) -> Unit,
    private val showDelete: Boolean = true
) : ListAdapter<FileLink, FileCardAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeIcon: ImageView = view.findViewById(R.id.fileTypeIcon)
        val labelText: TextView = view.findViewById(R.id.fileLabelTextView)
        val urlText: TextView = view.findViewById(R.id.fileUrlTextView)
        val notesText: TextView = view.findViewById(R.id.fileVersionNotesTextView)
        val deleteButton: ImageButton = view.findViewById(R.id.fileDeleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_file_card, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = getItem(position)
        holder.labelText.text = file.label
        holder.urlText.text = file.url
        if (file.versionNotes.isNotBlank()) {
            holder.notesText.visibility = View.VISIBLE
            holder.notesText.text = file.versionNotes
        } else {
            holder.notesText.visibility = View.GONE
        }
        holder.typeIcon.setImageResource(
            when (file.type) {
                "drive" -> R.drawable.ic_drive
                "github" -> R.drawable.ic_github
                else -> R.drawable.ic_link
            }
        )
        holder.deleteButton.visibility = if (showDelete) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onOpen(file) }
        holder.deleteButton.setOnClickListener { onDelete(file) }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FileLink>() {
        override fun areItemsTheSame(old: FileLink, new: FileLink) = old.id == new.id
        override fun areContentsTheSame(old: FileLink, new: FileLink) = old == new
    }
}
