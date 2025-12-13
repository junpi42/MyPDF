package com.example.mypdf.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

object GoogleDriveManager {
    private const val TAG = "GoogleDriveManager"
    private const val APP_FOLDER_NAME = "PentagramApp_Data"

    fun getSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    private fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            GsonFactory(),
            credential
        ).setApplicationName("PentagramApp").build()
    }

    private fun getOrCreateAppFolder(service: Drive): String {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$APP_FOLDER_NAME' and trashed = false"
        val list = service.files().list().setQ(query).setSpaces("drive").execute()
        if (list.files.isNotEmpty()) {
            return list.files[0].id
        }
        val folderMetadata = com.google.api.services.drive.model.File()
        folderMetadata.name = APP_FOLDER_NAME
        folderMetadata.mimeType = "application/vnd.google-apps.folder"
        val folder = service.files().create(folderMetadata).setFields("id").execute()
        return folder.id
    }

    suspend fun uploadFile(context: Context, account: GoogleSignInAccount, file: File, mimeType: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context, account)
                val folderId = getOrCreateAppFolder(service)
                
                // Check if file exists to update or create
                val query = "name = '${file.name}' and '$folderId' in parents and trashed = false"
                val existing = service.files().list().setQ(query).setSpaces("drive").execute()
                
                val fileMetadata = com.google.api.services.drive.model.File()
                fileMetadata.name = file.name
                
                val mediaContent = com.google.api.client.http.FileContent(mimeType, file)
                
                if (existing.files.isNotEmpty()) {
                    // Update
                    val fileId = existing.files[0].id
                    val updated = service.files().update(fileId, null, mediaContent).setFields("id").execute()
                    Log.d(TAG, "File updated: ${updated.id}")
                    updated.id
                } else {
                    // Create
                    fileMetadata.parents = listOf(folderId)
                    val uploaded = service.files().create(fileMetadata, mediaContent).setFields("id").execute()
                    Log.d(TAG, "File created: ${uploaded.id}")
                    uploaded.id
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
                null
            }
        }
    }
    
    suspend fun deleteFile(context: Context, account: GoogleSignInAccount, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context, account)
                val folderId = getOrCreateAppFolder(service)
                val query = "name = '$fileName' and '$folderId' in parents and trashed = false"
                val list = service.files().list().setQ(query).setSpaces("drive").execute()
                
                if (list.files.isNotEmpty()) {
                    for (f in list.files) {
                        service.files().delete(f.id).execute()
                        Log.d(TAG, "Deleted remote file: ${f.name} (${f.id})")
                    }
                    true
                } else {
                    Log.d(TAG, "File not found remotely: $fileName")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
                false
            }
        }
    }

    suspend fun listFiles(context: Context, account: GoogleSignInAccount): List<com.google.api.services.drive.model.File> {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context, account)
                val folderId = getOrCreateAppFolder(service)
                val query = "'$folderId' in parents and trashed = false"
                
                val result = service.files().list()
                    .setQ(query)
                    .setPageSize(100)
                    .setFields("nextPageToken, files(id, name, modifiedTime, size)")
                    .execute()
                result.files ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "List failed", e)
                emptyList()
            }
        }
    }
    
    suspend fun downloadFile(context: Context, account: GoogleSignInAccount, fileId: String, targetFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context, account)
                val outputStream = java.io.FileOutputStream(targetFile)
                service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                outputStream.close()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                false
            }
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
         val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
         GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener { onComplete() }
    }
}
