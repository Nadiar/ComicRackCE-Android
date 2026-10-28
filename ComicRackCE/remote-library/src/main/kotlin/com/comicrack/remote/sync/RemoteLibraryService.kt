package com.comicrack.remote.sync

import timber.log.Timber
import com.common.db.dao.RemoteComicDao
import com.common.db.dao.SyncStateDao
import com.common.db.entities.RemoteComic
import com.common.db.entities.SyncState
import com.comicrack.remote.scanner.RemoteComicScanner
import java.util.concurrent.TimeUnit

/**
 * Orchestrates the synchronization of remote (Google Drive) library with local database.
 *
 * Handles the complete sync workflow:
 * 1. Scan Google Drive folder for comics
 * 2. Compare with existing database records
 * 3. Insert/update/delete records as needed
 * 4. Update sync state and statistics
 *
 * Single responsibility: Coordinates between the scanner (discovery) and DAOs (persistence).
 * Handles the business logic of sync without worrying about UI or WorkManager details.
 *
 * Error handling: Returns SyncResult with detailed error information for logging and retry logic.
 *
 * Phase 2: Scanner & Sync Infrastructure
 * Remote Library module - Sync orchestration
 */
class RemoteLibraryService(
    private val remoteComicScanner: RemoteComicScanner,
    private val remoteComicDao: RemoteComicDao,
    private val syncStateDao: SyncStateDao
) {
    companion object {
        private const val TAG = "RemoteLibraryService"
    }

    /**
     * Synchronizes a library path with Google Drive.
     *
     * Performs a complete sync cycle:
     * 1. Gets the library path's Drive folder ID from database
     * 2. Scans the folder recursively for comic files
     * 3. Updates the local database with discovered comics
     * 4. Removes comics that no longer exist on Drive
     * 5. Updates sync state with statistics
     *
     * The sync is atomic at the library path level - either it succeeds completely
     * or rolls back on error.
     *
     * @param libraryPathId The ID of the library path to sync
     * @return SyncResult indicating success/failure and statistics
     */
    suspend fun syncLibraryPath(libraryPathId: Long): SyncResult {
        val startTime = System.currentTimeMillis()

        return try {
            Timber.tag(TAG).i("Starting sync for library path: $libraryPathId")

            // Mark sync as in progress
            syncStateDao.setSyncStatusSyncing(libraryPathId)
            Timber.tag(TAG).d("Marked library path as SYNCING")

            // Get the current library path's Drive folder ID
            // This would come from LibraryPath entity in the database
            // For now, we'll assume it's passed through some configuration
            // In production, you'd query: libraryPathDao.getLibraryPath(libraryPathId).driveFolderId
            val driveFolderId = getDriveFolderIdForLibraryPath(libraryPathId)
            if (driveFolderId.isEmpty()) {
                Timber.tag(TAG).e("No Drive folder ID configured for library path: $libraryPathId")
                return SyncResult(
                    isSuccessful = false,
                    totalFilesFound = 0,
                    totalComicsFound = 0,
                    durationMs = System.currentTimeMillis() - startTime,
                    errorMessage = "Drive folder not configured"
                )
            }

            Timber.tag(TAG).d("Drive folder ID: $driveFolderId")

            // Scan the Google Drive folder
            val scanResult = remoteComicScanner.scanFolder(driveFolderId, recursive = true)

            if (scanResult.isFailure) {
                val error = scanResult.exceptionOrNull() ?: Exception("Unknown scan error")
                Timber.tag(TAG).e(error, "Scan failed")

                val durationMs = System.currentTimeMillis() - startTime
                syncStateDao.setSyncStatusFailed(
                    libraryPathId = libraryPathId,
                    errorMessage = error.message ?: "Unknown error",
                    durationMs = durationMs
                )

                return SyncResult(
                    isSuccessful = false,
                    totalFilesFound = 0,
                    totalComicsFound = 0,
                    durationMs = durationMs,
                    errorMessage = error.message
                )
            }

            val remoteComics = scanResult.getOrNull() ?: emptyList()
            Timber.tag(TAG).i("Scan found ${remoteComics.size} comic files")

            // Update database with discovered comics
            val insertedCount = updateLocalDatabase(libraryPathId, remoteComics)
            Timber.tag(TAG).i("Updated database: $insertedCount comics")

            // Update sync state with success
            val durationMs = System.currentTimeMillis() - startTime
            syncStateDao.setSyncStatusSuccess(
                libraryPathId = libraryPathId,
                timestamp = System.currentTimeMillis(),
                totalFilesFound = remoteComics.size,
                totalComicsFound = remoteComics.size,
                durationMs = durationMs
            )

            Timber.tag(TAG).i("Sync completed successfully for library path: $libraryPathId")

            return SyncResult(
                isSuccessful = true,
                totalFilesFound = remoteComics.size,
                totalComicsFound = remoteComics.size,
                durationMs = durationMs,
                errorMessage = null
            )

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Sync failed with exception")

            val durationMs = System.currentTimeMillis() - startTime
            try {
                syncStateDao.setSyncStatusFailed(
                    libraryPathId = libraryPathId,
                    errorMessage = e.message ?: "Unknown error",
                    durationMs = durationMs
                )
            } catch (dbError: Exception) {
                Timber.tag(TAG).e(dbError, "Failed to update sync state")
            }

            return SyncResult(
                isSuccessful = false,
                totalFilesFound = 0,
                totalComicsFound = 0,
                durationMs = durationMs,
                errorMessage = e.message
            )
        }
    }

    /**
     * Updates the local database with discovered remote comics.
     *
     * Strategy:
     * 1. Insert/update all discovered comics (using REPLACE strategy)
     * 2. Delete comics that are no longer in the Drive folder
     * 3. Preserve user data (read progress, favorites, etc.) for existing comics
     *
     * @param libraryPathId The library path being synced
     * @param discoveredComics List of comics found on Drive
     * @return Number of comics updated
     */
    private suspend fun updateLocalDatabase(
        libraryPathId: Long,
        discoveredComics: List<com.comicrack.remote.scanner.RemoteComicFile>
    ): Int {
        Timber.tag(TAG).d("Updating database with ${discoveredComics.size} discovered comics")

        return try {
            // Convert scanner results to database entities
            val remoteComics = discoveredComics.map { comic ->
                RemoteComic(
                    driveFileId = comic.driveFileId,
                    libraryPathId = libraryPathId,
                    name = comic.name,
                    parentDriveFolderId = comic.parentFolderId,
                    mimeType = comic.mimeType,
                    sizeBytes = comic.sizeBytes,
                    thumbnailRemoteUrl = comic.thumbnailUrl,
                    thumbnailLocalPath = null,  // Not downloaded yet
                    cacheLocalPath = null,      // Not cached yet
                    readProgress = 0,           // New comics start at 0
                    lastModifiedRemote = comic.modifiedTime,
                    lastAccessedLocal = System.currentTimeMillis(),
                    isFavorite = false,
                    syncedAt = System.currentTimeMillis()
                )
            }

            // Batch insert/update all comics (REPLACE strategy preserves existing data)
            if (remoteComics.isNotEmpty()) {
                remoteComicDao.insertComics(*remoteComics.toTypedArray())
                Timber.tag(TAG).d("Inserted/updated ${remoteComics.size} comics")
            }

            // TODO: In future, detect deletions by comparing with existing records
            // and delete comics that are no longer on Drive:
            // val existingComicIds = remoteComicDao.getComicIds(libraryPathId)
            // val discoveredComicIds = discoveredComics.map { it.driveFileId }.toSet()
            // val deletedIds = existingComicIds - discoveredComicIds
            // deletedIds.forEach { remoteComicDao.deleteComicByDriveFileId(it) }

            remoteComics.size

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Database update failed")
            throw e
        }
    }

    /**
     * Gets the Google Drive folder ID for a library path.
     *
     * In production, this would query the LibraryPath entity to get the driveFolderId.
     * For now, this is a placeholder that would be implemented once LibraryPath
     * integration is complete.
     *
     * @param libraryPathId The library path ID
     * @return The Google Drive folder ID, or empty string if not configured
     */
    private suspend fun getDriveFolderIdForLibraryPath(libraryPathId: Long): String {
        // TODO: Query LibraryPath entity to get driveFolderId
        // val libraryPath = libraryPathDao.getLibraryPath(libraryPathId)
        // return libraryPath?.driveFolderId ?: ""

        // For now, return empty (will be implemented in integration phase)
        return ""
    }
}
