package com.snickerschat.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
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
    private val rtdb = FirebaseDatabase.getInstance()
    
    // Collections
    private val usersCollection = firestore.collection("users")
    private val messagesCollection = firestore.collection("messages")
    private val chatRoomsCollection = firestore.collection("chat_rooms")
    private val friendRequestsCollection = firestore.collection("requests")
    
    // RTDB References for real-time features
    private val onlineStatusRef = rtdb.getReference("online_status")
    private val typingStatusRef = rtdb.getReference("typing_status")
    private val messageReadRef = rtdb.getReference("message_read")
    
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
    
    suspend fun checkIfFriends(userId1: String, userId2: String): Result<Boolean> {
        return try {
            val chatRoomQuery = chatRoomsCollection
                .whereArrayContains("participants", userId1)
                .get()
                .await()
                .documents
                .filter { doc ->
                    val chatRoom = doc.toObject(ChatRoom::class.java)
                    chatRoom?.participants?.contains(userId2) == true
                }
            
            Result.success(chatRoomQuery.isNotEmpty())
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
            
            // Create chat room only if it doesn't exist
            if (existingChatRoom.isEmpty()) {
                val chatRoom = ChatRoom(
                    participants = listOf(request.senderId, request.receiverId),
                    lastMessageTimestamp = com.google.firebase.Timestamp.now()
                )
                val chatRoomRef = chatRoomsCollection.add(chatRoom).await()
                println("Chat room created with ID: ${chatRoomRef.id}")
                println("Participants: ${chatRoom.participants}")
            } else {
                println("Chat room already exists, skipping creation")
            }
            
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
    
    suspend fun deleteChatRoom(chatRoomId: String): Result<Unit> {
        return try {
            // Delete all messages in the chat room
            val messagesSnapshot = messagesCollection
                .whereEqualTo("chatRoomId", chatRoomId)
                .get()
                .await()
            
            for (messageDoc in messagesSnapshot.documents) {
                messageDoc.reference.delete().await()
            }
            
            // Delete the chat room
            chatRoomsCollection.document(chatRoomId).delete().await()
            
            println("Chat room $chatRoomId deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Error deleting chat room: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getChatRoom(chatRoomId: String): Result<ChatRoom> {
        return try {
            val doc = chatRoomsCollection.document(chatRoomId).get().await()
            val chatRoom = doc.toObject(ChatRoom::class.java)
                ?: throw Exception("Chat room not found")
            
            Result.success(chatRoom.copy(id = doc.id))
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
            val messageId = messagesCollection.document().id // Generate ID
            val now = System.currentTimeMillis()
            
            println("FirebaseRepository: Sending message from $senderId to $receiverId")
            
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
            
            println("FirebaseRepository: Found ${chatRoomQuery.size} existing chat rooms")
            
            val chatRoomId = if (chatRoomQuery.isNotEmpty()) {
                chatRoomQuery.first().id
            } else {
                val newChatRoom = ChatRoom(participants = listOf(senderId, receiverId))
                val newChatRoomRef = chatRoomsCollection.add(newChatRoom).await()
                println("FirebaseRepository: Created new chat room: ${newChatRoomRef.id}")
                newChatRoomRef.id
            }
            
            println("FirebaseRepository: Using chat room ID: $chatRoomId")
            
            // Create message in RTDB
            val messageData = mapOf(
                "id" to messageId,
                "senderId" to senderId,
                "receiverId" to receiverId,
                "content" to content,
                "timestamp" to now,
                "isRead" to false,
                "chatRoomId" to chatRoomId
            )
            
            // Save to RTDB for instant real-time sync
            messageReadRef.child(chatRoomId).child(messageId).setValue(messageData).await()
            
            // Also save to Firestore for persistence
            val message = Message(
                id = messageId,
                senderId = senderId,
                receiverId = receiverId,
                content = content,
                timestamp = com.google.firebase.Timestamp.now(),
                isRead = false,
                chatRoomId = chatRoomId
            )
            
            messagesCollection.document(messageId).set(message).await()
            
            println("FirebaseRepository: Message saved with ID: ${message.id}")
            
            // Update chat room
            chatRoomsCollection.document(chatRoomId).update(
                mapOf(
                    "lastMessage" to content,
                    "lastMessageTimestamp" to message.timestamp,
                    "lastMessageSenderId" to senderId
                )
            ).await()
            
            println("FirebaseRepository: Chat room updated successfully")
            println("FirebaseRepository: Message saved to RTDB and Firestore")
            Result.success(message)
        } catch (e: Exception) {
            println("FirebaseRepository: Error sending message: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun markMessageAsRead(messageId: String): Result<Unit> {
        return try {
            val now = com.google.firebase.Timestamp.now()
            messagesCollection.document(messageId).update(
                mapOf(
                    "isRead" to true,
                    "readAt" to now
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun markAllMessagesAsRead(chatRoomId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val nowMillis = System.currentTimeMillis()
            
            println("FirebaseRepository: Marking messages as read for user: $currentUserId in chat: $chatRoomId")
            
            // Get all messages from RTDB where current user is receiver
            val messagesSnapshot = messageReadRef.child(chatRoomId).get().await()
            
            println("FirebaseRepository: Found ${messagesSnapshot.childrenCount} messages in RTDB")
            
            for (child in messagesSnapshot.children) {
                val messageId = child.key
                val messageData = child.getValue(object : com.google.firebase.database.GenericTypeIndicator<Map<String, Any>>() {})
                
                if (messageData != null) {
                    val receiverId = messageData["receiverId"] as? String
                    val isRead = messageData["isRead"] as? Boolean ?: false
                    
                    // Only mark as read if current user is the receiver and message is not read
                    if (receiverId == currentUserId && !isRead) {
                        println("FirebaseRepository: Marking message $messageId as read")
                        
                        // Update in RTDB
                        messageReadRef.child(chatRoomId).child(messageId).child("isRead").setValue(true).await()
                        messageReadRef.child(chatRoomId).child(messageId).child("readAt").setValue(nowMillis).await()
                        messageReadRef.child(chatRoomId).child(messageId).child("readBy").setValue(currentUserId).await()
                        
                        println("FirebaseRepository: Marked message $messageId as read in RTDB")
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("FirebaseRepository: Error marking messages as read: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun updateUserOnlineStatus(isOnline: Boolean): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val now = System.currentTimeMillis()
            
            println("FirebaseRepository: Updating online status for user $currentUserId: $isOnline")
            
            // Update ONLY in RTDB for real-time
            if (isOnline) {
                println("FirebaseRepository: Setting user as online in RTDB")
                onlineStatusRef.child(currentUserId).setValue(
                    mapOf(
                        "isOnline" to true,
                        "lastSeen" to null,
                        "timestamp" to now
                    )
                ).await()
                
                // Set up onDisconnect to mark user as offline when connection is lost
                onlineStatusRef.child(currentUserId).onDisconnect().setValue(
                    mapOf(
                        "isOnline" to false,
                        "lastSeen" to now,
                        "timestamp" to now
                    )
                )
                println("FirebaseRepository: onDisconnect set up for user")
            } else {
                println("FirebaseRepository: Setting user as offline in RTDB")
                onlineStatusRef.child(currentUserId).setValue(
                    mapOf(
                        "isOnline" to false,
                        "lastSeen" to now,
                        "timestamp" to now
                    )
                ).await()
                
                // Remove onDisconnect when user goes offline manually
                onlineStatusRef.child(currentUserId).onDisconnect().removeValue()
                println("FirebaseRepository: onDisconnect removed for user")
            }
            
            println("FirebaseRepository: Online status updated successfully in RTDB")
            Result.success(Unit)
        } catch (e: Exception) {
            println("FirebaseRepository: Error updating online status: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    // Real-time listeners
    fun getMessagesFlow(chatRoomId: String): Flow<List<Message>> = callbackFlow {
        println("FirebaseRepository: Starting messages listener for chat: $chatRoomId")
        val listener = messageReadRef.child(chatRoomId).addValueEventListener(
            object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    println("FirebaseRepository: Messages data changed for chat $chatRoomId")
                    println("FirebaseRepository: Snapshot children count: ${snapshot.childrenCount}")
                    
                    val messages = mutableListOf<Message>()
                    for (child in snapshot.children) {
                        val messageData = child.getValue(object : com.google.firebase.database.GenericTypeIndicator<Map<String, Any>>() {})
                        if (messageData != null) {
                            val id = messageData["id"] as? String
                            val senderId = messageData["senderId"] as? String
                            val receiverId = messageData["receiverId"] as? String
                            val content = messageData["content"] as? String
                            val chatRoomId = messageData["chatRoomId"] as? String
                            
                            // Only create message if all required fields are present
                            if (id != null && senderId != null && receiverId != null && content != null && chatRoomId != null) {
                                val message = Message(
                                    id = id,
                                    senderId = senderId,
                                    receiverId = receiverId,
                                    content = content,
                                    timestamp = com.google.firebase.Timestamp.now(), // Convert from long
                                    isRead = messageData["isRead"] as? Boolean ?: false,
                                    chatRoomId = chatRoomId
                                )
                                messages.add(message)
                            }
                        }
                    }
                    
                    println("FirebaseRepository: Found ${messages.size} messages in RTDB")
                    trySend(messages)
                }
                
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    println("FirebaseRepository: Messages listener cancelled for chat $chatRoomId: ${error.message}")
                    trySend(emptyList())
                }
            }
        )
        
        awaitClose { 
            println("FirebaseRepository: Removing messages listener for chat: $chatRoomId")
            messageReadRef.child(chatRoomId).removeEventListener(listener) 
        }
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
    
    fun getUserFlow(userId: String): Flow<User> = callbackFlow {
        val listener = usersCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                
                val user = snapshot?.toObject(User::class.java)
                if (user != null) {
                    trySend(user.copy(id = snapshot.id))
                }
            }
        
        awaitClose { listener.remove() }
    }
    
    // RTDB Methods for real-time features
    
    suspend fun setTypingStatus(chatRoomId: String, isTyping: Boolean): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            if (isTyping) {
                typingStatusRef.child(chatRoomId).child(currentUserId).setValue(
                    mapOf(
                        "isTyping" to true,
                        "timestamp" to System.currentTimeMillis()
                    )
                ).await()
            } else {
                typingStatusRef.child(chatRoomId).child(currentUserId).removeValue().await()
            }
            
            println("FirebaseRepository: Typing status updated: $isTyping in chat: $chatRoomId")
            Result.success(Unit)
        } catch (e: Exception) {
            println("FirebaseRepository: Error updating typing status: ${e.message}")
            Result.failure(e)
        }
    }
    
    fun getTypingStatusFlow(chatRoomId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val listener = typingStatusRef.child(chatRoomId).addValueEventListener(
            object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val typingUsers = mutableMapOf<String, Boolean>()
                    for (child in snapshot.children) {
                        val userId = child.key
                        val isTyping = child.child("isTyping").getValue(Boolean::class.java) ?: false
                        if (userId != null && isTyping) {
                            typingUsers[userId] = true
                        }
                    }
                    trySend(typingUsers)
                }
                
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    // Handle error
                }
            }
        )
        
        awaitClose { typingStatusRef.child(chatRoomId).removeEventListener(listener) }
    }
    
    fun getOnlineStatusFlow(userId: String): Flow<Boolean> = callbackFlow {
        println("FirebaseRepository: Starting online status listener for user: $userId")
        val listener = onlineStatusRef.child(userId).addValueEventListener(
            object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    println("FirebaseRepository: Online status data changed for user $userId")
                    println("FirebaseRepository: Snapshot exists: ${snapshot.exists()}")
                    println("FirebaseRepository: Snapshot children count: ${snapshot.childrenCount}")
                    
                    if (snapshot.exists()) {
                        val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                        println("FirebaseRepository: Online status changed for user $userId: $isOnline")
                        trySend(isOnline)
                    } else {
                        println("FirebaseRepository: No online status data for user $userId, defaulting to offline")
                        trySend(false)
                    }
                }
                
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    println("FirebaseRepository: Online status listener cancelled for user $userId: ${error.message}")
                    trySend(false)
                }
            }
        )
        
        awaitClose { 
            println("FirebaseRepository: Removing online status listener for user: $userId")
            onlineStatusRef.child(userId).removeEventListener(listener) 
        }
    }
    
    fun getMessageReadStatusFlow(chatRoomId: String): Flow<Map<String, Boolean>> = callbackFlow {
        println("FirebaseRepository: Starting message read status listener for chat: $chatRoomId")
        val listener = messageReadRef.child(chatRoomId).addValueEventListener(
            object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    println("FirebaseRepository: Message read status data changed for chat $chatRoomId")
                    println("FirebaseRepository: Snapshot exists: ${snapshot.exists()}")
                    println("FirebaseRepository: Snapshot children count: ${snapshot.childrenCount}")
                    
                    val readMessages = mutableMapOf<String, Boolean>()
                    for (child in snapshot.children) {
                        val messageId = child.key
                        val readBy = child.child("readBy").getValue(String::class.java)
                        println("FirebaseRepository: Message $messageId read by: $readBy")
                        if (messageId != null && readBy != null) {
                            readMessages[messageId] = true
                        }
                    }
                    println("FirebaseRepository: Read messages: $readMessages")
                    trySend(readMessages)
                }
                
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    println("FirebaseRepository: Message read status listener cancelled for chat $chatRoomId: ${error.message}")
                    trySend(emptyMap())
                }
            }
        )
        
        awaitClose { 
            println("FirebaseRepository: Removing message read status listener for chat: $chatRoomId")
            messageReadRef.child(chatRoomId).removeEventListener(listener) 
        }
    }
}