package com.snickerschat.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.snickerschat.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Collections
    private val usersCollection = firestore.collection("users")
    private val messagesCollection = firestore.collection("messages")
    private val chatRoomsCollection = firestore.collection("chat_rooms")
    private val friendRequestsCollection = firestore.collection("requests")
    
    // Authentication
    suspend fun signInAnonymously(): Result<String> {
        return try {
            val result = auth.signInAnonymously().await()
            Result.success(result.user?.uid ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createUser(username: String): Result<User> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val user = User(
                id = userId,
                username = username,
                email = auth.currentUser?.email ?: "",
                isOnline = true,
                lastSeen = com.google.firebase.Timestamp.now()
            )
            usersCollection.document(userId).set(user).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateUserOnlineStatus(isOnline: Boolean) {
        try {
            val userId = auth.currentUser?.uid ?: return
            val updateData = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to com.google.firebase.Timestamp.now()
            )
            usersCollection.document(userId).update(updateData).await()
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    // User operations
    suspend fun getUser(userId: String): Result<User> {
        return try {
            val document = usersCollection.document(userId).get().await()
            val user = document.toObject(User::class.java)
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val snapshot = usersCollection
                .whereGreaterThanOrEqualTo("username", query)
                .whereLessThanOrEqualTo("username", query + '\uf8ff')
                .get()
                .await()
            
            val users = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                .filter { it.id != currentUserId }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Friend requests
    suspend fun sendFriendRequest(receiverId: String): Result<Unit> {
        return try {
            val senderId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val request = FriendRequest(
                senderId = senderId,
                receiverId = receiverId
            )
            friendRequestsCollection.add(request).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getFriendRequests(): Result<List<FriendRequest>> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val snapshot = friendRequestsCollection
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("status", RequestStatus.PENDING)
                .get()
                .await()
            
            val requests = snapshot.documents.mapNotNull { it.toObject(FriendRequest::class.java) }
            Result.success(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun acceptFriendRequest(requestId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            // Update request status
            friendRequestsCollection.document(requestId).update("status", RequestStatus.ACCEPTED).await()
            
            // Get request details
            val request = friendRequestsCollection.document(requestId).get().await()
                .toObject(FriendRequest::class.java) ?: throw Exception("Request not found")
            
            // Create chat room
            val chatRoom = ChatRoom(
                participants = listOf(request.senderId, request.receiverId)
            )
            chatRoomsCollection.add(chatRoom).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun declineFriendRequest(requestId: String): Result<Unit> {
        return try {
            friendRequestsCollection.document(requestId).update("status", RequestStatus.DECLINED).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Chat operations
    suspend fun getChatRooms(): Result<List<ChatRoom>> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val snapshot = chatRoomsCollection
                .whereArrayContains("participants", currentUserId)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val chatRooms = snapshot.documents.mapNotNull { it.toObject(ChatRoom::class.java) }
            Result.success(chatRooms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMessages(chatRoomId: String): Result<List<Message>> {
        return try {
            val snapshot = messagesCollection
                .whereEqualTo("chatRoomId", chatRoomId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
            
            val messages = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendMessage(receiverId: String, content: String): Result<Message> {
        return try {
            val senderId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            // Find or create chat room
            val chatRoomQuery = chatRoomsCollection
                .whereArrayContains("participants", senderId)
                .whereArrayContains("participants", receiverId)
                .get()
                .await()
            
            val chatRoomId = if (chatRoomQuery.documents.isNotEmpty()) {
                chatRoomQuery.documents.first().id
            } else {
                val newChatRoom = ChatRoom(participants = listOf(senderId, receiverId))
                chatRoomsCollection.add(newChatRoom).await().id
            }
            
            // Create message
            val message = Message(
                senderId = senderId,
                receiverId = receiverId,
                content = content,
                chatRoomId = chatRoomId
            )
            
            val messageRef = messagesCollection.add(message).await()
            val savedMessage = message.copy(id = messageRef.id)
            
            // Update chat room
            chatRoomsCollection.document(chatRoomId).update(
                mapOf(
                    "lastMessage" to content,
                    "lastMessageTimestamp" to message.timestamp,
                    "lastMessageSenderId" to senderId
                )
            ).await()
            
            Result.success(savedMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun markMessageAsRead(messageId: String): Result<Unit> {
        return try {
            messagesCollection.document(messageId).update("isRead", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Real-time listeners
    fun getMessagesFlow(chatRoomId: String): Flow<List<Message>> = flow {
        val listener = messagesCollection
            .whereEqualTo("chatRoomId", chatRoomId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Handle error
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { 
                    it.toObject(Message::class.java) 
                } ?: emptyList()
                
                trySend(messages)
            }
        
        // Clean up listener when flow is cancelled
        awaitClose { listener.remove() }
    }
    
    fun getChatRoomsFlow(): Flow<List<ChatRoom>> = flow {
        val currentUserId = auth.currentUser?.uid ?: return@flow
        
        val listener = chatRoomsCollection
            .whereArrayContains("participants", currentUserId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                
                val chatRooms = snapshot?.documents?.mapNotNull { 
                    it.toObject(ChatRoom::class.java) 
                } ?: emptyList()
                
                trySend(chatRooms)
            }
        
        awaitClose { listener.remove() }
    }
}