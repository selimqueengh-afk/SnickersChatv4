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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            
            LoginScreen(
                loginState = loginState,
                onUsernameChanged = { username ->
                    // Handle username change
                },
                onLoginClick = {
                    authViewModel.createUser("User${System.currentTimeMillis()}")
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
                friendsViewModel = friendsViewModel
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