package com.nothing.news.util

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val driveScope = DriveScopes.DRIVE_APPDATA
    private val gson = Gson()
    private val fileName = "nothing_news_backup.json"

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(driveScope))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(driveScope))
        credential.selectedAccount = account.account
        
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Nothing News").build()
    }

    suspend fun uploadBackup(account: GoogleSignInAccount, backupData: BackupData): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService(account)
            val jsonContent = gson.toJson(backupData)
            
            // Check if file exists
            val query = "name = '$fileName' and 'appDataFolder' in parents"
            val fileList = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .execute()
            
            val metadata = com.google.api.services.drive.model.File().apply {
                name = fileName
                parents = listOf("appDataFolder")
            }
            
            val content = ByteArrayContent.fromString("application/json", jsonContent)
            
            if (fileList.files.isEmpty()) {
                drive.files().create(metadata, content).execute()
            } else {
                val fileId = fileList.files[0].id
                drive.files().update(fileId, null, content).execute()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun downloadBackup(account: GoogleSignInAccount): BackupData? = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService(account)
            
            val query = "name = '$fileName' and 'appDataFolder' in parents"
            val fileList = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .execute()
            
            if (fileList.files.isEmpty()) return@withContext null
            
            val fileId = fileList.files[0].id
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            val json = outputStream.toString("UTF-8")
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
