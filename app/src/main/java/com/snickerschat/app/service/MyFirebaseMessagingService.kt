package com.snickerschat.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.snickerschat.app.MainActivity
import com.snickerschat.app.R

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Handle data payload
        remoteMessage.data.isNotEmpty().let {
            val title = remoteMessage.data["title"] ?: "Yeni Mesaj"
            val message = remoteMessage.data["message"] ?: "Birisi size mesaj gönderdi"
            val senderId = remoteMessage.data["senderId"]
            val chatRoomId = remoteMessage.data["chatRoomId"]

            // Show notification
            showNotification(title, message, senderId, chatRoomId)
        }

        // Handle notification payload
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "Yeni Mesaj"
            val message = notification.body ?: "Birisi size mesaj gönderdi"
            
            showNotification(title, message, null, null)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to your server
        sendRegistrationToServer(token)
    }

    private fun showNotification(
        title: String,
        message: String,
        senderId: String?,
        chatRoomId: String?
    ) {
        val channelId = "chat_messages"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat message notifications"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open the app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Add chat room data if available
            chatRoomId?.let { putExtra("chatRoomId", it) }
            senderId?.let { putExtra("senderId", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use existing icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setLights(0xFF0000FF.toInt(), 3000, 3000)
            .build()

        // Show notification
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun sendRegistrationToServer(token: String) {
        // Save FCM token to Firestore
        val repository = com.snickerschat.app.data.repository.FirebaseRepository()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val currentUser = repository.getCurrentUser()
                currentUser?.let { user ->
                    repository.saveFCMToken(user.id, token)
                    println("FCM Token saved to Firestore: ${token.take(20)}...")
                }
            } catch (e: Exception) {
                println("Error saving FCM token: ${e.message}")
            }
        }
    }
}