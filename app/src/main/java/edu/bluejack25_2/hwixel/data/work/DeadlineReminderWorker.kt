package edu.bluejack25_2.hwixel.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.data.repository.SharedPrefsProfileSettingsRepository

class DeadlineReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val CHANNEL_ID = "deadline_reminder"
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_TITLE = "task_title"
        const val KEY_LABEL = "label"
    }

    override fun doWork(): Result {
        if (!SharedPrefsProfileSettingsRepository(context)
                .isNotificationEnabled(SharedPrefsProfileSettingsRepository.NOTIF_DEADLINE)
        ) {
            return Result.success()
        }
        val taskTitle = inputData.getString(KEY_TASK_TITLE) ?: context.getString(R.string.notif_unknown_task)
        val label = inputData.getString(KEY_LABEL) ?: "soon"

        ensureChannel()

        val body = when (label) {
            "24h" -> context.getString(R.string.notif_deadline_24h_body, taskTitle)
            "1h" -> context.getString(R.string.notif_deadline_1h_body, taskTitle)
            else -> context.getString(R.string.notif_deadline_15m_body, taskTitle)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(context.getString(R.string.notif_deadline_title))
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }

        return Result.success()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_deadline_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
