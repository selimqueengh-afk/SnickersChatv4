package com.snickerschat.app.service

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.snickerschat.app.data.api.ApiClient
import com.snickerschat.app.data.api.LatestVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class UpdateManager(private val context: Context) {
    
    suspend fun checkForUpdates(): LatestVersion? {
        return try {
            val response = ApiClient.backendApi.checkAppVersion()
            if (response.isSuccessful) {
                val appVersionResponse = response.body()
                val currentVersionCode = getCurrentVersionCode()
                
                if (appVersionResponse != null && 
                    appVersionResponse.latestVersion.versionCode > currentVersionCode) {
                    appVersionResponse.latestVersion
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error checking for updates: ${e.message}")
            null
        }
    }
    
    fun downloadUpdate(downloadUrl: String, versionName: String): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("SnickersChat Güncelleme")
            setDescription("Versiyon $versionName indiriliyor...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SnickersChat-$versionName.apk")
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setAllowedOverRoaming(false)
        }
        
        return downloadManager.enqueue(request)
    }
    
    fun installUpdate(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                ),
                "application/vnd.android.package-archive"
            )
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    private fun getCurrentVersionCode(): Int {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (e: Exception) {
            1
        }
    }
}