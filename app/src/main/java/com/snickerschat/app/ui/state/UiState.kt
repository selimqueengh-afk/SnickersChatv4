package com.snickerschat.app.ui.state

import com.snickerschat.app.data.model.*

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

data class LoginState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val user: User? = null
)

data class ChatListState(
    val chatRooms: List<ChatRoomWithUser> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ChatRoomWithUser(
    val chatRoom: ChatRoom,
    val otherUser: User
)

data class ChatState(
    val messages: List<MessageWithUser> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isTyping: Boolean = false,
    val otherUserId: String? = null
)

data class MessageWithUser(
    val message: Message,
    val sender: User,
    val isFromCurrentUser: Boolean
)

data class FriendsState(
    val friends: List<User> = emptyList(),
    val friendRequests: List<FriendRequestWithUser> = emptyList(),
    val searchResults: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val pendingRequests: Set<String> = emptySet(), // Track pending requests by user ID
    val existingFriends: Set<String> = emptySet() // Track existing friends by user ID
)

data class FriendRequestWithUser(
    val request: FriendRequest,
    val sender: User
)

data class ThemeState(
    val isDarkMode: Boolean = false
)