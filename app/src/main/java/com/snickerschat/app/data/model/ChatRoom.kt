package com.snickerschat.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ChatRoom(
    @DocumentId
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Timestamp? = null,
    val lastMessageSenderId: String = "",
    val unreadCount: Map<String, Int> = emptyMap(),
    val createdAt: Timestamp = Timestamp.now()
)