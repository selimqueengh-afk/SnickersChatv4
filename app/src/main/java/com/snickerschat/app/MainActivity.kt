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
import com.snickerschat.app.data.repository.FirebaseRepository
import com.snickerschat.app.ui.screens.*
import com.snickerschat.app.ui.theme.SnickersChatTheme
import com.snickerschat.app.ui.viewmodel.*

class MainActivity : ComponentActivity() {
    private lateinit var repository: FirebaseRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
    
    override fun onResume() {
        super.onResume()
        // Set user as online when app becomes active
        repository.updateUserOnlineStatus(true)
    }
    
    override fun onPause() {
        super.onPause()
        // Set user as offline when app goes to background
        repository.updateUserOnlineStatus(false)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Set user as offline when app is destroyed
        repository.updateUserOnlineStatus(false)
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
            val chatViewModel: ChatViewModel = viewModel { ChatViewModel(repository) }
            
            ChatScreen(
                chatRoomId = chatRoomId,
                chatViewModel = chatViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}