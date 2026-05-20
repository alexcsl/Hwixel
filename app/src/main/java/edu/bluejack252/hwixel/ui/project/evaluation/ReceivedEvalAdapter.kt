package edu.bluejack252.hwixel.ui.project.evaluation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.model.EvaluationSubmission
import kotlin.math.round

class ReceivedEvalAdapter(
    private val memberNames: () -> Map<String, String>
) : ListAdapter<EvaluationSubmission, ReceivedEvalAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<EvaluationSubmission>() {
        override fun areItemsTheSame(a: EvaluationSubmission, b: EvaluationSubmission) = a.id == b.id
        override fun areContentsTheSame(a: EvaluationSubmission, b: EvaluationSubmission) = a == b
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fromLabel: TextView = itemView.findViewById(R.id.evalFromTextView)
        val avgScore: TextView = itemView.findViewById(R.id.evalAvgScoreTextView)
        val ratingBar: RatingBar = itemView.findViewById(R.id.evalRatingBar)
        val feedbackText: TextView = itemView.findViewById(R.id.evalFeedbackTextView)
        val commScore: TextView = itemView.findViewById(R.id.evalCommScoreTextView)
        val qualScore: TextView = itemView.findViewById(R.id.evalQualScoreTextView)
        val relScore: TextView = itemView.findViewById(R.id.evalRelScoreTextView)
        val effortScore: TextView = itemView.findViewById(R.id.evalEffortScoreTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_eval_received, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sub = getItem(position)
        val ctx = holder.itemView.context

        // Show "Anonymous" per academic norms, but allow name if desired
        holder.fromLabel.text = ctx.getString(R.string.eval_from_anonymous)

        val avg = round(((sub.communication + sub.quality + sub.reliability + sub.effort) / 4f) * 10f) / 10f
        holder.avgScore.text = ctx.getString(R.string.eval_avg_format, avg)
        holder.ratingBar.rating = avg
        holder.feedbackText.text = sub.feedback.ifBlank { ctx.getString(R.string.eval_no_feedback) }

        holder.commScore.text = ctx.getString(R.string.eval_score_format, sub.communication)
        holder.qualScore.text = ctx.getString(R.string.eval_score_format, sub.quality)
        holder.relScore.text = ctx.getString(R.string.eval_score_format, sub.reliability)
        holder.effortScore.text = ctx.getString(R.string.eval_score_format, sub.effort)
    }
}
