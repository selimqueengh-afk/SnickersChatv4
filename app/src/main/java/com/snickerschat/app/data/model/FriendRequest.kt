package com.snickerschat.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class FriendRequest(
    @DocumentId
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val status: RequestStatus = RequestStatus.PENDING,
    val timestamp: Timestamp = Timestamp.now()
)

enum class RequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}