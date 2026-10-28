package com.comicrack.remote.ui

import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicrack.remote.repository.RemoteComicDownloadManager
import com.comicrack.remote.repository.RemoteLibraryRepository
import com.comicrack.remote.sync.SyncRepository
import com.common.db.entities.RemoteComic
import com.common.db.entities.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for managing remote library UI state and interactions.
 *
 * Responsibilities:
 * - Expose reactive StateFlows for UI consumption
 * - Handle folder selection and filtering
 * - Orchestrate comic downloads
 * - Track download progress and sync status
 * - Manage read progress and favorites
 * - Trigger manual sync requests
 *
 * State Flows:
 * - remoteComics: List of comics in selected folder (or all if none selected)
 * - selectedFolder: Currently selected Google Drive folder ID
 * - isDownloading: Whether a download is in progress
 * - downloadProgress: Current download progress (0-100%)
 * - syncStatus: Current synchronization status
 *
 * Phase 4: Repository & ViewModel Layer
 * Remote Library module - MVVM ViewModel with Hilt DI
 *
 * @property remoteLibraryRepository Data access for remote comics
 * @property downloadManager Orchestrates comic downloads
 * @property syncRepository Manages synchronization state
 */
@HiltViewModel
class RemoteLibraryViewModel @Inject constructor(
    private val remoteLibraryRepository: RemoteLibraryRepository,
    private val downloadManager: RemoteComicDownloadManager,
    private val syncRepository: SyncRepository
) : ViewModel() {

    companion object {
        private const val TAG = "RemoteLibraryViewModel"
    }

    // Mutable state for UI-controlled selections
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    // Reactive comics based on selected folder
    /**
     * Remote comics filtered by selected folder.
     * Shows all comics if no folder is selected.
     * Updates automatically when folder selection or database changes.
     */
    val remoteComics: StateFlow<List<RemoteComic>> = _selectedFolder
        .flatMapLatest { folderId ->
            if (folderId != null) {
                Timber.tag(TAG).d("Loading comics for folder: $folderId")
                remoteLibraryRepository.getComicsInFolder(folderId)
            } else {
                Timber.tag(TAG).d("Loading all remote comics")
                remoteLibraryRepository.getAllRemoteComics()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Sync status (placeholder - would be connected to SyncRepository in full implementation)
    private val _syncStatus = MutableStateFlow<SyncState?>(null)
    val syncStatus: StateFlow<SyncState?> = _syncStatus.asStateFlow()

    /**
     * Count of downloaded comics for UI display.
     */
    val downloadedComicCount: StateFlow<Int> = remoteLibraryRepository.getDownloadedComicCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /**
     * Total count of remote comics for UI display.
     */
    val totalComicCount: StateFlow<Int> = remoteLibraryRepository.getRemoteComicCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /**
     * Favorite comics for dedicated favorites view.
     */
    val favoriteComics: StateFlow<List<RemoteComic>> = remoteLibraryRepository.getFavoriteComics()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ===== Folder Selection =====

    /**
     * Selects a folder to filter comics.
     *
     * Updates the remoteComics StateFlow to show only comics in this folder.
     * Pass null to show all comics across all folders.
     *
     * @param folderId Google Drive folder ID, or null for all comics
     */
    fun selectFolder(folderId: String?) {
        Timber.tag(TAG).d("Selecting folder: $folderId")
        _selectedFolder.value = folderId
    }

    // ===== Download Operations =====

    /**
     * Downloads a comic for reading.
     *
     * Shows download progress via downloadProgress StateFlow.
     * Returns the file path for the viewer to open.
     *
     * @param comic The RemoteComic to download
     * @param onComplete Callback when download finishes with file path (or null on error)
     */
    fun downloadComicForReading(comic: RemoteComic, onComplete: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            _downloadProgress.value = 0

            try {
                Timber.tag(TAG).i("Downloading comic for reading: ${comic.name}")

                val result = downloadManager.downloadForReading(
                    comic = comic,
                    onProgress = { progress ->
                        _downloadProgress.value = progress
                        Timber.tag(TAG).d("Download progress: $progress%")
                    }
                )

                if (result.isSuccess) {
                    val file = result.getOrNull()
                    Timber.tag(TAG).i("Download completed successfully: ${file?.absolutePath}")
                    onComplete(file)
                } else {
                    Timber.tag(TAG).e(result.exceptionOrNull(), "Download failed")
                    onComplete(null)
                }

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error downloading comic")
                onComplete(null)
            } finally {
                _isDownloading.value = false
                _downloadProgress.value = 0
            }
        }
    }

    /**
     * Checks if a comic is already cached without downloading.
     *
     * @param comic The RemoteComic to check
     * @return true if cached locally
     */
    fun isComicCached(comic: RemoteComic): Boolean {
        return downloadManager.isCached(comic)
    }

    /**
     * Gets the cached file path if available.
     *
     * @param comic The RemoteComic to check
     * @return File path if cached, null otherwise
     */
    fun getCachedPath(comic: RemoteComic): File? {
        return downloadManager.getCachedPath(comic)
    }

    // ===== Read Progress =====

    /**
     * Updates read progress for a comic.
     *
     * Called by the viewer to save the current page position.
     *
     * @param comic The RemoteComic being read
     * @param pageNumber The current page (0-indexed)
     */
    fun updateReadProgress(comic: RemoteComic, pageNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.tag(TAG).d("Updating read progress for ${comic.name}: page $pageNumber")
                remoteLibraryRepository.updateReadProgress(comic.driveFileId, pageNumber)
                Timber.tag(TAG).d("Read progress updated")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error updating read progress")
            }
        }
    }

    // ===== Favorite Management =====

    /**
     * Toggles favorite status for a comic.
     *
     * @param comic The RemoteComic to toggle
     */
    fun toggleFavorite(comic: RemoteComic) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newFavoriteStatus = !comic.isFavorite
                Timber.tag(TAG).d("Toggling favorite for ${comic.name}: $newFavoriteStatus")
                remoteLibraryRepository.toggleFavorite(comic.driveFileId, newFavoriteStatus)
                Timber.tag(TAG).d("Favorite status toggled")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error toggling favorite")
            }
        }
    }

    /**
     * Checks if a comic is favorited.
     *
     * @param comic The RemoteComic to check
     * @return true if comic is favorited
     */
    fun isFavorited(comic: RemoteComic): Boolean {
        return comic.isFavorite
    }

    // ===== Synchronization =====

    /**
     * Requests a manual synchronization of the remote library.
     *
     * Enqueues a sync job with WorkManager to scan Google Drive for new/updated comics
     * and update the local database.
     *
     * @param libraryPathId The library path ID to sync
     */
    fun requestSync(libraryPathId: Long) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).i("Requesting sync for library path: $libraryPathId")
                syncRepository.enqueueSyncJob(libraryPathId)
                Timber.tag(TAG).i("Sync job enqueued")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error requesting sync")
            }
        }
    }

    /**
     * Cancels a pending or running sync job.
     *
     * @param libraryPathId The library path ID
     */
    fun cancelSync(libraryPathId: Long) {
        viewModelScope.launch {
            try {
                Timber.tag(TAG).i("Cancelling sync for library path: $libraryPathId")
                syncRepository.cancelSyncJob(libraryPathId)
                Timber.tag(TAG).i("Sync job cancelled")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error cancelling sync")
            }
        }
    }

    /**
     * Observes sync status for a library path.
     *
     * @param libraryPathId The library path ID
     * @return StateFlow emitting SyncState updates
     */
    fun observeSyncStatus(libraryPathId: Long): StateFlow<SyncState?> {
        Timber.tag(TAG).d("Observing sync status for library path: $libraryPathId")
        return syncRepository.getSyncStateFlow(libraryPathId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }

    // ===== Cache Management =====

    /**
     * Clears the cached file for a comic to free up space.
     *
     * @param comic The RemoteComic to clear cache for
     * @param onComplete Callback when operation completes
     */
    fun clearComicCache(comic: RemoteComic, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.tag(TAG).d("Clearing cache for: ${comic.name}")
                val result = downloadManager.clearCache(comic)
                onComplete(result.isSuccess)
                if (result.isSuccess) {
                    Timber.tag(TAG).d("Cache cleared successfully")
                } else {
                    Timber.tag(TAG).e(result.exceptionOrNull(), "Cache clear failed")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error clearing cache")
                onComplete(false)
            }
        }
    }

    /**
     * Updates the last accessed time for a comic (used for LRU eviction).
     *
     * @param comic The RemoteComic being accessed
     */
    fun recordComicAccess(comic: RemoteComic) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                remoteLibraryRepository.updateLastAccessed(comic.driveFileId)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Error recording comic access")
                // Non-critical, don't fail
            }
        }
    }
}
