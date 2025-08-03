package com.snickerschat.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.snickerschat.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
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
    
    suspend fun signUpWithEmail(email: String, password: String, username: String): Result<User> {
        return try {
            // Create user with email/password
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("Failed to create user")
            
            // Create user document in Firestore
            val user = User(
                id = userId,
                username = username,
                email = email,
                isOnline = true,
                lastSeen = com.google.firebase.Timestamp.now()
            )
            usersCollection.document(userId).set(user).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("Failed to sign in")
            
            // Get user data from Firestore
            val userDoc = usersCollection.document(userId).get().await()
            val user = userDoc.toObject(User::class.java)
            
            if (user != null) {
                // Update online status
                updateUserOnlineStatus(true)
                Result.success(user)
            } else {
                Result.failure(Exception("User data not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signOut() {
        try {
            updateUserOnlineStatus(false)
            auth.signOut()
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser
        return if (firebaseUser != null) {
            User(
                id = firebaseUser.uid,
                username = "",
                email = firebaseUser.email ?: "",
                isOnline = false
            )
        } else null
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
            
            // Get request details first
            val request = friendRequestsCollection.document(requestId).get().await()
                .toObject(FriendRequest::class.java) ?: throw Exception("Request not found")
            
            // Update request status
            friendRequestsCollection.document(requestId).update("status", RequestStatus.ACCEPTED).await()
            
            // Check if chat room already exists
            val existingChatRoom = chatRoomsCollection
                .whereArrayContains("participants", request.senderId)
                .get()
                .await()
                .documents
                .filter { doc ->
                    val chatRoom = doc.toObject(ChatRoom::class.java)
                    val containsReceiver = chatRoom?.participants?.contains(request.receiverId) == true
                    println("Chat room ${doc.id}: participants=${chatRoom?.participants}, contains receiver: $containsReceiver")
                    containsReceiver
                }
            
            // Always create a new chat room for now (for debugging)
            val chatRoom = ChatRoom(
                participants = listOf(request.senderId, request.receiverId),
                lastMessageTimestamp = com.google.firebase.Timestamp.now()
            )
            val chatRoomRef = chatRoomsCollection.add(chatRoom).await()
            println("Chat room created with ID: ${chatRoomRef.id}")
            println("Participants: ${chatRoom.participants}")
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("Error in acceptFriendRequest: ${e.message}")
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
            println("Getting chat rooms for user: $currentUserId")
            
            val snapshot = chatRoomsCollection
                .whereArrayContains("participants", currentUserId)
                .get()
                .await()
            
            val chatRooms = snapshot.documents.mapNotNull { doc ->
                val chatRoom = doc.toObject(ChatRoom::class.java)
                println("Chat room ${doc.id}: participants=${chatRoom?.participants}")
                chatRoom
            }
            println("Found ${chatRooms.size} chat rooms in Firestore")
            Result.success(chatRooms)
        } catch (e: Exception) {
            println("Error getting chat rooms: ${e.message}")
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
                .get()
                .await()
                .documents
                .filter { doc ->
                    val chatRoom = doc.toObject(ChatRoom::class.java)
                    chatRoom?.participants?.contains(receiverId) == true
                }
            
            val chatRoomId = if (chatRoomQuery.isNotEmpty()) {
                chatRoomQuery.first().id
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
    fun getMessagesFlow(chatRoomId: String): Flow<List<Message>> = callbackFlow {
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
    
    fun getChatRoomsFlow(): Flow<List<ChatRoom>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: return@callbackFlow
        
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