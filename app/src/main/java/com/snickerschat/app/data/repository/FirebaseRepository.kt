package com.snickerschat.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.messaging.FirebaseMessaging
import com.snickerschat.app.data.model.*
import com.snickerschat.app.config.CloudinaryConfig
import com.snickerschat.app.data.api.ApiClient
import com.snickerschat.app.data.api.NotificationRequest
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    private val userStatusRef = rtdb.getReference("userStatus")
    private val typingStatusRef = rtdb.getReference("typing_status")
    private val messageReadRef = rtdb.getReference("message_read")
    private val fcmTokensRef = rtdb.getReference("fcm_tokens")
    
    // Authentication
    suspend fun signInAnonymously(): Result<String> {
        return try {
            val result = auth.signInAnonymously().await()
            val userId = result.user?.uid ?: throw Exception("Failed to create anonymous user")
            
            // Create user document in Firestore for anonymous user
            val user = User(
                id = userId,
                username = "Anonim_${userId.take(8)}",
                email = "anonim@snickers.chat",
                isOnline = true,
                lastSeen = com.google.firebase.Timestamp.now()
            )
            usersCollection.document(userId).set(user).await()
            
            // Initialize user status in RTDB
            println("FirebaseRepository: DEBUG - Initializing user status in RTDB for anonymous user: $userId")
            userStatusRef.child(userId).setValue(
                mapOf(
                    "isOnline" to true,
                    "lastSeen" to null
                )
            ).await()
            
            // Set up onDisconnect for anonymous user
            userStatusRef.child(userId).onDisconnect().setValue(
                mapOf(
                    "isOnline" to false,
                    "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                )
            )
            
            println("FirebaseRepository: DEBUG - Anonymous user status initialized in RTDB")
            Result.success(userId)
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
            
            // Initialize user status in RTDB
            println("FirebaseRepository: DEBUG - Initializing user status in RTDB for new user: $userId")
            userStatusRef.child(userId).setValue(
                mapOf(
                    "isOnline" to true,
                    "lastSeen" to null
                )
            ).await()
            
            // Set up onDisconnect for new user
            userStatusRef.child(userId).onDisconnect().setValue(
                mapOf(
                    "isOnline" to false,
                    "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                )
            )
            
            println("FirebaseRepository: DEBUG - User status initialized in RTDB")
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
                // Initialize user status in RTDB if not exists
                println("FirebaseRepository: DEBUG - Checking/initializing user status in RTDB for existing user: $userId")
                userStatusRef.child(userId).get().await().let { snapshot ->
                    if (!snapshot.exists()) {
                        println("FirebaseRepository: DEBUG - User status not found in RTDB, initializing...")
                        userStatusRef.child(userId).setValue(
                            mapOf(
                                "isOnline" to true,
                                "lastSeen" to null
                            )
                        ).await()
                        
                        // Set up onDisconnect
                        userStatusRef.child(userId).onDisconnect().setValue(
                            mapOf(
                                "isOnline" to false,
                                "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                        )
                        println("FirebaseRepository: DEBUG - User status initialized in RTDB")
                    } else {
                        println("FirebaseRepository: DEBUG - User status already exists in RTDB, updating to online")
                        // Don't call updateUserOnlineStatus here to avoid conflicts
                        userStatusRef.child(userId).updateChildren(
                            mapOf(
                                "isOnline" to true,
                                "lastSeen" to null
                            )
                        ).await()
                        
                        // Set up onDisconnect
                        userStatusRef.child(userId).onDisconnect().setValue(
                            mapOf(
                                "isOnline" to false,
                                "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                        )
                        println("FirebaseRepository: DEBUG - User status updated to online in RTDB")
                    }
                }
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
            
            // Initialize user status in RTDB
            println("FirebaseRepository: DEBUG - Initializing user status in RTDB for created user: $userId")
            userStatusRef.child(userId).setValue(
                mapOf(
                    "isOnline" to true,
                    "lastSeen" to null
                )
            ).await()
            
            // Set up onDisconnect
            userStatusRef.child(userId).onDisconnect().setValue(
                mapOf(
                    "isOnline" to false,
                    "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                )
            )
            
            println("FirebaseRepository: DEBUG - User status initialized in RTDB")
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
            println("DEBUG: Starting sendFriendRequest...")
            println("DEBUG: receiverId: $receiverId")
            
            val senderId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            println("DEBUG: senderId: $senderId")
            val request = FriendRequest(
                senderId = senderId,
                receiverId = receiverId,
                status = RequestStatus.PENDING,
                timestamp = com.google.firebase.Timestamp.now()
            )
            friendRequestsCollection.add(request).await()
            println("DEBUG: Friend request sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: Error sending friend request: ${e.message}")
            e.printStackTrace()
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
            // Removed unused currentUserId variable to fix warning
            
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
            println("DEBUG: Starting sendMessage...")
            println("DEBUG: receiverId: $receiverId")
            println("DEBUG: content: $content")
            
            val senderId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            println("DEBUG: senderId: $senderId")
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
            
            println("FirebaseRepository: Found "+chatRoomQuery.size+" existing chat rooms")
            
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
            
            // Send push notification via backend in background
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                sendPushNotification(senderId, receiverId, content, chatRoomId)
            }

            Result.success(message)
        } catch (e: Exception) {
            println("FirebaseRepository: Error sending message: ${e.message}")
            e.printStackTrace()
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
    
    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            println("FirebaseRepository: Deleting message: $messageId")
            
            // Delete from Firestore - find message by ID
            val query = messagesCollection.whereEqualTo("id", messageId)
            val querySnapshot = query.get().await()
            
            for (document in querySnapshot.documents) {
                document.reference.delete().await()
                println("FirebaseRepository: Deleted message from Firestore: ${document.id}")
            }
            
            // Delete from RTDB - we need to find the chatRoomId first
            // Get all chat rooms for current user
            val chatRoomsQuery = chatRoomsCollection.whereArrayContains("participants", currentUserId)
            val chatRoomsSnapshot = chatRoomsQuery.get().await()
            
            for (chatRoomDoc in chatRoomsSnapshot.documents) {
                val chatRoomId = chatRoomDoc.id
                println("FirebaseRepository: Checking chat room: $chatRoomId")
                
                // Check if message exists in this chat room
                val messageSnapshot = messageReadRef.child(chatRoomId).child(messageId).get().await()
                if (messageSnapshot.exists()) {
                    println("FirebaseRepository: Found message in RTDB chat room: $chatRoomId")
                    messageReadRef.child(chatRoomId).child(messageId).removeValue().await()
                    println("FirebaseRepository: Deleted message from RTDB: $messageId in chat: $chatRoomId")
                    break // Found and deleted, no need to check other chat rooms
                }
            }
            
            println("FirebaseRepository: Message deleted successfully from both Firestore and RTDB")
            Result.success(Unit)
        } catch (e: Exception) {
            println("FirebaseRepository: Error deleting message: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun addReactionToMessage(messageId: String, emoji: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            println("FirebaseRepository: Adding reaction: $emoji to message: $messageId")
            
            // Find message in Firestore
            val query = messagesCollection.whereEqualTo("id", messageId)
            val querySnapshot = query.get().await()
            
            for (document in querySnapshot.documents) {
                @Suppress("UNCHECKED_CAST")
                val currentReactions = document.get("reactions") as? List<String> ?: emptyList()
                val updatedReactions = if (currentReactions.contains(emoji)) {
                    currentReactions - emoji // Remove if already exists
                } else {
                    currentReactions + emoji // Add if doesn't exist
                }
                
                document.reference.update("reactions", updatedReactions).await()
                println("FirebaseRepository: Updated reactions in Firestore: $updatedReactions")
            }
            
            // Update in RTDB - we need to find the chatRoomId first
            val chatRoomsQuery = chatRoomsCollection.whereArrayContains("participants", currentUserId)
            val chatRoomsSnapshot = chatRoomsQuery.get().await()
            
            for (chatRoomDoc in chatRoomsSnapshot.documents) {
                val chatRoomId = chatRoomDoc.id
                println("FirebaseRepository: Checking chat room for reaction: $chatRoomId")
                
                // Check if message exists in this chat room
                val messageSnapshot = messageReadRef.child(chatRoomId).child(messageId).get().await()
                if (messageSnapshot.exists()) {
                    println("FirebaseRepository: Found message in RTDB chat room: $chatRoomId")
                    
                    val currentReactions = messageSnapshot.child("reactions").getValue(object : com.google.firebase.database.GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                    val updatedReactions = if (currentReactions.contains(emoji)) {
                        currentReactions - emoji
                    } else {
                        currentReactions + emoji
                    }
                    
                    messageReadRef.child(chatRoomId).child(messageId).child("reactions").ref.setValue(updatedReactions).await()
                    println("FirebaseRepository: Updated reactions in RTDB: $updatedReactions")
                    break // Found and updated, no need to check other chat rooms
                }
            }
            
            println("FirebaseRepository: Reaction added successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("FirebaseRepository: Error adding reaction: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun uploadMedia(file: File, mediaType: MediaType): Result<String> {
        return try {
            println("FirebaseRepository: Uploading media: ${file.name}, type: $mediaType")
            
            val resourceType = when(mediaType) {
                MediaType.IMAGE -> "image"
                MediaType.AUDIO -> "video" // Cloudinary uses video for audio
                MediaType.VIDEO -> "video"
                MediaType.FILE -> "raw"
            }
            
            // Cloudinary upload (gerçek upload)
            return suspendCancellableCoroutine { cont ->
                MediaManager.get().upload(file.absolutePath)
                    .option("resource_type", resourceType)
                    .option("public_id", "snickers_chat/${System.currentTimeMillis()}_${file.nameWithoutExtension}")
                    .option("overwrite", true)
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String?) {
                            println("Cloudinary upload started: $requestId")
                        }
                        override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                        override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                            val url = resultData?.get("secure_url") as? String
                                ?: resultData?.get("url") as? String
                            println("Cloudinary upload success: $url")
                            if (url != null) {
                                cont.resume(Result.success(url), null)
                            } else {
                                cont.resume(Result.failure(Exception("Cloudinary: URL null")), null)
                            }
                        }
                        override fun onError(requestId: String?, error: ErrorInfo?) {
                            println("Cloudinary upload error: ${error?.description}")
                            cont.resume(Result.failure(Exception(error?.description)), null)
                        }
                        override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                            println("Cloudinary upload rescheduled: ${error?.description}")
                            cont.resume(Result.failure(Exception(error?.description)), null)
                        }
                    })
                    .dispatch()
            }
        } catch (e: Exception) {
            println("FirebaseRepository: Error uploading media: ${e.message}")
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
                
                if (messageData != null && messageId != null) {
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
    
    // FCM Token Management
    suspend fun getFCMToken(): Result<String> {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            println("FirebaseRepository: FCM token obtained: $token")
            Result.success(token)
        } catch (e: Exception) {
            println("FirebaseRepository: Error getting FCM token: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun saveFCMToken(userId: String, token: String): Result<Unit> {
        return try {
            // Save to RTDB for real-time access
            fcmTokensRef.child(userId).setValue(token).await()
            
            // Also save to Firestore for backend access
            usersCollection.document(userId).update(
                mapOf(
                    "fcmToken" to token,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
            ).await()
            
            println("FirebaseRepository: FCM token saved for user: $userId (RTDB + Firestore)")
            Result.success(Unit)
        } catch (e: Exception) {
            println("FirebaseRepository: Error saving FCM token: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getFCMTokenForUser(userId: String): Result<String?> {
        return try {
            val snapshot = fcmTokensRef.child(userId).get().await()
            val token = snapshot.getValue(String::class.java)
            println("FirebaseRepository: FCM token for user $userId: $token")
            Result.success(token)
        } catch (e: Exception) {
            println("FirebaseRepository: Error getting FCM token for user: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun updateUserOnlineStatus(isOnline: Boolean): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId.isNullOrEmpty()) {
                println("FirebaseRepository: ERROR - User ID is null or empty!")
                throw Exception("User not authenticated or ID is empty")
            }
            
            println("FirebaseRepository: DEBUG - User ID: $currentUserId")
            println("FirebaseRepository: DEBUG - Updating online status: $isOnline")
            println("FirebaseRepository: DEBUG - RTDB path: ${userStatusRef.child(currentUserId).toString()}")
            
            // Update in RTDB only - userStatus/{userId} structure
            val statusData = if (isOnline) {
                mapOf(
                    "isOnline" to true,
                    "lastSeen" to null
                )
            } else {
                mapOf(
                    "isOnline" to false,
                    "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                )
            }
            
            println("FirebaseRepository: DEBUG - Status data to write: $statusData")
            
            // Update in RTDB using updateChildren to avoid overwriting onDisconnect
            if (isOnline) {
                userStatusRef.child(currentUserId).updateChildren(
                    mapOf(
                        "isOnline" to true,
                        "lastSeen" to null
                    )
                ).await()
                println("FirebaseRepository: DEBUG - Successfully updated to online in RTDB")
                
                // Set up onDisconnect when going online
                userStatusRef.child(currentUserId).onDisconnect().setValue(
                    mapOf(
                        "isOnline" to false,
                        "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                    )
                )
                println("FirebaseRepository: DEBUG - onDisconnect set up for user")
            } else {
                // For offline, use setValue to ensure complete update
                userStatusRef.child(currentUserId).setValue(
                    mapOf(
                        "isOnline" to false,
                        "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                    )
                ).await()
                println("FirebaseRepository: DEBUG - Successfully updated to offline in RTDB")
                
                // Remove onDisconnect when going offline manually
                userStatusRef.child(currentUserId).onDisconnect().removeValue()
                println("FirebaseRepository: DEBUG - onDisconnect removed for user")
            }
            
            println("FirebaseRepository: Online status updated successfully in RTDB")
            Result.success(Unit)
        } catch (e: Exception) {
            println("FirebaseRepository: ERROR updating online status: ${e.message}")
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
                            val messageChatRoomId = messageData["chatRoomId"] as? String
                            val timestamp = messageData["timestamp"] as? Long
                            @Suppress("UNCHECKED_CAST")
                            val reactions = messageData["reactions"] as? List<String> ?: emptyList()
                            
                            // Only create message if all required fields are present
                            if (id != null && senderId != null && receiverId != null && content != null && messageChatRoomId != null && timestamp != null) {
                                val message = Message(
                                    id = id,
                                    senderId = senderId,
                                    receiverId = receiverId,
                                    content = content,
                                    timestamp = com.google.firebase.Timestamp(timestamp / 1000, ((timestamp % 1000) * 1000000).toInt()),
                                    isRead = messageData["isRead"] as? Boolean ?: false,
                                    chatRoomId = messageChatRoomId,
                                    reactions = reactions
                                )
                                messages.add(message)
                            }
                        }
                    }
                    
                    // Sort messages by timestamp (oldest first)
                    messages.sortBy { it.timestamp.seconds }
                    
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
    
    fun getOnlineStatusFlow(userId: String): Flow<Map<String, Any>> = callbackFlow {
        if (userId.isNullOrEmpty()) {
            println("FirebaseRepository: ERROR - Cannot start listener for null/empty userId")
            return@callbackFlow
        }
        
        println("FirebaseRepository: DEBUG - Starting online status listener for user: $userId")
        println("FirebaseRepository: DEBUG - RTDB path: ${userStatusRef.child(userId).toString()}")
        
        val listener = userStatusRef.child(userId).addValueEventListener(
            object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    println("FirebaseRepository: DEBUG - onDataChange triggered for user $userId")
                    println("FirebaseRepository: DEBUG - Snapshot exists: ${snapshot.exists()}")
                    println("FirebaseRepository: DEBUG - Snapshot value: ${snapshot.value}")
                    
                    if (snapshot.exists()) {
                        val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                        val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java)
                        
                        println("FirebaseRepository: DEBUG - Raw values from RTDB:")
                        println("FirebaseRepository: DEBUG - isOnline: ${snapshot.child("isOnline").value}")
                        println("FirebaseRepository: DEBUG - lastSeen: ${snapshot.child("lastSeen").value}")
                        println("FirebaseRepository: DEBUG - Parsed isOnline: $isOnline")
                        println("FirebaseRepository: DEBUG - Parsed lastSeen: $lastSeen")
                        
                        val statusData = mutableMapOf<String, Any>()
                        statusData["isOnline"] = isOnline
                        statusData["lastSeen"] = lastSeen ?: "null"
                        
                        println("FirebaseRepository: DEBUG - Sending status data: $statusData")
                        trySend(statusData)
                    } else {
                        println("FirebaseRepository: DEBUG - No online status data for user $userId, defaulting to offline")
                        val defaultStatus = mutableMapOf<String, Any>()
                        defaultStatus["isOnline"] = false
                        defaultStatus["lastSeen"] = "null"
                        trySend(defaultStatus)
                    }
                }
                
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    println("FirebaseRepository: ERROR - Online status listener cancelled for user $userId")
                    println("FirebaseRepository: ERROR - Database error: ${error.message}")
                    println("FirebaseRepository: ERROR - Error code: ${error.code}")
                    val errorStatus = mutableMapOf<String, Any>()
                    errorStatus["isOnline"] = false
                    errorStatus["lastSeen"] = "null"
                    trySend(errorStatus)
                }
            }
        )
        
        awaitClose { 
            println("FirebaseRepository: DEBUG - Removing online status listener for user: $userId")
            userStatusRef.child(userId).removeEventListener(listener) 
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

    private suspend fun sendFCMPushNotification(token: String, title: String, body: String, chatRoomId: String, senderId: String) {
        withContext(Dispatchers.IO) {
            try {
                println("FCM: Preparing to send push notification...")
                println("FCM: Token: ${token.take(20)}...")
                println("FCM: Title: $title")
                println("FCM: Body: $body")
                println("FCM: ChatRoomId: $chatRoomId")
                println("FCM: SenderId: $senderId")
                
                // FCM HTTP v1 API endpoint
                val FCM_API = "https://fcm.googleapis.com/fcm/send"
                val SERVER_KEY = "key=BEojipfPOa3zG3WJHEIMzR-XJPUfNVpg3d5a05MIjW4yIE1klzKJtamHZM7qvVUgbs_DoWHz-IgX5ynJhSgOrDw"
                
                println("FCM: Using API: $FCM_API")
                println("FCM: Server key: ${SERVER_KEY.take(20)}...")
                
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("to", token)
                    put("notification", JSONObject().apply {
                        put("title", title)
                        put("body", body)
                        put("sound", "default")
                        put("priority", "high")
                    })
                    put("data", JSONObject().apply {
                        put("chatRoomId", chatRoomId)
                        put("senderId", senderId)
                        put("click_action", "FLUTTER_NOTIFICATION_CLICK")
                    })
                    put("priority", "high")
                }
                
                val jsonString = json.toString()
                println("FCM: JSON payload: $jsonString")
                
                val bodyReq = jsonString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(FCM_API)
                    .addHeader("Authorization", SERVER_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(bodyReq)
                    .build()
                
                println("FCM: Sending HTTP request...")
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    println("FCM: Response code: ${response.code}")
                    println("FCM: Response message: ${response.message}")
                    println("FCM: Response body: $responseBody")
                    
                    if (response.isSuccessful) {
                        println("FCM: Push notification sent successfully!")
                    } else {
                        println("FCM: Failed to send push notification: ${response.code} ${response.message}")
                        println("FCM: Error response: $responseBody")
                    }
                }
            } catch (e: Exception) {
                println("FCM: Exception in sendFCMPushNotification: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    // Push Notification via Backend
    private suspend fun sendPushNotification(
        senderId: String, 
        receiverId: String, 
        message: String, 
        chatRoomId: String
    ) {
        try {
            println("DEBUG: Starting backend push notification...")
            println("DEBUG: senderId: $senderId")
            println("DEBUG: receiverId: $receiverId")
            println("DEBUG: message: $message")
            println("DEBUG: chatRoomId: $chatRoomId")
            
            // Get sender's name
            val senderDoc = usersCollection.document(senderId).get().await()
            val senderName = senderDoc.getString("username") ?: "Kullanıcı"
            println("DEBUG: senderName: $senderName")
            
            // Create notification request
            val request = NotificationRequest(
                receiverId = receiverId,
                senderId = senderId,
                senderName = senderName,
                message = message,
                chatRoomId = chatRoomId
            )
            println("DEBUG: Notification request created: $request")
            
            // Send notification via backend API
            println("DEBUG: Sending request to backend API...")
            val response = ApiClient.backendApi.sendNotification(request)
            println("DEBUG: Backend API response code: ${response.code()}")
            println("DEBUG: Backend API response body: ${response.body()}")
            
            if (response.isSuccessful) {
                println("FirebaseRepository: Push notification sent successfully via backend")
            } else {
                val errorBody = response.errorBody()?.string()
                println("FirebaseRepository: Failed to send push notification via backend: ${response.code()}")
                println("FirebaseRepository: Error response: $errorBody")
            }
        } catch (e: Exception) {
            println("FirebaseRepository: Error sending push notification via backend: ${e.message}")
            e.printStackTrace()
        }
    }
}