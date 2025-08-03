package com.snickerschat.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snickerschat.app.data.repository.FirebaseRepository
import com.snickerschat.app.ui.state.ChatState
import com.snickerschat.app.ui.state.MessageWithUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: FirebaseRepository
) : ViewModel() {
    
    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()
    
    fun loadMessages(chatRoomId: String) {
        viewModelScope.launch {
            _chatState.value = _chatState.value.copy(isLoading = true, error = null)
            
            // First load the chat room to get participants
            repository.getChatRoom(chatRoomId)
                .onSuccess { chatRoom ->
                    println("ChatViewModel: Chat room loaded, participants: ${chatRoom.participants}")
                    
                    // Get the other user ID (not current user)
                    val currentUserId = getCurrentUserId()
                    val otherUserId = chatRoom.participants.find { it != currentUserId }
                    
                    if (otherUserId != null) {
                        println("ChatViewModel: Other user ID: $otherUserId")
                        
                        // Load the other user's information
                        repository.getUser(otherUserId)
                            .onSuccess { otherUser ->
                                println("ChatViewModel: Other user loaded: ${otherUser.username}")
                                _chatState.value = _chatState.value.copy(
                                    otherUserId = otherUserId,
                                    otherUser = otherUser
                                )
                            }
                            .onFailure { exception ->
                                println("ChatViewModel: Failed to load other user: ${exception.message}")
                                _chatState.value = _chatState.value.copy(otherUserId = otherUserId)
                            }
                    }
                    
                    // Now load initial messages
                    repository.getMessages(chatRoomId)
                        .onSuccess { messages ->
                            // Convert messages to MessageWithUser
                            val messagesWithUser = mutableListOf<MessageWithUser>()
                            for (message in messages) {
                                repository.getUser(message.senderId)
                                    .onSuccess { user ->
                                        messagesWithUser.add(
                                            MessageWithUser(
                                                message = message,
                                                sender = user,
                                                isFromCurrentUser = message.senderId == currentUserId
                                            )
                                        )
                                    }
                            }
                            
                            _chatState.value = _chatState.value.copy(
                                messages = messagesWithUser,
                                isLoading = false
                            )
                        }
                        .onFailure { exception ->
                            _chatState.value = _chatState.value.copy(
                                isLoading = false,
                                error = exception.message ?: "Mesajlar yüklenemedi"
                            )
                        }
                }
                .onFailure { exception ->
                    _chatState.value = _chatState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Sohbet yüklenemedi"
                    )
                }
        }
        
        // Start real-time listener for messages
        viewModelScope.launch {
            repository.getMessagesFlow(chatRoomId).collect { messages ->
                println("ChatViewModel: Real-time messages update: ${messages.size} messages")
                
                // Convert messages to MessageWithUser
                val messagesWithUser = mutableListOf<MessageWithUser>()
                val currentUserId = getCurrentUserId()
                
                for (message in messages) {
                    repository.getUser(message.senderId)
                        .onSuccess { user ->
                            messagesWithUser.add(
                                MessageWithUser(
                                    message = message,
                                    sender = user,
                                    isFromCurrentUser = message.senderId == currentUserId
                                )
                            )
                        }
                }
                
                _chatState.value = _chatState.value.copy(messages = messagesWithUser)
            }
        }
    }
    
    fun sendMessage(receiverId: String, content: String) {
        if (content.trim().isEmpty()) return
        
        println("ChatViewModel: Sending message to $receiverId: $content")
        
        viewModelScope.launch {
            repository.sendMessage(receiverId, content.trim())
                .onSuccess { message ->
                    println("ChatViewModel: Message sent successfully: ${message.id}")
                    // Message will be added through real-time listener
                }
                .onFailure { exception ->
                    println("ChatViewModel: Failed to send message: ${exception.message}")
                    _chatState.value = _chatState.value.copy(
                        error = exception.message ?: "Mesaj gönderilemedi"
                    )
                }
        }
    }
    
    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            repository.markMessageAsRead(messageId)
        }
    }
    
    fun showError(message: String) {
        _chatState.value = _chatState.value.copy(error = message)
    }
    
    fun clearError() {
        _chatState.value = _chatState.value.copy(error = null)
    }
    
    private fun getCurrentUserId(): String? {
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
}