package com.example.mypdf

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Collections

class GoogleDriveService(context: Context, account: GoogleSignInAccount) {

    private val driveService: Drive

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        
        driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("PentagramApp")
        .build()
    }

    suspend fun findOrCreateFolder(folderName: String, parentId: String? = null): String = withContext(Dispatchers.IO) {
        // 1. Search for existing folder
        val query = if (parentId == null) {
            "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false and 'root' in parents"
        } else {
            "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false and '$parentId' in parents"
        }
        
        val fileList = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("nextPageToken, files(id, name)")
            .execute()

        if (fileList.files.isNotEmpty()) {
            return@withContext fileList.files[0].id
        }

        // 2. Create if not found
        val folderMetadata = File()
        folderMetadata.name = folderName
        folderMetadata.mimeType = "application/vnd.google-apps.folder"
        if (parentId != null) {
            folderMetadata.parents = listOf(parentId)
        }

        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()

        return@withContext folder.id
    }

    suspend fun findFileId(fileName: String, folderId: String): String? = withContext(Dispatchers.IO) {
        val query = "'$folderId' in parents and name = '$fileName' and trashed = false"
        val fileList = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()

        if (fileList.files.isNotEmpty()) {
            return@withContext fileList.files[0].id
        }
        return@withContext null
    }

    suspend fun listFiles(folderId: String): List<File> = withContext(Dispatchers.IO) {
        // We want folders AND files now
        val query = "'$folderId' in parents and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("nextPageToken, files(id, name, mimeType, modifiedTime, size)")
            .execute()
        return@withContext result.files ?: emptyList()
    }

    suspend fun uploadFile(localFile: java.io.File, folderId: String): String = withContext(Dispatchers.IO) {
        // Check if file exists to update or create new
        val query = "'$folderId' in parents and name = '${localFile.name}' and trashed = false"
        val existingFiles = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()

        val fileMetadata = File()
        fileMetadata.name = localFile.name
        
        val mediaContent = FileContent("application/pdf", localFile) // Assuming PDFs mostly

        if (existingFiles.files.isNotEmpty()) {
            // Update
            val fileId = existingFiles.files[0].id
            val updatedFile = driveService.files().update(fileId, fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            return@withContext updatedFile.id
        } else {
            // Create
            fileMetadata.parents = Collections.singletonList(folderId)
            val newFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            return@withContext newFile.id
        }
    }

    suspend fun downloadFile(fileId: String, destFile: java.io.File) = withContext(Dispatchers.IO) {
        val outputStream = FileOutputStream(destFile)
        driveService.files().get(fileId)
            .executeMediaAndDownloadTo(outputStream)
        outputStream.flush()
        outputStream.close()
    }
    
    suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        driveService.files().delete(fileId).execute()
    }

    suspend fun updateFile(localFile: java.io.File, fileId: String): String = withContext(Dispatchers.IO) {
        val mediaContent = FileContent("application/pdf", localFile)
        val fileMetadata = File()
        val updatedFile = driveService.files().update(fileId, fileMetadata, mediaContent)
            .setFields("id, modifiedTime")
            .execute()
        return@withContext updatedFile.id
    }
}
