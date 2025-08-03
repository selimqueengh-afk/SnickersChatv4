package com.snickerschat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snickerschat.app.R
import com.snickerschat.app.ui.state.ChatState
import com.snickerschat.app.ui.state.MessageWithUser
import com.snickerschat.app.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatRoomId: String,
    chatViewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    val chatState by chatViewModel.chatState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    
    // Get receiver ID from chat state
    val receiverId = chatState.otherUserId ?: ""
    
    // Get other user info for the header - from chat state with real-time updates
    var otherUser by remember { mutableStateOf(chatState.otherUser) }
    
    // Update other user info when chat state changes
    LaunchedEffect(chatState.otherUser) {
        otherUser = chatState.otherUser
    }
    
    // Real-time listener for other user's online status
    LaunchedEffect(chatState.otherUserId) {
        if (chatState.otherUserId != null) {
            // Start listening to other user's online status changes
            println("ChatScreen: Starting real-time listener for user: ${chatState.otherUserId}")
        }
    }
    
    // Update receiver ID when chat state changes
    LaunchedEffect(chatState.otherUserId) {
        if (chatState.otherUserId != null) {
            println("ChatScreen: Receiver ID set from chat state to: ${chatState.otherUserId}")
        }
    }
    
    LaunchedEffect(chatRoomId) {
        chatViewModel.loadMessages(chatRoomId)
        // Mark all messages as read when entering chat
        delay(1000) // Wait a bit for messages to load
        chatViewModel.markAllMessagesAsRead(chatRoomId)
    }
    
    // Update online status when entering chat
    LaunchedEffect(Unit) {
        chatViewModel.updateOnlineStatus(true)
    }
    
    // Set offline when leaving chat
    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.updateOnlineStatus(false)
        }
    }
    
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top bar
        TopAppBar(
            title = {
                if (otherUser != null) {
                    Column {
                        Text(
                            text = otherUser.username,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = if (otherUser.isOnline) {
                                "🟢 Çevrimiçi"
                            } else {
                                "🔴 Çevrimdışı"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                        if (!otherUser.isOnline && otherUser.lastSeen != null) {
                            Text(
                                text = otherUser.lastSeen.let { lastSeen ->
                                    val now = com.google.firebase.Timestamp.now()
                                    val diffInSeconds = now.seconds - lastSeen.seconds
                                    when {
                                        diffInSeconds < 60 -> "Son görülme: Az önce"
                                        diffInSeconds < 3600 -> "Son görülme: ${diffInSeconds / 60} dakika önce"
                                        diffInSeconds < 86400 -> "Son görülme: ${diffInSeconds / 3600} saat önce"
                                        else -> "Son görülme: ${diffInSeconds / 86400} gün önce"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Sohbet",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Geri",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        
        // Error message
        chatState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Messages
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {

            if (chatState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (chatState.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_messages),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatState.messages) { messageWithUser ->
                        MessageItem(
                            messageWithUser = messageWithUser,
                            onLongClick = {
                                // Handle long click for copy/delete
                            }
                        )
                    }
                }
            }
            

        }
        
        // Message input
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text(stringResource(R.string.type_message)) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                FloatingActionButton(
                    onClick = {
                        if (messageText.trim().isNotEmpty()) {
                            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            
                            println("Sending message to: $receiverId")
                            println("Current user: $currentUserId")
                            println("Message: ${messageText.trim()}")
                            println("Chat room ID: $chatRoomId")
                            
                            if (receiverId.isNotEmpty() && receiverId != currentUserId) {
                                chatViewModel.sendMessage(receiverId, messageText.trim())
                                messageText = ""
                            } else {
                                println("Invalid receiver ID or same as current user")
                                println("Receiver ID: '$receiverId'")
                                println("Current User ID: '$currentUserId'")
                                chatViewModel.showError("Mesaj gönderilemedi: Alıcı bulunamadı")
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = stringResource(R.string.send)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    messageWithUser: MessageWithUser,
    onLongClick: () -> Unit
) {
    val isFromCurrentUser = messageWithUser.isFromCurrentUser
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(vertical = 2.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromCurrentUser) 16.dp else 4.dp,
                bottomEnd = if (isFromCurrentUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isFromCurrentUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = messageWithUser.message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFromCurrentUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dateFormat.format(messageWithUser.message.timestamp.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFromCurrentUser) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                    )
                    
                    if (isFromCurrentUser) {
                        Icon(
                            imageVector = if (messageWithUser.message.isRead) {
                                Icons.Default.DoneAll
                            } else {
                                Icons.Default.Done
                            },
                            contentDescription = if (messageWithUser.message.isRead) {
                                "Okundu (${messageWithUser.message.id})"
                            } else {
                                "Gönderildi (${messageWithUser.message.id})"
                            },
                            modifier = Modifier.size(16.dp),
                            tint = if (messageWithUser.message.isRead) {
                                Color.Blue // Mavi renk for read messages
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
        }
        
        // User name for received messages
        if (!isFromCurrentUser) {
            Text(
                text = messageWithUser.sender.username,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
            )
        }
    }
}