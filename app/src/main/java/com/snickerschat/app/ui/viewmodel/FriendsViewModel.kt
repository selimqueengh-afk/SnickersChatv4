package com.snickerschat.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snickerschat.app.data.repository.FirebaseRepository
import com.snickerschat.app.ui.state.FriendsState
import com.snickerschat.app.ui.state.FriendRequestWithUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val repository: FirebaseRepository
) : ViewModel() {
    
    private val _friendsState = MutableStateFlow(FriendsState())
    val friendsState: StateFlow<FriendsState> = _friendsState.asStateFlow()
    
    fun loadFriendRequests() {
        viewModelScope.launch {
            _friendsState.value = _friendsState.value.copy(isLoading = true, error = null)
            
            repository.getFriendRequests()
                .onSuccess { requests ->
                    // Convert requests to FriendRequestWithUser
                    val requestsWithUser = mutableListOf<FriendRequestWithUser>()
                    for (request in requests) {
                        repository.getUser(request.senderId)
                            .onSuccess { user ->
                                requestsWithUser.add(FriendRequestWithUser(request = request, sender = user))
                            }
                    }
                    
                    _friendsState.value = _friendsState.value.copy(
                        friendRequests = requestsWithUser,
                        isLoading = false
                    )
                }
                .onFailure { exception ->
                    _friendsState.value = _friendsState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Arkadaşlık istekleri yüklenemedi"
                    )
                }
        }
    }
    
    fun searchUsers(query: String) {
        if (query.trim().isEmpty()) {
            _friendsState.value = _friendsState.value.copy(
                searchResults = emptyList(),
                searchQuery = ""
            )
            return
        }
        
        viewModelScope.launch {
            _friendsState.value = _friendsState.value.copy(
                searchQuery = query,
                isLoading = true,
                error = null
            )
            
            repository.searchUsers(query.trim())
                .onSuccess { users ->
                    _friendsState.value = _friendsState.value.copy(
                        searchResults = users,
                        isLoading = false
                    )
                }
                .onFailure { exception ->
                    _friendsState.value = _friendsState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Kullanıcı arama başarısız"
                    )
                }
        }
    }
    
    fun sendFriendRequest(receiverId: String) {
        viewModelScope.launch {
            repository.sendFriendRequest(receiverId)
                .onSuccess {
                    // Refresh search results
                    val currentQuery = _friendsState.value.searchQuery
                    if (currentQuery.isNotEmpty()) {
                        searchUsers(currentQuery)
                    }
                }
                .onFailure { exception ->
                    _friendsState.value = _friendsState.value.copy(
                        error = exception.message ?: "Arkadaşlık isteği gönderilemedi"
                    )
                }
        }
    }
    
    fun acceptFriendRequest(requestId: String, onChatRoomCreated: () -> Unit = {}) {
        viewModelScope.launch {
            repository.acceptFriendRequest(requestId)
                .onSuccess {
                    // Refresh friend requests
                    loadFriendRequests()
                    // Notify that chat room was created
                    onChatRoomCreated()
                }
                .onFailure { exception ->
                    _friendsState.value = _friendsState.value.copy(
                        error = exception.message ?: "Arkadaşlık isteği kabul edilemedi"
                    )
                }
        }
    }
    
    fun declineFriendRequest(requestId: String) {
        viewModelScope.launch {
            repository.declineFriendRequest(requestId)
                .onSuccess {
                    // Refresh friend requests
                    loadFriendRequests()
                }
                .onFailure { exception ->
                    _friendsState.value = _friendsState.value.copy(
                        error = exception.message ?: "Arkadaşlık isteği reddedilemedi"
                    )
                }
        }
    }
    
    fun clearError() {
        _friendsState.value = _friendsState.value.copy(error = null)
    }
    
    fun clearSearch() {
        _friendsState.value = _friendsState.value.copy(
            searchResults = emptyList(),
            searchQuery = ""
        )
    }
}