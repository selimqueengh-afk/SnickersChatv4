package com.snickerschat.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.snickerschat.app.data.repository.FirebaseRepository
import com.snickerschat.app.ui.screens.*
import com.snickerschat.app.ui.theme.SnickersChatTheme
import com.snickerschat.app.ui.viewmodel.*

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, get FCM token
            getFCMToken()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request notification permission for Android 13+
        requestNotificationPermission()
        
        setContent {
            SnickersChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SnickersChatApp()
                }
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted, get FCM token
                    getFCMToken()
                }
                else -> {
                    // Request permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For older Android versions, get FCM token directly
            getFCMToken()
        }
    }
    
    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                println("FCM Token: $token")
                // Token will be automatically saved by SnickersFirebaseMessagingService
            } else {
                println("Failed to get FCM token: ${task.exception}")
            }
        }
    }
}

@Composable
fun SnickersChatApp() {
    val navController = rememberNavController()
    val repository = remember { FirebaseRepository() }
    
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
            val chatViewModel: ChatViewModel = viewModel { ChatViewModel(repository) }
            
            ChatScreen(
                chatRoomId = chatRoomId,
                chatViewModel = chatViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}