package edu.bluejack252.hwixel.data.source.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.repository.SharedPrefsProfileSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HwixelMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationSource = NotificationFirebaseSource()

    companion object {
        private const val CHANNEL_ID = "hwixel_push"
        private const val CHANNEL_NAME = "Hwixel Notifications"
    }

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        serviceScope.launch {
            runCatching {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("users/$uid/fcmToken").setValue(token).await()
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val type = remoteMessage.data["type"] ?: return
        val message = remoteMessage.data["message"] ?: remoteMessage.notification?.body ?: return
        val referenceId = remoteMessage.data["referenceId"] ?: ""
        if (!SharedPrefsProfileSettingsRepository(this).isNotificationEnabled(type)) return

        serviceScope.launch {
            runCatching {
                notificationSource.writeNotification(uid, type, message, referenceId)
            }
        }

        postSystemNotification(remoteMessage.notification?.title ?: getString(R.string.app_name), message)
    }

    private fun postSystemNotification(title: String, body: String) {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_24)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            NotificationManagerCompat.from(this).notify(notifId, notification)
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
