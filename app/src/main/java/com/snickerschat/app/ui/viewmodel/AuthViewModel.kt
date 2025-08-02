package com.snickerschat.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snickerschat.app.data.repository.FirebaseRepository
import com.snickerschat.app.ui.state.LoginState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: FirebaseRepository
) : ViewModel() {
    
    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()
    
    fun signInAnonymously() {
        viewModelScope.launch {
            _loginState.value = _loginState.value.copy(isLoading = true, error = null)
            
            repository.signInAnonymously()
                .onSuccess { userId ->
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                }
                .onFailure { exception ->
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Giriş başarısız"
                    )
                }
        }
    }
    
    fun createUser(username: String) {
        viewModelScope.launch {
            _loginState.value = _loginState.value.copy(isLoading = true, error = null)
            
            repository.createUser(username)
                .onSuccess { user ->
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        user = user
                    )
                }
                .onFailure { exception ->
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Kullanıcı oluşturulamadı"
                    )
                }
        }
    }
    
    fun clearError() {
        _loginState.value = _loginState.value.copy(error = null)
    }
}