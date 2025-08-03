package com.snickerschat.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snickerschat.app.data.repository.FirebaseRepository
import com.snickerschat.app.ui.state.ChatListState
import com.snickerschat.app.ui.state.ChatRoomWithUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val repository: FirebaseRepository
) : ViewModel() {
    
    private val _chatListState = MutableStateFlow(ChatListState())
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()
    
    fun loadChatRooms() {
        viewModelScope.launch {
            _chatListState.value = _chatListState.value.copy(isLoading = true, error = null)
            
            repository.getChatRooms()
                .onSuccess { chatRooms ->
                    println("Found ${chatRooms.size} chat rooms")
                    // Convert chat rooms to ChatRoomWithUser
                    val chatRoomsWithUser = mutableListOf<ChatRoomWithUser>()
                    for (chatRoom in chatRooms) {
                        val currentUserId = getCurrentUserId() ?: continue
                        val otherUserId = chatRoom.participants.find { it != currentUserId } ?: continue
                        println("Getting user info for: $otherUserId")
                        
                        try {
                            val user = repository.getUser(otherUserId).getOrThrow()
                            println("Found user: ${user.username}")
                            chatRoomsWithUser.add(ChatRoomWithUser(chatRoom = chatRoom, otherUser = user))
                        } catch (error: Exception) {
                            println("Failed to get user $otherUserId: ${error.message}")
                        }
                    }
                    
                    println("Converted to ${chatRoomsWithUser.size} ChatRoomWithUser")
                    _chatListState.value = _chatListState.value.copy(
                        chatRooms = chatRoomsWithUser,
                        isLoading = false
                    )
                }
                .onFailure { exception ->
                    _chatListState.value = _chatListState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Sohbetler yüklenemedi"
                    )
                }
        }
    }
    
    fun clearError() {
        _chatListState.value = _chatListState.value.copy(error = null)
    }
    
    private fun getCurrentUserId(): String? {
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
}