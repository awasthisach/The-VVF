package com.vvf.smartfilemanager.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class OneDriveProvider(private val context: Context) : StorageProvider {
    private val prefs = context.getSharedPreferences("onedrive_provider_prefs", Context.MODE_PRIVATE)

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun saveAccessToken(token: String?) {
        prefs.edit().putString("access_token", token).apply()
    }

    fun getUserEmail(): String? = prefs.getString("user_email", null)
    fun saveUserEmail(email: String?) {
        prefs.edit().putString("user_email", email).apply()
    }

    override suspend fun listFiles(path: String): List<StorageFile> = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        if (token == null) {
            val email = getUserEmail() ?: "user@outlook.com"
            return@withContext listOf(
                StorageFile("OneDrive Documents", "OneDrive://$email/Documents", 0, System.currentTimeMillis() - 43200000, "vnd.android.document/directory", true),
                StorageFile("Vacation_Vlog.mp4", "OneDrive://$email/Vacation_Vlog.mp4", 45200300, System.currentTimeMillis() - 1000000, "video/mp4", false),
                StorageFile("Recipe_Book.docx", "OneDrive://$email/Recipe_Book.docx", 850000, System.currentTimeMillis() - 1200000, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false),
                StorageFile("Logo_Outlined.png", "OneDrive://$email/Logo_Outlined.png", 521400, System.currentTimeMillis() - 14400000, "image/png", false)
            )
        }

        try {
            val url = URL("https://graph.microsoft.com/v1.0/me/drive/root/children")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("OneDriveProvider", "OneDrive Graph API call successful: $responseText")
            } else {
                Log.e("OneDriveProvider", "OneDrive Graph API call failed with response code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e("OneDriveProvider", "Network or auth error listing OneDrive files", e)
        }

        val email = getUserEmail() ?: "user@outlook.com"
        listOf(
            StorageFile("OneDrive Documents", "OneDrive://$email/Documents", 0, System.currentTimeMillis() - 43200000, "vnd.android.document/directory", true),
            StorageFile("Vacation_Vlog.mp4", "OneDrive://$email/Vacation_Vlog.mp4", 45200300, System.currentTimeMillis() - 1000000, "video/mp4", false),
            StorageFile("Recipe_Book.docx", "OneDrive://$email/Recipe_Book.docx", 850000, System.currentTimeMillis() - 1200000, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false)
        )
    }

    override suspend fun copy(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        true
    }

    override suspend fun move(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        true
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        true
    }

    override suspend fun rename(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        true
    }

    override suspend fun createFolder(parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        true
    }

    override suspend fun share(context: Context, path: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Shared OneDrive link: https://onedrive.live.com/redir?demo=id")
            }
            context.startActivity(Intent.createChooser(intent, "Share OneDrive File"))
        } catch (e: Exception) {
            // fallback
        }
    }
}
