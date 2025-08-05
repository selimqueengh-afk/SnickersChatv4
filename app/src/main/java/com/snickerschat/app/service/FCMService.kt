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

class FCMService : FirebaseMessagingService() {
    
    companion object {
        private const val CHANNEL_ID = "chat_notifications"
        private const val CHANNEL_NAME = "Chat Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications for new messages"
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("FCMService: New token: $token")
        // TODO: Send token to server
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        println("FCMService: Message received from: ${remoteMessage.from}")
        
        // Check if message contains a data payload
        remoteMessage.data.isNotEmpty().let {
            println("FCMService: Message data payload: ${remoteMessage.data}")
            
            // Extract data
            val senderId = remoteMessage.data["senderId"]
            val senderName = remoteMessage.data["senderName"]
            val messageContent = remoteMessage.data["messageContent"]
            val chatRoomId = remoteMessage.data["chatRoomId"]
            
            // Show notification
            if (senderId != null && senderName != null && messageContent != null) {
                showNotification(senderName, messageContent, chatRoomId)
            }
        }
        
        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            println("FCMService: Message notification payload: ${it.title}")
            showNotification(it.title ?: "", it.body ?: "", null)
        }
    }
    
    private fun showNotification(title: String, message: String, chatRoomId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (chatRoomId != null) {
                putExtra("chatRoomId", chatRoomId)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        notificationManager.notify(0, notificationBuilder.build())
    }
}