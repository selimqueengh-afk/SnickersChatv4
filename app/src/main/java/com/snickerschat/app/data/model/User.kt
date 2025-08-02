package com.snickerschat.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val avatarUrl: String = ""
)