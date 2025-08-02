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
    val chatRoomId: String = ""
)

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
}