package com.comicrack.remote.repository

import timber.log.Timber
import com.common.db.dao.RemoteComicDao
import com.common.db.dao.SyncStateDao
import com.common.db.entities.RemoteComic
import kotlinx.coroutines.flow.Flow

/**
 * Repository for querying remote comic data from the database.
 *
 * Single source of truth for remote library data. Provides reactive Flows
 * for UI consumption and suspend functions for mutations.
 *
 * Responsibilities:
 * - Query remote comics by folder, favorite status, or individual ID
 * - Update read progress and favorite status
 * - Expose data as Flows for reactive UI updates
 *
 * Phase 4: Repository & ViewModel Layer
 * Remote Library module - Data access abstraction
 *
 * @property remoteComicDao DAO for querying RemoteComic entities
 * @property syncStateDao DAO for querying SyncState entities
 */
class RemoteLibraryRepository(
    private val remoteComicDao: RemoteComicDao,
    private val syncStateDao: SyncStateDao
) {
    companion object {
        private const val TAG = "RemoteLibraryRepository"
    }

    /**
     * Gets all remote comics in a specific folder as a reactive Flow.
     *
     * Useful for displaying comics in a folder view.
     * Updates automatically when database changes.
     *
     * @param folderId Google Drive folder ID
     * @return Flow emitting list of RemoteComic in the folder
     */
    fun getComicsInFolder(folderId: String): Flow<List<RemoteComic>> {
        Timber.tag(TAG).d("Getting comics in folder: $folderId")
        return remoteComicDao.getComicsByFolderId(folderId)
    }

    /**
     * Gets all remote comics as a reactive Flow.
     *
     * Useful for displaying entire library view.
     * Updates automatically when database changes.
     *
     * @return Flow emitting list of all RemoteComic entities
     */
    fun getAllRemoteComics(): Flow<List<RemoteComic>> {
        Timber.tag(TAG).d("Getting all remote comics")
        return remoteComicDao.observeAllRemoteComics()
    }

    /**
     * Gets a specific remote comic by Drive file ID as a reactive Flow.
     *
     * Useful for detail view or verification after download.
     *
     * @param driveFileId Google Drive file ID
     * @return Flow emitting RemoteComic or null if not found
     */
    fun getComicById(driveFileId: String): Flow<RemoteComic?> {
        Timber.tag(TAG).d("Getting comic by ID: $driveFileId")
        return remoteComicDao.getComicById(driveFileId)
    }

    /**
     * Gets all favorited remote comics as a reactive Flow.
     *
     * Useful for dedicated favorites view.
     *
     * @return Flow emitting list of favorited RemoteComic entities
     */
    fun getFavoriteComics(): Flow<List<RemoteComic>> {
        Timber.tag(TAG).d("Getting favorite comics")
        return remoteComicDao.getFavoriteComics()
    }

    /**
     * Updates read progress for a remote comic.
     *
     * Records the last page the user read, allowing them to resume
     * from where they left off.
     *
     * @param driveFileId Google Drive file ID
     * @param pageNumber The page number to save (0-indexed)
     */
    suspend fun updateReadProgress(driveFileId: String, pageNumber: Int) {
        return try {
            Timber.tag(TAG).d("Updating read progress for $driveFileId: page $pageNumber")
            remoteComicDao.updateReadProgress(driveFileId, pageNumber)
            Timber.tag(TAG).d("Read progress updated successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating read progress")
            throw e
        }
    }

    /**
     * Toggles favorite status for a remote comic.
     *
     * @param driveFileId Google Drive file ID
     * @param isFavorite New favorite status
     */
    suspend fun toggleFavorite(driveFileId: String, isFavorite: Boolean) {
        return try {
            Timber.tag(TAG).d("Toggling favorite for $driveFileId: $isFavorite")
            remoteComicDao.updateFavorite(driveFileId, isFavorite)
            Timber.tag(TAG).d("Favorite status toggled successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error toggling favorite")
            throw e
        }
    }

    /**
     * Updates the last accessed timestamp for a remote comic.
     *
     * Used for LRU cache eviction and tracking user activity.
     *
     * @param driveFileId Google Drive file ID
     */
    suspend fun updateLastAccessed(driveFileId: String) {
        return try {
            Timber.tag(TAG).d("Updating last accessed time for $driveFileId")
            remoteComicDao.updateLastAccessed(driveFileId, System.currentTimeMillis())
            Timber.tag(TAG).d("Last accessed time updated successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating last accessed time")
            throw e
        }
    }

    /**
     * Updates the cached file path for a remote comic.
     *
     * Called after successful download to record where the file is cached.
     *
     * @param driveFileId Google Drive file ID
     * @param cachePath Local file system path to cached comic
     */
    suspend fun updateCachePath(driveFileId: String, cachePath: String) {
        return try {
            Timber.tag(TAG).d("Updating cache path for $driveFileId: $cachePath")
            remoteComicDao.updateCachePath(driveFileId, cachePath)
            Timber.tag(TAG).d("Cache path updated successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating cache path")
            throw e
        }
    }

    /**
     * Gets the total count of remote comics in the database.
     *
     * Useful for displaying statistics or empty state UI.
     *
     * @return Flow emitting the total count of remote comics
     */
    fun getRemoteComicCount(): Flow<Int> {
        Timber.tag(TAG).d("Getting remote comic count")
        return remoteComicDao.getComicCount()
    }

    /**
     * Gets the total count of downloaded (cached) remote comics.
     *
     * Useful for displaying cache usage statistics.
     *
     * @return Flow emitting the count of downloaded comics
     */
    fun getDownloadedComicCount(): Flow<Int> {
        Timber.tag(TAG).d("Getting downloaded comic count")
        return remoteComicDao.getDownloadedComicCount()
    }
}
