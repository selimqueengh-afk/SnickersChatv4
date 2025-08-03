package com.snickerschat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Context
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    
    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedMessageForDelete by remember { mutableStateOf<String?>(null) }
    var showMessageOptionsDialog by remember { mutableStateOf(false) }
    var selectedMessageForOptions by remember { mutableStateOf<MessageWithUser?>(null) }
    
    // Get receiver ID from chat state
    val receiverId = chatState.otherUserId ?: ""
    
    // Get other user info for the header - from chat state with real-time updates
    var otherUser by remember { mutableStateOf(chatState.otherUser) }
    
    // Update other user info when chat state changes
    LaunchedEffect(chatState.otherUser) {
        otherUser = chatState.otherUser
        println("ChatScreen: DEBUG - otherUser updated: ${chatState.otherUser?.username}")
        println("ChatScreen: DEBUG - otherUser isOnline: ${chatState.otherUser?.isOnline}")
        println("ChatScreen: DEBUG - otherUser lastSeen: ${chatState.otherUser?.lastSeen}")
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
    
    // Typing status tracking
    LaunchedEffect(messageText) {
        if (messageText.isNotEmpty()) {
            chatViewModel.setTypingStatus(chatRoomId, true)
        } else {
            chatViewModel.setTypingStatus(chatRoomId, false)
        }
    }
    
    // Clear typing status when leaving
    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.setTypingStatus(chatRoomId, false)
        }
    }
    
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
            // Mark messages as read when they are loaded
            chatViewModel.markAllMessagesAsRead(chatRoomId)
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Modern Top Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Geri",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // User Info
                val user = otherUser
                if (user != null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Avatar
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (user.isOnline) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.username.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (user.isOnline) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            
                            // Online indicator
                            if (user.isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.tertiary,
                                            shape = CircleShape
                                        )
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // User Details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.username,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Status indicator
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (user.isOnline) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape
                                        )
                                )
                                
                                Text(
                                    text = if (user.isOnline) {
                                        "Çevrimiçi"
                                    } else {
                                        user.lastSeen?.let { lastSeen ->
                                            val now = com.google.firebase.Timestamp.now()
                                            val diffInSeconds = now.seconds - lastSeen.seconds
                                            val timeText = when {
                                                diffInSeconds < 60 -> "Az önce"
                                                diffInSeconds < 3600 -> "${diffInSeconds / 60} dakika önce"
                                                diffInSeconds < 86400 -> "${diffInSeconds / 3600} saat önce"
                                                else -> "${diffInSeconds / 86400} gün önce"
                                            }
                                            "Son görülme: $timeText"
                                        } ?: "Çevrimdışı"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (user.isOnline) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Loading state
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = "Yükleniyor...",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Kullanıcı bilgileri alınıyor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { /* TODO: Video call */ },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoCall,
                            contentDescription = "Video arama",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = { /* TODO: Voice call */ },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Sesli arama",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
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
                    items(
                        items = chatState.messages,
                        key = { it.message.id }
                    ) { messageWithUser ->
                        MessageItem(
                            messageWithUser = messageWithUser,
                            onLongClick = {
                                // Show message options
                                showMessageOptionsDialog = true
                                selectedMessageForOptions = messageWithUser
                            },
                            modifier = Modifier.animateItemPlacement(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        )
                    }
                }
            }
            

        }
        
        // Animated Typing indicator
        AnimatedVisibility(
            visible = chatState.isOtherUserTyping,
            enter = slideInVertically(
                initialOffsetY = { 50 },
                animationSpec = tween(durationMillis = 200)
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = slideOutVertically(
                targetOffsetY = { 50 },
                animationSpec = tween(durationMillis = 200)
            ) + fadeOut(animationSpec = tween(durationMillis = 200))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Yazıyor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Animated typing dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        repeat(3) { index ->
                            val infiniteTransition = rememberInfiniteTransition()
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        durationMillis = 600,
                                        delayMillis = index * 200
                                    ),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                        shape = CircleShape
                                    )
                            )
                        }
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
    
    // Modern Message Options Dialog
    if (showMessageOptionsDialog) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showMessageOptionsDialog = false }
            )
            
            // Modern Options Card
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header
                    Text(
                        text = "Mesaj Seçenekleri",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Copy option
                    selectedMessageForOptions?.let { messageWithUser ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Mesaj", messageWithUser.message.content)
                                    clipboard.setPrimaryClip(clip)
                                    chatViewModel.showError("Mesaj kopyalandı!")
                                    showMessageOptionsDialog = false
                                    selectedMessageForOptions = null
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Kopyala",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Kopyala",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Reply option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // TODO: Implement reply feature
                                    chatViewModel.showError("Yanıtlama özelliği yakında eklenecek!")
                                    showMessageOptionsDialog = false
                                    selectedMessageForOptions = null
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Reply,
                                contentDescription = "Yanıtla",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Yanıtla",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // React option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // TODO: Implement reaction feature
                                    chatViewModel.showError("Tepki özelliği yakında eklenecek!")
                                    showMessageOptionsDialog = false
                                    selectedMessageForOptions = null
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Tepki Ver",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Tepki Ver",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        // Delete option (only for own messages)
                        if (messageWithUser.isFromCurrentUser) {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showMessageOptionsDialog = false
                                        showDeleteDialog = true
                                        selectedMessageForDelete = messageWithUser.message.id
                                        selectedMessageForOptions = null
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Sil",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Mesajı Sil") },
            text = { Text("Bu mesajı silmek istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedMessageForDelete?.let { messageId ->
                            // Delete message from ViewModel
                            chatViewModel.deleteMessage(messageId)
                            println("Delete message: $messageId")
                        }
                        showDeleteDialog = false
                        selectedMessageForDelete = null
                    }
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        selectedMessageForDelete = null
                    }
                ) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun MessageItem(
    messageWithUser: MessageWithUser,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFromCurrentUser = messageWithUser.isFromCurrentUser
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    // Message bubble state
    var isPressed by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(vertical = 2.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongClick() },
                        onPress = { 
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                }
                .graphicsLayer {
                    scaleX = if (isPressed) 0.95f else 1f
                    scaleY = if (isPressed) 0.95f else 1f
                }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .then(modifier),
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
                    if (isFromCurrentUser) {
                        // WhatsApp style message status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = dateFormat.format(messageWithUser.message.timestamp.toDate()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                            
                            // Message status icons (WhatsApp style)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                if (messageWithUser.message.isRead) {
                                    // Double blue check (read)
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Okundu",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.Blue
                                    )
                                } else {
                                    // Single gray check (sent)
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Gönderildi",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        // For received messages, only show time
                        Text(
                            text = dateFormat.format(messageWithUser.message.timestamp.toDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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

