package com.snickerschat.app.data.api

import retrofit2.Response
import retrofit2.http.*

interface BackendApi {
    
    @POST("api/send-notification")
    suspend fun sendNotification(
        @Body request: NotificationRequest
    ): Response<NotificationResponse>
    
    @GET("api/user/{userId}/token")
    suspend fun getUserToken(
        @Path("userId") userId: String
    ): Response<TokenResponse>
    
    @POST("api/user/{userId}/token")
    suspend fun updateUserToken(
        @Path("userId") userId: String,
        @Body request: TokenUpdateRequest
    ): Response<TokenUpdateResponse>
    
    @GET("api/app/version")
    suspend fun checkAppVersion(): Response<AppVersionResponse>
}

data class NotificationRequest(
    val receiverId: String,
    val senderId: String,
    val senderName: String,
    val message: String,
    val chatRoomId: String
)

data class NotificationResponse(
    val success: Boolean,
    val messageId: String,
    val message: String
)

data class TokenResponse(
    val userId: String,
    val fcmToken: String?
)

data class TokenUpdateRequest(
    val fcmToken: String
)

data class TokenUpdateResponse(
    val success: Boolean,
    val message: String
)

data class AppVersionResponse(
    val success: Boolean,
    val currentVersion: String,
    val latestVersion: LatestVersion
)

data class LatestVersion(
    val version: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: List<String>,
    val isForceUpdate: Boolean,
    val minVersion: String
)