package com.snickerschat.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.core.app.RemoteInput
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import android.app.Notification
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.snickerschat.app.MainActivity
import com.snickerschat.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.snickerschat.app.data.repository.FirebaseRepository
import android.widget.Toast
import kotlinx.coroutines.tasks.await

private const val REPLY_KEY = "key_text_reply"
private const val REPLY_ACTION = "com.snickerschat.REPLY_ACTION"

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "chat_messages"
        private const val GROUP_KEY = "com.snickerschat.app.CHAT_MESSAGES"
        private var notificationId = 0
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Handle data payload
        remoteMessage.data.isNotEmpty().let {
            val title = remoteMessage.data["title"] ?: "Yeni Mesaj"
            val message = remoteMessage.data["message"] ?: "Birisi size mesaj gönderdi"
            val senderId = remoteMessage.data["senderId"]
            val senderName = remoteMessage.data["senderName"] ?: "Bilinmeyen"
            val senderAvatar = remoteMessage.data["senderAvatar"]
            val chatRoomId = remoteMessage.data["chatRoomId"]
            val messageType = remoteMessage.data["messageType"] ?: "text"

            // Show rich notification
            showRichNotification(title, message, senderId, senderName, senderAvatar, chatRoomId, messageType)
        }

        // Handle notification payload
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "Yeni Mesaj"
            val message = notification.body ?: "Birisi size mesaj gönderdi"
            
            showRichNotification(title, message, null, "Bilinmeyen", null, null, "text")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sendRegistrationToServer(token)
    }

    private fun showRichNotification(
        title: String,
        message: String,
        senderId: String?,
        senderName: String,
        senderAvatar: String?,
        chatRoomId: String?,
        messageType: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel
        createNotificationChannel()

        // Create intent to open the app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            chatRoomId?.let { putExtra("chatRoomId", it) }
            senderId?.let { putExtra("senderId", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Yanıtla aksiyonu için RemoteInput
        val remoteInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel("Yanıtla")
            .build()
        val replyIntent = Intent(this, NotificationReplyReceiver::class.java).apply {
            action = REPLY_ACTION
            putExtra("chatRoomId", chatRoomId)
            putExtra("senderId", senderId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "Yanıtla",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // Build notification with rich content
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(getMessagePreview(message, messageType))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setLights(0xFF2196F3.toInt(), 3000, 3000)
            .setStyle(createMessageStyle(senderName, message, messageType))
            .addAction(replyAction)

        // Add sender avatar if available
        senderAvatar?.let { avatarUrl ->
            try {
                val bitmap = loadBitmapFromUrl(avatarUrl)
                bitmap?.let {
                    notificationBuilder.setLargeIcon(it)
                }
            } catch (e: Exception) {
                println("Error loading avatar: ${e.message}")
            }
        }

        // Show notification with unique ID for grouping
        val uniqueId = (senderId?.hashCode() ?: System.currentTimeMillis()).toInt()
        notificationManager.notify(uniqueId, notificationBuilder.build())

        // Show group summary notification
        showGroupSummaryNotification(notificationManager)
    }

    private fun createMessageStyle(senderName: String, message: String, messageType: String): NotificationCompat.Style {
        return NotificationCompat.MessagingStyle(Person.Builder()
            .setName(senderName)
            .build())
            .addMessage(getMessagePreview(message, messageType), System.currentTimeMillis(), 
                Person.Builder().setName(senderName).build())
    }

    private fun getMessagePreview(message: String, messageType: String): String {
        return when (messageType) {
            "image" -> "📷 Fotoğraf gönderdi"
            "video" -> "🎥 Video gönderdi"
            "audio" -> "🎵 Ses gönderdi"
            "file" -> "📎 Dosya gönderdi"
            "location" -> "📍 Konum gönderdi"
            else -> if (message.length > 50) "${message.take(50)}..." else message
        }
    }

    private fun loadBitmapFromUrl(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            val input = connection.getInputStream()
            BitmapFactory.decodeStream(input)
        } catch (e: IOException) {
            null
        }
    }

    private fun showGroupSummaryNotification(notificationManager: NotificationManager) {
        val summaryIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val summaryPendingIntent = PendingIntent.getActivity(
            this,
            2,
            summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SnickersChat")
            .setContentText("Yeni mesajlarınız var")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setContentIntent(summaryPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(0, summaryNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat message notifications"
                enableLights(true)
                lightColor = 0xFF2196F3.toInt()
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendRegistrationToServer(token: String) {
        val repository = com.snickerschat.app.data.repository.FirebaseRepository()
        CoroutineScope(Dispatchers.IO).launch {
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

// Bildirimden gelen yanıtı yakalayan BroadcastReceiver
class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == REPLY_ACTION) {
            val chatRoomId = intent.getStringExtra("chatRoomId")
            val senderId = intent.getStringExtra("senderId")
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val replyText = remoteInput?.getCharSequence(REPLY_KEY)?.toString()
            if (!replyText.isNullOrEmpty() && !chatRoomId.isNullOrEmpty() && !senderId.isNullOrEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = FirebaseRepository()
                        // chatRoomId'den receiverId'yi bul
                        val chatRoomDoc = repository.firestore.collection("chat_rooms").document(chatRoomId).get().await()
                        val participants = chatRoomDoc.get("participants") as? List<*> ?: emptyList<String>()
                        val receiverId = participants.firstOrNull { it != senderId } as? String
                        if (receiverId == null) {
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(context, "Yanıt gönderilemedi: Alıcı bulunamadı", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                        val result = repository.sendMessage(
                            receiverId = receiverId,
                            content = replyText
                        )
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, if (result.isSuccess) "Yanıt gönderildi" else "Yanıt gönderilemedi", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Yanıt gönderilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}