package com.example.mypdf.data

import android.content.Context
import android.util.Log
import com.example.mypdf.appPdfDir
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SyncManager {
    private const val TAG = "SyncManager"
    private const val SETTINGS_FILE_NAME = "app_settings.json"

    suspend fun syncAll(context: Context, account: GoogleSignInAccount) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting Sync...")
                syncSettings(context, account)
                syncLibrary(context, account)
                Log.d(TAG, "Sync Completed.")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            }
        }
    }

    private suspend fun syncSettings(context: Context, account: GoogleSignInAccount) {
        val localFile = File(context.filesDir, SETTINGS_FILE_NAME)
        val remoteFiles = GoogleDriveManager.listFiles(context, account)
        val remoteSettings = remoteFiles.find { it.name == SETTINGS_FILE_NAME }

        if (localFile.exists()) {
            if (remoteSettings != null) {
                // Compare timestamps
                if (localFile.lastModified() > remoteSettings.modifiedTime.value) {
                    // Upload Local -> Remote
                    Log.d(TAG, "Uploading Settings (Local newer)")
                    GoogleDriveManager.uploadFile(context, account, localFile, "application/json")
                } else if (remoteSettings.modifiedTime.value > localFile.lastModified()) {
                    // Download Remote -> Local
                    Log.d(TAG, "Downloading Settings (Remote newer)")
                    GoogleDriveManager.downloadFile(context, account, remoteSettings.id, localFile)
                }
            } else {
                // Upload Local -> Remote (New)
                Log.d(TAG, "Uploading Settings (New Remote)")
                GoogleDriveManager.uploadFile(context, account, localFile, "application/json")
            }
        } else if (remoteSettings != null) {
            // Download Remote -> Local (New Device)
            Log.d(TAG, "Downloading Settings (New Local)")
            GoogleDriveManager.downloadFile(context, account, remoteSettings.id, localFile)
        }
    }

    private suspend fun syncLibrary(context: Context, account: GoogleSignInAccount) {
        val rootDir = appPdfDir(context)
        val localFiles = mutableListOf<File>()
        
        // Helper to walk directory
        fun walk(dir: File) {
             dir.listFiles()?.forEach { 
                 if (it.isDirectory) walk(it) 
                 else if (it.extension.equals("pdf", true)) localFiles.add(it)
             }
        }
        walk(rootDir)

        val remoteFiles = GoogleDriveManager.listFiles(context, account)
            .filter { it.name != SETTINGS_FILE_NAME && it.mimeType != "application/vnd.google-apps.folder" } 
            
        // 1. Upload Local Files if missing or newer
        for (local in localFiles) {
            val remote = remoteFiles.find { it.name == local.name }
            if (remote == null) {
                Log.d(TAG, "Uploading ${local.name} (New Remote)")
                GoogleDriveManager.uploadFile(context, account, local, "application/pdf")
            } else if (local.lastModified() > remote.modifiedTime.value) {
                Log.d(TAG, "Uploading ${local.name} (Local newer)")
                GoogleDriveManager.uploadFile(context, account, local, "application/pdf")
            }
        }

        // 2. Download Remote Files if missing or newer
        for (remote in remoteFiles) {
            val local = localFiles.find { it.name == remote.name }
            if (local == null) {
                // Download to default folder
                val defaultDir = File(rootDir, "default")
                if (!defaultDir.exists()) defaultDir.mkdirs()
                val target = File(defaultDir, remote.name)
                Log.d(TAG, "Downloading ${remote.name} (New Local)")
                GoogleDriveManager.downloadFile(context, account, remote.id, target)
            } else if (remote.modifiedTime.value > local.lastModified()) {
                Log.d(TAG, "Downloading ${remote.name} (Remote newer)")
                GoogleDriveManager.downloadFile(context, account, remote.id, local) // Overwrite existing location
            }
        }
    }
}
