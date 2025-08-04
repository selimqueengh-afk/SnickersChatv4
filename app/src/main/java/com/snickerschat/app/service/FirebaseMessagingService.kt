package com.snickerschat.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.snickerschat.app.MainActivity
import com.snickerschat.app.R
import com.snickerschat.app.data.repository.FirebaseRepository

class SnickersFirebaseMessagingService : FirebaseMessagingService() {
    
    private val repository = FirebaseRepository()
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token'ı backend'e kaydet
        saveTokenToBackend(token)
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Bildirim verilerini al
        val title = remoteMessage.data["title"] ?: "Yeni Mesaj"
        val body = remoteMessage.data["body"] ?: "Bir mesajınız var"
        val chatRoomId = remoteMessage.data["chatRoomId"]
        val senderId = remoteMessage.data["senderId"]
        
        // Bildirimi göster
        showNotification(title, body, chatRoomId, senderId)
    }
    
    private fun saveTokenToBackend(token: String) {
        val currentUser = repository.getCurrentUser()
        currentUser?.let { user ->
            // Token'ı Firestore'a kaydet
            repository.saveFCMToken(user.id, token)
        }
    }
    
    private fun showNotification(
        title: String,
        body: String,
        chatRoomId: String?,
        senderId: String?
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Notification channel oluştur (Android 8.0+ için gerekli)
        createNotificationChannel(notificationManager)
        
        // MainActivity'yi açacak intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("chatRoomId", chatRoomId)
            putExtra("senderId", senderId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Bildirimi oluştur
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sohbet Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sohbet mesajları için bildirimler"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    companion object {
        private const val CHANNEL_ID = "snickers_chat_channel"
    }
}