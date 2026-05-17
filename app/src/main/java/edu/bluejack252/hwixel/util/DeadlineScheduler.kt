package edu.bluejack252.hwixel.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import edu.bluejack252.hwixel.data.work.DeadlineReminderWorker
import java.util.concurrent.TimeUnit

object DeadlineScheduler {

    fun schedule(context: Context, taskId: String, taskTitle: String, deadlineMs: Long) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag("deadline_$taskId")

        val now = System.currentTimeMillis()
        val intervals = listOf(
            "24h" to deadlineMs - 24 * 60 * 60 * 1000L - now,
            "1h" to deadlineMs - 60 * 60 * 1000L - now,
            "15m" to deadlineMs - 15 * 60 * 1000L - now
        )

        intervals.forEach { (label, delay) ->
            if (delay > 0) {
                val request = OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            DeadlineReminderWorker.KEY_TASK_ID to taskId,
                            DeadlineReminderWorker.KEY_TASK_TITLE to taskTitle,
                            DeadlineReminderWorker.KEY_LABEL to label
                        )
                    )
                    .addTag("deadline_$taskId")
                    .build()
                wm.enqueueUniqueWork(
                    "deadline_${taskId}_$label",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }
    }

    fun cancel(context: Context, taskId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("deadline_$taskId")
    }
}
