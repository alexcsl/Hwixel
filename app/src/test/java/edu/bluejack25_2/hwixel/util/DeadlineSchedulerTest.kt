package edu.bluejack25_2.hwixel.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DeadlineSchedulerTest {

    @Test
    fun scheduleCancelsPriorJobsAndEnqueuesThreeReminderJobs() {
        val scheduler = FakeDeadlineWorkScheduler()
        val now = 1_000L
        val deadline = now + 25 * HOUR_MS

        DeadlineScheduler.schedule(
            taskId = "task-1",
            taskTitle = "Submit report",
            deadlineMs = deadline,
            nowMs = now,
            scheduler = scheduler
        )

        assertEquals(listOf("deadline_task-1"), scheduler.cancelledTags)
        assertEquals(listOf("24h", "1h", "15m"), scheduler.enqueued.map { it.label })
        assertEquals(
            listOf(
                "deadline_task-1_24h",
                "deadline_task-1_1h",
                "deadline_task-1_15m"
            ),
            scheduler.enqueued.map { it.uniqueName }
        )
        assertEquals(listOf("deadline_task-1"), scheduler.enqueued.map { it.tag }.distinct())
        assertEquals(listOf(HOUR_MS, 24 * HOUR_MS, 24 * HOUR_MS + 45 * MINUTE_MS), scheduler.enqueued.map { it.delayMs })
    }

    @Test
    fun rescheduleCancelsPriorJobsEveryTime() {
        val scheduler = FakeDeadlineWorkScheduler()
        val now = 1_000L
        val deadline = now + 25 * HOUR_MS

        DeadlineScheduler.schedule("task-1", "Submit report", deadline, now, scheduler)
        DeadlineScheduler.schedule("task-1", "Submit report", deadline, now, scheduler)

        assertEquals(listOf("deadline_task-1", "deadline_task-1"), scheduler.cancelledTags)
        assertEquals(6, scheduler.enqueued.size)
    }

    private class FakeDeadlineWorkScheduler : DeadlineWorkScheduler {
        val cancelledTags = mutableListOf<String>()
        val enqueued = mutableListOf<DeadlineWorkSpec>()

        override fun cancelAllByTag(tag: String) {
            cancelledTags.add(tag)
        }

        override fun enqueueUnique(spec: DeadlineWorkSpec) {
            enqueued.add(spec)
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60L * MINUTE_MS
    }
}
