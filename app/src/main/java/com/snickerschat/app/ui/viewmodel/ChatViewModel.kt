package com.snickerschat.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snickerschat.app.data.repository.FirebaseRepository
import com.snickerschat.app.data.model.User
import com.snickerschat.app.ui.state.ChatState
import com.snickerschat.app.ui.state.MessageWithUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.snickerschat.app.data.model.Message
import android.net.Uri
import java.io.File
import android.content.Context
import androidx.core.content.ContextCompat
import com.snickerschat.app.data.model.MediaType
import android.content.ContentResolver

class ChatViewModel(
    private val repository: FirebaseRepository,
    private val context: Context
) : ViewModel() {
    
    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()
    
    // Memory optimization: Clear messages when ViewModel is cleared
    override fun onCleared() {
        super.onCleared()
        _chatState.value = _chatState.value.copy(messages = emptyList())
    }
    
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
                        .onFailure { exception ->
                            println("ChatViewModel: Failed to get user for message: ${exception.message}")
                            // Add message without user info
                            messagesWithUser.add(
                                MessageWithUser(
                                    message = message,
                                    sender = User(id = message.senderId, username = "Unknown"),
                                    isFromCurrentUser = message.senderId == currentUserId
                                )
                            )
                        }
                }
                
                _chatState.value = _chatState.value.copy(messages = messagesWithUser)
            }
        }
        
        // Start real-time listener for other user's online status from RTDB
        viewModelScope.launch {
            // Wait for otherUser to be loaded
            while (_chatState.value.otherUser == null) {
                println("ChatViewModel: DEBUG - Waiting for otherUser to be loaded...")
                delay(100)
            }
            
            val otherUserId = _chatState.value.otherUserId
            if (otherUserId == null) {
                println("ChatViewModel: ERROR - otherUserId is null!")
                return@launch
            }
            
            println("ChatViewModel: DEBUG - Starting online status listener for user: $otherUserId")
            println("ChatViewModel: DEBUG - Current user: ${getCurrentUserId()}")
            println("ChatViewModel: DEBUG - Other user ID: $otherUserId")
            println("ChatViewModel: DEBUG - Other user loaded: ${_chatState.value.otherUser?.username}")
            
            repository.getOnlineStatusFlow(otherUserId).collect { statusData ->
                val isOnline = statusData["isOnline"] as? Boolean ?: false
                val lastSeenValue = statusData["lastSeen"]
                
                println("ChatViewModel: DEBUG - Received status data: $statusData")
                println("ChatViewModel: DEBUG - Parsed isOnline: $isOnline")
                println("ChatViewModel: DEBUG - Parsed lastSeenValue: $lastSeenValue")
                
                val currentOtherUser = _chatState.value.otherUser
                println("ChatViewModel: DEBUG - Current other user: ${currentOtherUser?.username}")
                
                if (currentOtherUser != null) {
                    // Convert lastSeen to Timestamp if available and not "null"
                    val lastSeenTimestamp = when (lastSeenValue) {
                        is Long -> {
                            val timestamp = com.google.firebase.Timestamp(lastSeenValue / 1000, ((lastSeenValue % 1000) * 1000000).toInt())
                            println("ChatViewModel: DEBUG - Converted lastSeen to Timestamp: $timestamp")
                            timestamp
                        }
                        "null" -> {
                            println("ChatViewModel: DEBUG - lastSeen is null")
                            null
                        }
                        else -> {
                            println("ChatViewModel: DEBUG - lastSeen is unknown type: ${lastSeenValue?.javaClass}")
                            null
                        }
                    }
                    
                    _chatState.value = _chatState.value.copy(
                        otherUser = currentOtherUser.copy(
                            isOnline = isOnline,
                            lastSeen = lastSeenTimestamp
                        )
                    )
                    println("ChatViewModel: DEBUG - Updated other user online status: ${currentOtherUser.username} isOnline: $isOnline, lastSeen: $lastSeenTimestamp")
                } else {
                    println("ChatViewModel: ERROR - Current other user is null!")
                }
            }
        }
        
        // Start real-time listener for message read status
        viewModelScope.launch {
            repository.getMessageReadStatusFlow(chatRoomId).collect { readMessages ->
                println("ChatViewModel: Real-time message read status update: $readMessages")
                
                // Update messages with read status
                val updatedMessages = _chatState.value.messages.map { messageWithUser ->
                    val isRead = readMessages[messageWithUser.message.id] ?: messageWithUser.message.isRead
                    println("ChatViewModel: Message ${messageWithUser.message.id} read status: $isRead")
                    messageWithUser.copy(
                        message = messageWithUser.message.copy(isRead = isRead)
                    )
                }
                
                _chatState.value = _chatState.value.copy(messages = updatedMessages)
            }
        }
        
        // Start real-time listener for typing status
        viewModelScope.launch {
            repository.getTypingStatusFlow(chatRoomId).collect { typingUsers ->
                println("ChatViewModel: Real-time typing status update: $typingUsers")
                val currentUserId = getCurrentUserId()
                // Removed unused otherUserId variable to fix warning
                
                // Filter out current user's typing status
                val otherUserTyping = typingUsers.any { (userId, isTyping) ->
                    userId != currentUserId && isTyping
                }
                
                _chatState.value = _chatState.value.copy(
                    isOtherUserTyping = otherUserTyping
                )
            }
        }
    }
    
    fun sendMessage(receiverId: String, content: String) {
        if (content.trim().isEmpty()) return
        
        println("ChatViewModel: Sending message to $receiverId: $content")
        
        // Optimistic update - immediately add message to UI
        val currentUserId = getCurrentUserId()
        val optimisticMessage = Message(
            senderId = currentUserId ?: "",
            receiverId = receiverId,
            content = content.trim(),
            timestamp = com.google.firebase.Timestamp.now(),
            isRead = false
        )
        
        val optimisticMessageWithUser = MessageWithUser(
            message = optimisticMessage,
            sender = User(id = currentUserId ?: "", username = "You"),
            isFromCurrentUser = true
        )
        
        // Add to UI immediately
        val currentMessages = _chatState.value.messages.toMutableList()
        currentMessages.add(optimisticMessageWithUser)
        _chatState.value = _chatState.value.copy(messages = currentMessages)
        
        // Clear input immediately
        // messageText = "" // This will be handled by the UI
        
        viewModelScope.launch {
            repository.sendMessage(receiverId, content.trim())
                .onSuccess { message ->
                    println("ChatViewModel: Message sent successfully: ${message.id}")
                    // Message will be updated through real-time listener
                }
                .onFailure { exception ->
                    println("ChatViewModel: Failed to send message: ${exception.message}")
                    // Remove optimistic message on failure
                    val updatedMessages = _chatState.value.messages.filter { 
                        it.message.content != content.trim() || !it.isFromCurrentUser 
                    }
                    _chatState.value = _chatState.value.copy(
                        messages = updatedMessages,
                        error = exception.message ?: "Mesaj gönderilemedi"
                    )
                }
        }
    }
    
    fun setTypingStatus(chatRoomId: String, isTyping: Boolean) {
        viewModelScope.launch {
            repository.setTypingStatus(chatRoomId, isTyping)
                .onSuccess {
                    println("ChatViewModel: Typing status updated: $isTyping")
                }
                .onFailure { exception ->
                    println("ChatViewModel: Failed to update typing status: ${exception.message}")
                }
        }
    }
    
    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            repository.markMessageAsRead(messageId)
        }
    }
    
    fun markAllMessagesAsRead(chatRoomId: String) {
        viewModelScope.launch {
            println("ChatViewModel: Marking all messages as read in chat: $chatRoomId")
            repository.markAllMessagesAsRead(chatRoomId)
                .onSuccess {
                    println("ChatViewModel: Successfully marked all messages as read")
                }
                .onFailure { exception ->
                    println("ChatViewModel: Failed to mark messages as read: ${exception.message}")
                }
        }
    }
    
    fun updateOnlineStatus(isOnline: Boolean) {
        viewModelScope.launch {
            repository.updateUserOnlineStatus(isOnline)
        }
    }
    
    fun showError(message: String) {
        _chatState.value = _chatState.value.copy(error = message)
    }
    
    fun clearError() {
        _chatState.value = _chatState.value.copy(error = null)
    }
    
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
                .onSuccess {
                    println("ChatViewModel: Message deleted successfully: $messageId")
                    // Message will be removed from UI through real-time listener
                }
                .onFailure { exception ->
                    println("ChatViewModel: Failed to delete message: ${exception.message}")
                    _chatState.value = _chatState.value.copy(
                        error = "Mesaj silinemedi: ${exception.message}"
                    )
                }
        }
    }
    
    fun addReactionToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.addReactionToMessage(messageId, emoji)
                .onSuccess {
                    println("ChatViewModel: Reaction added successfully: $emoji to message: $messageId")
                }
                .onFailure { exception ->
                    _chatState.value = _chatState.value.copy(
                        error = "Tepki eklenemedi: ${exception.message}"
                    )
                }
        }
    }
    
    private fun getCurrentUserId(): String? {
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
    
    // Extension function to get file name from URI
    private fun ContentResolver.getFileName(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                val cursor = query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            it.getString(displayNameIndex)
                        } else null
                    } else null
                }
            }
            "file" -> uri.lastPathSegment
            else -> null
        }
    }
    
    private fun sendMessageWithMedia(content: String, mediaUrl: String, mediaType: MediaType) {
        viewModelScope.launch {
            try {
                val receiverId = _chatState.value.otherUserId
                if (receiverId != null) {
                    // Create message with media
                    val messageContent = "$content\n$mediaUrl"
                    sendMessage(receiverId, messageContent)
                } else {
                    showError("Alıcı bulunamadı")
                }
            } catch (e: Exception) {
                showError("Medya mesajı gönderilirken hata: ${e.message}")
            }
        }
    }
    
    // Media handling functions
    fun handleCameraPhoto(photoUri: Uri) {
        viewModelScope.launch {
            try {
                // Convert URI to File
                val inputStream = context.contentResolver.openInputStream(photoUri)
                val file = File.createTempFile("camera_photo_", ".jpg", context.cacheDir)
                inputStream?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Upload to Cloudinary
                repository.uploadMedia(file, MediaType.IMAGE)
                    .onSuccess { url ->
                        // Send message with media
                        sendMessageWithMedia("📸 Fotoğraf", url, MediaType.IMAGE)
                    }
                    .onFailure { exception ->
                        showError("Fotoğraf yüklenirken hata: ${exception.message}")
                    }
            } catch (e: Exception) {
                showError("Fotoğraf işlenirken hata: ${e.message}")
            }
        }
    }
    
    fun handleGallerySelection(uri: Uri) {
        viewModelScope.launch {
            try {
                // Convert URI to File
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File.createTempFile("gallery_image_", ".jpg", context.cacheDir)
                inputStream?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Upload to Cloudinary
                repository.uploadMedia(file, MediaType.IMAGE)
                    .onSuccess { url ->
                        // Send message with media
                        sendMessageWithMedia("🖼️ Resim", url, MediaType.IMAGE)
                    }
                    .onFailure { exception ->
                        showError("Resim yüklenirken hata: ${exception.message}")
                    }
            } catch (e: Exception) {
                showError("Galeri seçimi işlenirken hata: ${e.message}")
            }
        }
    }
    
    fun handleAudioRecording(audioFile: File) {
        viewModelScope.launch {
            try {
                // Upload to Cloudinary
                repository.uploadMedia(audioFile, MediaType.AUDIO)
                    .onSuccess { url ->
                        // Send message with media
                        sendMessageWithMedia("🎵 Sesli Mesaj", url, MediaType.AUDIO)
                    }
                    .onFailure { exception ->
                        showError("Sesli mesaj yüklenirken hata: ${exception.message}")
                    }
            } catch (e: Exception) {
                showError("Sesli mesaj işlenirken hata: ${e.message}")
            }
        }
    }
    
    fun handleFileSelection(uri: Uri) {
        viewModelScope.launch {
            try {
                // Get file name from URI
                val fileName = context.contentResolver.getFileName(uri) ?: "unknown_file"
                
                // Convert URI to File
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File.createTempFile("file_", "_$fileName", context.cacheDir)
                inputStream?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Upload to Cloudinary
                repository.uploadMedia(file, MediaType.FILE)
                    .onSuccess { url ->
                        // Send message with media
                        sendMessageWithMedia("📎 $fileName", url, MediaType.FILE)
                    }
                    .onFailure { exception ->
                        showError("Dosya yüklenirken hata: ${exception.message}")
                    }
            } catch (e: Exception) {
                showError("Dosya işlenirken hata: ${e.message}")
            }
        }
    }
}