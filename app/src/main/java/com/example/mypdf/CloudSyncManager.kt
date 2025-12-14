package com.example.mypdf

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CloudSyncManager(private val context: Context, private val driveService: GoogleDriveService) {

    private val ROOT_FOLDER_NAME = "PentagramApp_Data"
    private val PREFS_NAME = "PentagramSyncPrefs"
    private val KEY_KNOWN_FILES = "known_synced_relative_paths" // Updated key for relative paths

    // Track relative paths: e.g. "manual.pdf", "category1/score.pdf"
    private fun getKnownPaths(): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_KNOWN_FILES, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun saveKnownPaths(paths: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_KNOWN_FILES, paths).apply()
    }

    private fun addToKnown(relativePath: String) {
        val current = getKnownPaths()
        current.add(relativePath)
        saveKnownPaths(current)
    }

    private fun removeFromKnown(relativePath: String) {
        val current = getKnownPaths()
        current.remove(relativePath)
        saveKnownPaths(current)
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        // 1. Root Setup
        // Find or create the root folder in Drive
        val rootFolderId = driveService.findOrCreateFolder(ROOT_FOLDER_NAME) // parentId null for root

        // 2. Load Sync State
        val knownPaths = getKnownPaths()
        val allCurrentPaths = mutableSetOf<String>()

        // 3. Recursive Sync
        // We start syncing from the App's file directory (local root) and the Drive root folder
        syncFolder(context.filesDir, rootFolderId, "", allCurrentPaths)

        // 4. Cleanup Stale Known Entries
        // If a path was "known" but is neither local nor remote after sync, it means it's gone.
        // We should stop tracking it.
        val staleEntries = knownPaths.filter { !allCurrentPaths.contains(it) }
        if (staleEntries.isNotEmpty()) {
            val updated = knownPaths.toMutableSet()
            updated.removeAll(staleEntries.toSet())
            saveKnownPaths(updated)
        }
    }

    /**
     * Recursive function to sync a local folder with a remote folder.
     * @param localDir The local directory File object.
     * @param remoteFolderId The ID of the corresponding folder in Drive.
     * @param relativePathPrefix The relative path up to this folder (e.g. "subfolder/"). Empty for root.
     * @param allCurrentPaths Accumulator for all paths found during this sync session (to clean up stale entries).
     */
    private suspend fun syncFolder(
        localDir: File, 
        remoteFolderId: String, 
        relativePathPrefix: String,
        allCurrentPaths: MutableSet<String>
    ) {
        val knownPaths = getKnownPaths()

        // --- A. Scan Remote Content ---
        val remoteItems = driveService.listFiles(remoteFolderId)
        val remoteMap = remoteItems.associateBy { it.name }

        // --- B. Scan Local Content ---
        val localItems = localDir.listFiles()?.toList() ?: emptyList()
        val localMap = localItems.associateBy { it.name }

        // --- C. Process Remote Items (Downwards) ---
        // For every item in Drive, ensure we have it locally or handle deleted.
        for (rItem in remoteItems) {
            val rName = rItem.name
            val isFolder = rItem.mimeType == "application/vnd.google-apps.folder"

            // Construct relative path for this item
            val itemRelativePath = if (relativePathPrefix.isEmpty()) rName else "$relativePathPrefix/$rName"

            // Track existence
            allCurrentPaths.add(itemRelativePath)

            val localItem = localMap[rName]
            if (localItem == null) {
                // Remote exists, Local MISSING
                if (knownPaths.contains(itemRelativePath)) {
                    // It WAS known, so it must have been deleted locally -> DELETE REMOTE
                    // (Propagate local deletion to Drive)
                    try {
                        driveService.deleteFile(rItem.id) // Works for folders too? Yes usually
                        removeFromKnown(itemRelativePath)
                        allCurrentPaths.remove(itemRelativePath) // It's gone
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    // It was NOT known -> New file from other device -> DOWNLOAD
                    if (isFolder) {
                        // Create local directory
                        val newLocalDir = File(localDir, rName)
                        if (!newLocalDir.exists() && newLocalDir.mkdirs()) {
                            addToKnown(itemRelativePath)
                            // Recurse into this new folder
                            syncFolder(newLocalDir, rItem.id, itemRelativePath, allCurrentPaths)
                        }
                    } else {
                        // Download file
                        val destFile = File(localDir, rName)
                        try {
                            driveService.downloadFile(rItem.id, destFile)
                            rItem.modifiedTime?.value?.let { destFile.setLastModified(it) }
                            addToKnown(itemRelativePath)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                continue
            }

            // Both exist -> SYNCED or UPDATE
            if (isFolder && localItem.isDirectory) {
                addToKnown(itemRelativePath)
                syncFolder(localItem, rItem.id, itemRelativePath, allCurrentPaths)
                continue
            }

            if (!isFolder && localItem.isFile) {
                val remoteTime = rItem.modifiedTime?.value ?: 0L
                val localTime = localItem.lastModified()

                // Simple logic: If difference > 2 seconds (buffer), sync newest
                if (remoteTime > localTime + 2000) {
                    // Remote is newer -> Download
                    try {
                        driveService.downloadFile(rItem.id, localItem)
                        localItem.setLastModified(remoteTime) // Try to sync timestamps
                        addToKnown(itemRelativePath)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (localTime > remoteTime + 2000) {
                    // Local is newer -> Upload
                    try {
                        driveService.updateFile(localItem, rItem.id)
                        addToKnown(itemRelativePath)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    // In sync
                    addToKnown(itemRelativePath)
                }
                continue
            }

            // Type mismatch (folder vs file)
            addToKnown(itemRelativePath)
        }

        // --- D. Process Local Items (Upwards) ---
        // For every item locally, ensure it is in Drive
        for (lItem in localItems) {
            val lName = lItem.name
            val isFolder = lItem.isDirectory
            // Skip non-pdf/json files if strictly required, but for "structure" usually we sync all or specific types
            // For this app, let's sync folders, PDF files, and JSON (annotations/prefs).
            val ext = lItem.extension.lowercase()
            android.util.Log.d("CloudSync", "Checking local file: $lName, ext: $ext, isFolder: $isFolder")
            if (!isFolder && ext != "pdf" && ext != "json") {
                android.util.Log.d("CloudSync", "Skipping $lName (invalid extension)")
                continue
            }

            val itemRelativePath = if (relativePathPrefix.isEmpty()) lName else "$relativePathPrefix/$lName"
            allCurrentPaths.add(itemRelativePath)

            if (!remoteMap.containsKey(lName)) {
                // Local exists, Remote MISSING
                if (knownPaths.contains(itemRelativePath)) {
                    // It WAS known, so it must have been deleted remotely -> DELETE LOCAL
                    // (Propagate remote deletion to local)
                    try {
                        if (lItem.isDirectory) {
                            lItem.deleteRecursively()
                        } else {
                            lItem.delete()
                        }
                        removeFromKnown(itemRelativePath)
                        allCurrentPaths.remove(itemRelativePath)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    // It was NOT known -> New local file -> UPLOAD
                    try {
                        if (isFolder) {
                            // Create remote folder
                            val newFolderId = driveService.findOrCreateFolder(lName, remoteFolderId)
                            addToKnown(itemRelativePath)
                            // Recurse
                            syncFolder(lItem, newFolderId, itemRelativePath, allCurrentPaths)
                        } else {
                            // Upload file
                            driveService.uploadFile(lItem, remoteFolderId)
                            addToKnown(itemRelativePath)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            // If both exist, we already handled it in the remote loop (or they match)
        }
    }

    suspend fun deleteRemoteFile(fileName: String) = withContext(Dispatchers.IO) {
        // NOTE: This simple helper assumes flat structure or simple filename.
        // For deep structures, we'd need the relative path or find it recursively.
        // Given the new recursive logic, this might be better handled by "sync" (marking local deletion then sync).
        // But for immediate feedback:
        
        // Strategy: "Lazy" deletion via Sync is better for consistency.
        // But if we MUST delete immediately:
        // We'd need to find the file ID by path... complex.
        // Let's rely on the robust sync() we just wrote.
        // If the user deletes a file locally (UI action), and then triggers sync, 
        // our sync logic (step D) sees "Local Missing, Remote Exists, Known=Yes" -> Delete Remote.
        // So we might NOT need this explicit method if we trust sync().
        
        // HOWEVER, to be safe and responsive:
        // We can just remove it from "Known" so that valid re-creation is possible,
        // or actually try to delete it.
        // Let's attempt to find it in the root or just trigger a sync.
        
        // If we strictly want to support the "Delete propagation" requirement:
        // The most robust way is: User deletes local file -> App calls sync() -> Sync detects deletion -> Sync deletes remote.
        // So we can change the calling code to just sync().
        
        // For compatibility with existing calls, let's leave a stub or best-effort root delete.
        try {
            val rootId = driveService.findOrCreateFolder(ROOT_FOLDER_NAME)
            val fileId = driveService.findFileId(fileName, rootId)
            if (fileId != null) {
                driveService.deleteFile(fileId)
                // We'd need to know the relative path to remove from known, which is hard from just `fileName`.
                // If we assume flat for this legacy call:
                removeFromKnown(fileName) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
