package com.snickerschat.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import android.app.Notification

object NotificationSettings {
    
    // Notification Channels
    const val CHANNEL_CHAT = "chat_messages"
    const val CHANNEL_GROUP = "group_messages"
    const val CHANNEL_SYSTEM = "system_notifications"
    
    // Vibration Patterns
    val VIBRATION_PATTERN_SHORT = longArrayOf(0, 250, 250, 250)
    val VIBRATION_PATTERN_LONG = longArrayOf(0, 500, 250, 500, 250, 500)
    val VIBRATION_PATTERN_DOUBLE = longArrayOf(0, 200, 100, 200, 100, 200)
    
    // Colors
    val COLOR_PRIMARY = 0xFF2196F3.toInt()
    val COLOR_SUCCESS = 0xFF4CAF50.toInt()
    val COLOR_WARNING = 0xFFFF9800.toInt()
    val COLOR_ERROR = 0xFFF44336.toInt()
    
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            
            // Chat Messages Channel
            val chatChannel = NotificationChannel(
                CHANNEL_CHAT,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Individual chat message notifications"
                enableLights(true)
                lightColor = COLOR_PRIMARY
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN_SHORT
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), 
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }
            
            // Group Messages Channel
            val groupChannel = NotificationChannel(
                CHANNEL_GROUP,
                "Group Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Group chat message notifications"
                enableLights(true)
                lightColor = COLOR_SUCCESS
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN_DOUBLE
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            
            // System Notifications Channel
            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "System Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System and update notifications"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            notificationManager.createNotificationChannels(listOf(chatChannel, groupChannel, systemChannel))
        }
    }
    
    fun getNotificationSound(context: Context, soundType: String = "default"): Uri {
        return when (soundType) {
            "message" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "ringtone" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            "alarm" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
    
    fun getVibrationPattern(patternType: String = "short"): LongArray {
        return when (patternType) {
            "short" -> VIBRATION_PATTERN_SHORT
            "long" -> VIBRATION_PATTERN_LONG
            "double" -> VIBRATION_PATTERN_DOUBLE
            else -> VIBRATION_PATTERN_SHORT
        }
    }
    
    fun getNotificationColor(colorType: String = "primary"): Int {
        return when (colorType) {
            "primary" -> COLOR_PRIMARY
            "success" -> COLOR_SUCCESS
            "warning" -> COLOR_WARNING
            "error" -> COLOR_ERROR
            else -> COLOR_PRIMARY
        }
    }
}