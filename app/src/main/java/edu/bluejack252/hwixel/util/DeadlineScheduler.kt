package edu.bluejack252.hwixel.util

import android.content.Context
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
            Triple("24h", 24 * 60 * 60 * 1000L, deadlineMs - 24 * 60 * 60 * 1000L - now),
            Triple("1h",  60 * 60 * 1000L,       deadlineMs - 60 * 60 * 1000L - now),
            Triple("15m", 15 * 60 * 1000L,        deadlineMs - 15 * 60 * 1000L - now)
        )

        intervals.forEach { (label, _, delay) ->
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
                wm.enqueue(request)
            }
        }
    }

    fun cancel(context: Context, taskId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("deadline_$taskId")
    }
}
