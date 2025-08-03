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
                                        isFromCurrentUser = message.senderId == getCurrentUserId()
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
    }
    
    fun sendMessage(receiverId: String, content: String) {
        if (content.trim().isEmpty()) return
        
        viewModelScope.launch {
            repository.sendMessage(receiverId, content.trim())
                .onSuccess { message ->
                    // Message will be added through real-time listener
                }
                .onFailure { exception ->
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
    
    fun clearError() {
        _chatState.value = _chatState.value.copy(error = null)
    }
    
    private fun getCurrentUserId(): String? {
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
}