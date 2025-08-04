package com.snickerschat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.snickerschat.app.data.repository.FirebaseRepository
import com.snickerschat.app.ui.screens.*
import com.snickerschat.app.ui.theme.SnickersChatTheme
import com.snickerschat.app.ui.viewmodel.*
import com.snickerschat.app.config.CloudinaryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var repository: FirebaseRepository
    private val scope = CoroutineScope(Dispatchers.Main)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            println("All permissions granted")
        } else {
            println("Some permissions denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request permissions
        requestPermissions()
        
        // Initialize Cloudinary
        CloudinaryConfig.init(this)
        
        repository = FirebaseRepository()
        
        setContent {
            SnickersChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SnickersChatApp(repository)
                }
            }
        }
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        // Microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Storage permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Set user as online when app becomes active
        println("MainActivity: DEBUG - onResume called")
        val currentUser = repository.getCurrentUser()
        println("MainActivity: DEBUG - Current user: ${currentUser?.id}")
        
        // Add delay to avoid conflicts with signIn
        scope.launch {
            try {
                delay(1000) // Wait 1 second to avoid conflicts
                repository.updateUserOnlineStatus(true)
                println("MainActivity: DEBUG - Successfully set user as online")
                
                // Save FCM token
                currentUser?.id?.let { userId ->
                    repository.getFCMToken().onSuccess { token ->
                        repository.saveFCMToken(userId, token)
                        println("MainActivity: DEBUG - FCM token saved")
                    }.onFailure { exception ->
                        println("MainActivity: ERROR - Failed to get FCM token: ${exception.message}")
                    }
                }
            } catch (e: Exception) {
                println("MainActivity: ERROR - Failed to set user as online: ${e.message}")
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Set user as offline when app goes to background
        println("MainActivity: DEBUG - onStop called")
        val currentUser = repository.getCurrentUser()
        println("MainActivity: DEBUG - Current user: ${currentUser?.id}")
        scope.launch {
            try {
                repository.updateUserOnlineStatus(false)
                println("MainActivity: DEBUG - Successfully set user as offline")
            } catch (e: Exception) {
                println("MainActivity: ERROR - Failed to set user as offline: ${e.message}")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Set user as offline when app is destroyed
        println("MainActivity: DEBUG - onDestroy called")
        val currentUser = repository.getCurrentUser()
        println("MainActivity: DEBUG - Current user: ${currentUser?.id}")
        scope.launch {
            try {
                repository.updateUserOnlineStatus(false)
                println("MainActivity: DEBUG - Successfully set user as offline in onDestroy")
            } catch (e: Exception) {
                println("MainActivity: ERROR - Failed to set user as offline in onDestroy: ${e.message}")
            }
        }
    }
}

@Composable
fun SnickersChatApp(repository: FirebaseRepository) {
    val navController = rememberNavController()
    
    // ViewModels
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(repository) }
    val chatListViewModel: ChatListViewModel = viewModel { ChatListViewModel(repository) }
    val friendsViewModel: FriendsViewModel = viewModel { FriendsViewModel(repository) }
    
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            val loginState by authViewModel.loginState.collectAsState()
            
            // Check if user is already logged in
            LaunchedEffect(Unit) {
                authViewModel.checkCurrentUser()
            }
            
            LoginScreen(
                loginState = loginState,
                onSignUp = { email, password, username ->
                    authViewModel.signUpWithEmail(email, password, username)
                },
                onSignIn = { email, password ->
                    authViewModel.signInWithEmail(email, password)
                },
                onSignInAnonymously = {
                    authViewModel.signInAnonymously()
                }
            )
            
            // Navigate to main app when logged in
            LaunchedEffect(loginState.isLoggedIn) {
                if (loginState.isLoggedIn) {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
        }
        
        composable("main") {
            MainScreen(
                navController = navController,
                chatListViewModel = chatListViewModel,
                friendsViewModel = friendsViewModel,
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate("login") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
        
        composable("chat/{chatRoomId}") { backStackEntry ->
            val chatRoomId = backStackEntry.arguments?.getString("chatRoomId") ?: ""
            val context = LocalContext.current
            val chatViewModel: ChatViewModel = viewModel { ChatViewModel(repository, context) }
            
            ChatScreen(
                chatRoomId = chatRoomId,
                chatViewModel = chatViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}