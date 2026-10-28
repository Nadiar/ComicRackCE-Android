package com.comicrack.core.sync

import com.comicrack.core.storage.drive.DriveApiClient
import com.comicrack.core.storage.drive.GoogleDriveStorageProvider
import com.comicrack.core.storage.provider.StorageFileEntry
import com.comicrack.core.storage.provider.StorageProviderFactory
import com.comicrack.core.storage.provider.StorageType
import com.comicrack.data.database.dao.ComicBookDao
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.database.dao.SyncStateDao
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.LibraryPath
import com.comicrack.data.database.entities.SyncState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates scan + incremental sync for remote library paths.
 *
 * Discovers comic files via [StorageProviderFactory], creates/updates
 * [ComicBook] entities with `storageType = "GOOGLE_DRIVE"`, and
 * updates [SyncState] to track sync history.
 *
 * Phase 5: Remote Sync and Caching (SRC-05-007 v2)
 */
@Singleton
class RemoteSyncService @Inject constructor(
    private val libraryPathDao: LibraryPathDao,
    private val comicDao: ComicBookDao,
    private val syncStateDao: SyncStateDao,
    private val storageProviderFactory: StorageProviderFactory,
) {
    companion object {
        private const val TAG = "RemoteSyncService"
    }

    /**
     * Result of a sync operation.
     */
    data class SyncResult(
        val totalFilesFound: Int = 0,
        val totalComicsFound: Int = 0,
        val newComicsAdded: Int = 0,
        val comicsRemoved: Int = 0,
        val durationMs: Long = 0,
        val error: String? = null,
    ) {
        val isSuccessful: Boolean get() = error == null
    }

    /**
     * Sync all active remote library paths.
     *
     * @return List of sync results, one per path
     */
    suspend fun syncAllRemotePaths(): List<SyncResult> {
        val remotePaths = libraryPathDao.getByStorageType(StorageType.GOOGLE_DRIVE.value)
        Timber.tag(TAG).i("Syncing ${remotePaths.size} remote paths")

        return remotePaths.map { path -> syncPath(path) }
    }

    /**
     * Sync a single library path.
     *
     * @param libraryPath The remote library path to sync
     * @return Sync result with statistics
     */
    suspend fun syncPath(libraryPath: LibraryPath): SyncResult {
        val startTime = System.currentTimeMillis()

        ensureSyncStateExists(libraryPath.id)
        syncStateDao.setSyncStatusSyncing(libraryPath.id)

        val provider = storageProviderFactory.createProvider(libraryPath)
        if (provider == null) {
            val error = "No provider available for ${libraryPath.storageType}"
            Timber.tag(TAG).e(error)
            syncStateDao.setSyncStatusFailed(libraryPath.id, error, elapsed(startTime))
            return SyncResult(error = error, durationMs = elapsed(startTime))
        }

        if (!provider.isAccessible()) {
            val error = "Provider not accessible (not signed in?)"
            Timber.tag(TAG).e(error)
            syncStateDao.setSyncStatusFailed(libraryPath.id, error, elapsed(startTime))
            return SyncResult(error = error, durationMs = elapsed(startTime))
        }

        return try {
            val driveProvider = provider as? GoogleDriveStorageProvider
            val allEntries = mutableListOf<StorageFileEntry>()

            // BFS traversal of the folder tree
            val foldersToScan = mutableListOf(libraryPath.path)
            val processedFolders = mutableSetOf<String>()

            while (foldersToScan.isNotEmpty()) {
                val folderId = foldersToScan.removeFirst()
                if (folderId in processedFolders) continue
                processedFolders.add(folderId)

                val listResult = provider.listFiles(folderId)
                val entries = listResult.getOrElse { error ->
                    Timber.tag(TAG).e(error, "Error listing folder $folderId")
                    continue
                }

                entries.forEach { entry ->
                    if (entry.isDirectory) {
                        foldersToScan.add(entry.identifier)
                    } else if (driveProvider?.isSupportedComicFormat(entry.mimeType, entry.displayName) != false) {
                        allEntries.add(entry)
                    }
                }
            }

            Timber.tag(TAG).i("Found ${allEntries.size} comic files in ${processedFolders.size} folders")

            // Upsert comics and remove stale entries
            val result = upsertComics(libraryPath, allEntries)

            val duration = elapsed(startTime)
            syncStateDao.setSyncStatusSuccess(
                libraryPathId = libraryPath.id,
                timestamp = System.currentTimeMillis(),
                totalFilesFound = processedFolders.size,
                totalComicsFound = allEntries.size,
                durationMs = duration,
            )

            result.copy(durationMs = duration)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Sync failed for path ${libraryPath.id}")
            val duration = elapsed(startTime)
            syncStateDao.setSyncStatusFailed(libraryPath.id, e.message ?: "Unknown error", duration)
            SyncResult(error = e.message, durationMs = duration)
        }
    }

    /**
     * Upsert discovered comics into the database and remove stale entries.
     */
    private suspend fun upsertComics(
        libraryPath: LibraryPath,
        entries: List<StorageFileEntry>,
    ): SyncResult {
        val existingComics = comicDao.getComicsByStorageType(libraryPath.storageType)
        val existingByRemoteId = existingComics.associateBy { it.remoteId }
        val discoveredRemoteIds = entries.map { it.identifier }.toSet()

        var newCount = 0

        for (entry in entries) {
            val existing = existingByRemoteId[entry.identifier]
            if (existing == null) {
                val comic = ComicBook(
                    filePath = "drive://${entry.identifier}",
                    title = entry.displayName.substringBeforeLast('.'),
                    fileName = entry.displayName,
                    fileSize = entry.size,
                    libraryPathId = libraryPath.id,
                    storageType = libraryPath.storageType,
                    remoteId = entry.identifier,
                    thumbnailRemoteUrl = entry.providerSpecific["thumbnailUrl"],
                    syncedAt = System.currentTimeMillis(),
                )
                comicDao.insertComicBook(comic)
                newCount++
            } else {
                // Update syncedAt timestamp for existing entries
                comicDao.insertComicBook(
                    existing.copy(syncedAt = System.currentTimeMillis()),
                )
            }
        }

        // Remove comics no longer present on Drive
        val staleComics = existingComics.filter {
            it.remoteId != null &&
                it.remoteId !in discoveredRemoteIds &&
                it.libraryPathId == libraryPath.id
        }
        staleComics.forEach { comicDao.deleteComicBook(it) }

        Timber.tag(TAG).d("Upsert: $newCount new, ${staleComics.size} removed")

        return SyncResult(
            totalFilesFound = entries.size,
            totalComicsFound = entries.size,
            newComicsAdded = newCount,
            comicsRemoved = staleComics.size,
        )
    }

    private suspend fun ensureSyncStateExists(pathId: Long) {
        val status = syncStateDao.getSyncStatus(pathId)
        if (status == null) {
            syncStateDao.insertSyncState(SyncState(libraryPathId = pathId))
        }
    }

    private fun elapsed(startTime: Long): Long = System.currentTimeMillis() - startTime
}
