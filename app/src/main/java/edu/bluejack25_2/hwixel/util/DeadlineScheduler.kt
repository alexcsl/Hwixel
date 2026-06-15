package edu.bluejack25_2.hwixel.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import edu.bluejack25_2.hwixel.data.work.DeadlineReminderWorker
import java.util.concurrent.TimeUnit

object DeadlineScheduler {

    fun schedule(context: Context, taskId: String, taskTitle: String, deadlineMs: Long) {
        schedule(
            taskId = taskId,
            taskTitle = taskTitle,
            deadlineMs = deadlineMs,
            nowMs = System.currentTimeMillis(),
            scheduler = WorkManagerDeadlineScheduler(context)
        )
    }

    internal fun schedule(
        taskId: String,
        taskTitle: String,
        deadlineMs: Long,
        nowMs: Long,
        scheduler: DeadlineWorkScheduler
    ) {
        val tag = "deadline_$taskId"
        scheduler.cancelAllByTag(tag)

        buildSpecs(taskId, taskTitle, deadlineMs, nowMs).forEach { spec ->
            val delay = spec.delayMs
            if (delay > 0) {
                scheduler.enqueueUnique(spec)
            }
        }
    }

    fun cancel(context: Context, taskId: String) {
        cancel(taskId, WorkManagerDeadlineScheduler(context))
    }

    internal fun cancel(taskId: String, scheduler: DeadlineWorkScheduler) {
        scheduler.cancelAllByTag("deadline_$taskId")
    }

    internal fun buildSpecs(
        taskId: String,
        taskTitle: String,
        deadlineMs: Long,
        nowMs: Long
    ): List<DeadlineWorkSpec> {
        return listOf(
            "24h" to deadlineMs - 24 * 60 * 60 * 1000L - nowMs,
            "1h" to deadlineMs - 60 * 60 * 1000L - nowMs,
            "15m" to deadlineMs - 15 * 60 * 1000L - nowMs
        ).map { (label, delay) ->
            DeadlineWorkSpec(
                uniqueName = "deadline_${taskId}_$label",
                tag = "deadline_$taskId",
                taskId = taskId,
                taskTitle = taskTitle,
                label = label,
                delayMs = delay
            )
        }
    }

    private class WorkManagerDeadlineScheduler(context: Context) : DeadlineWorkScheduler {
        private val workManager = WorkManager.getInstance(context)

        override fun cancelAllByTag(tag: String) {
            workManager.cancelAllWorkByTag(tag)
        }

        override fun enqueueUnique(spec: DeadlineWorkSpec) {
            val request = OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
                .setInitialDelay(spec.delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        DeadlineReminderWorker.KEY_TASK_ID to spec.taskId,
                        DeadlineReminderWorker.KEY_TASK_TITLE to spec.taskTitle,
                        DeadlineReminderWorker.KEY_LABEL to spec.label
                    )
                )
                .addTag(spec.tag)
                .build()
            workManager.enqueueUniqueWork(
                spec.uniqueName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

internal interface DeadlineWorkScheduler {
    fun cancelAllByTag(tag: String)
    fun enqueueUnique(spec: DeadlineWorkSpec)
}

internal data class DeadlineWorkSpec(
    val uniqueName: String,
    val tag: String,
    val taskId: String,
    val taskTitle: String,
    val label: String,
    val delayMs: Long
)
