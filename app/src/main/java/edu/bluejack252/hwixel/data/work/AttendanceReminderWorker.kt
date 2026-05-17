package edu.bluejack252.hwixel.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import edu.bluejack252.hwixel.R

class AttendanceReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val CHANNEL_ID = "attendance_reminder"
        const val KEY_PROJECT_NAME = "project_name"
        const val KEY_SESSION_DATE = "session_date"
        private const val NOTIFICATION_ID = 2001
    }

    override fun doWork(): Result {
        val projectName = inputData.getString(KEY_PROJECT_NAME) ?: "your project"
        val sessionDate = inputData.getString(KEY_SESSION_DATE) ?: ""

        ensureChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_attendance)
            .setContentTitle(context.getString(R.string.attendance_next_session_label))
            .setContentText("$projectName — $sessionDate")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        return Result.success()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.attendance_next_session_label),
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
