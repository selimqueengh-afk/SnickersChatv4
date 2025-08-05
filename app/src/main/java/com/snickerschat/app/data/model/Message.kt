package com.snickerschat.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Message(
    @DocumentId
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false,
    val isDeleted: Boolean = false,
    val chatRoomId: String = "",
    val reactions: List<String> = emptyList(),
    val replyTo: String? = null,
    val mediaUrl: String? = null,
    val mediaType: MediaType? = null
)

enum class MediaType {
    IMAGE,
    AUDIO,
    VIDEO,
    FILE
}

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
}