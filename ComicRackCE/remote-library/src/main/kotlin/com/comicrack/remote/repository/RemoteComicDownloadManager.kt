package com.comicrack.remote.repository

import timber.log.Timber
import com.comicrack.remote.cache.RemoteCacheManager
import com.common.db.entities.RemoteComic
import java.io.File

/**
 * Orchestrates on-demand downloading and caching of remote comics for reading.
 *
 * Responsibilities:
 * - Download comic files from Google Drive
 * - Delegate to RemoteCacheManager for actual caching
 * - Update read progress after successful download
 * - Return cached file path to viewer
 * - Handle download errors and retries
 *
 * Download Workflow:
 * 1. Request download for comic
 * 2. Check if already cached
 * 3. If not cached, call RemoteCacheManager.downloadComicFile()
 * 4. On success, update read progress to page 0
 * 5. Return file path to viewer
 * 6. On failure, report error to caller
 *
 * Phase 4: Repository & ViewModel Layer
 * Remote Library module - Download orchestration
 *
 * @property cacheManager Manages actual file downloads and cache persistence
 * @property repository Provides database access for updating read progress
 */
class RemoteComicDownloadManager(
    private val cacheManager: RemoteCacheManager,
    private val repository: RemoteLibraryRepository
) {
    companion object {
        private const val TAG = "RemoteComicDownloadManager"
    }

    /**
     * Downloads a remote comic for reading.
     *
     * This is the main entry point for viewer requests. It handles:
     * - Checking cache first
     * - Downloading if necessary
     * - Updating read progress
     * - Returning the file path
     *
     * The download process is delegated to RemoteCacheManager, which handles
     * the actual API calls and local file persistence.
     *
     * @param comic The RemoteComic to download
     * @param onProgress Optional callback for download progress (0-100%)
     * @return Result<File> containing the downloaded file or error
     */
    suspend fun downloadForReading(
        comic: RemoteComic,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = try {
        Timber.tag(TAG).i("Starting download for reading: ${comic.name} (${comic.driveFileId})")

        // Step 1: Check if already cached
        val cachedFile = cacheManager.getCachedComicPath(comic)
        if (cachedFile != null && cachedFile.exists()) {
            Timber.tag(TAG).d("Comic already cached: ${cachedFile.absolutePath}")
            // Update last accessed time
            repository.updateLastAccessed(comic.driveFileId)
            return Result.success(cachedFile)
        }

        Timber.tag(TAG).d("Comic not in cache, downloading from Drive...")

        // Step 2: Download from Google Drive
        val downloadResult = cacheManager.downloadComicFile(
            comic = comic,
            progressCallback = { progress ->
                Timber.tag(TAG).d("Download progress: $progress%")
                onProgress(progress)
            }
        )

        if (downloadResult.isFailure) {
            Timber.tag(TAG).e(downloadResult.exceptionOrNull(), "Download failed")
            return downloadResult
        }

        val downloadedFile = downloadResult.getOrNull()
            ?: return Result.failure(Exception("Download returned null file"))

        Timber.tag(TAG).d("Download completed: ${downloadedFile.absolutePath}")

        // Step 3: Update database with cache path and reset read progress
        try {
            repository.updateCachePath(comic.driveFileId, downloadedFile.absolutePath)
            repository.updateReadProgress(comic.driveFileId, 0)
            Timber.tag(TAG).d("Database updated for cached comic")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to update database after download, but file is still accessible")
            // Continue anyway - file was downloaded successfully
        }

        // Step 4: Return file path
        Result.success(downloadedFile)

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Error during download for reading")
        Result.failure(e)
    }

    /**
     * Gets or downloads a comic, with automatic cache eviction if needed.
     *
     * This is the internal method used for cache management. It may trigger
     * eviction of other cached comics if the cache is full.
     *
     * @param comic The RemoteComic to get or download
     * @param onProgress Optional callback for download progress
     * @param maxCacheSizeBytes Maximum cache size in bytes
     * @return Result<File> containing the file or error
     */
    suspend fun getOrDownloadComicWithEviction(
        comic: RemoteComic,
        onProgress: (Int) -> Unit = {},
        maxCacheSizeBytes: Long = 5_000_000_000L  // 5GB default
    ): Result<File> = try {
        Timber.tag(TAG).d("Getting or downloading comic with eviction: ${comic.name}")

        // First, attempt eviction if needed
        val evictionResult = cacheManager.evictCacheIfNeeded(maxCacheSizeBytes)
        if (evictionResult.isFailure) {
            Timber.tag(TAG).w(evictionResult.exceptionOrNull(), "Cache eviction encountered error")
            // Continue anyway - download may still succeed
        } else {
            val freedBytes = evictionResult.getOrNull() ?: 0
            if (freedBytes > 0) {
                Timber.tag(TAG).d("Evicted ${freedBytes / 1_000_000}MB from cache")
            }
        }

        // Now download the comic
        downloadForReading(comic, onProgress)

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Error during get or download with eviction")
        Result.failure(e)
    }

    /**
     * Checks if a comic is already cached locally.
     *
     * Useful for UI to show which comics don't require download.
     *
     * @param comic The RemoteComic to check
     * @return true if cached and accessible, false otherwise
     */
    fun isCached(comic: RemoteComic): Boolean {
        val cachedFile = cacheManager.getCachedComicPath(comic)
        return cachedFile != null && cachedFile.exists()
    }

    /**
     * Gets the cached file path if available.
     *
     * @param comic The RemoteComic to check
     * @return File path if cached, null otherwise
     */
    fun getCachedPath(comic: RemoteComic): File? {
        return cacheManager.getCachedComicPath(comic)
    }

    /**
     * Clears the cached file for a comic.
     *
     * Frees up space but allows the comic to be re-downloaded later.
     * Does not affect the read progress in the database.
     *
     * @param comic The RemoteComic to clear cache for
     * @return Result<Boolean> indicating success
     */
    suspend fun clearCache(comic: RemoteComic): Result<Unit> = try {
        Timber.tag(TAG).d("Clearing cache for: ${comic.name}")

        val cachedFile = cacheManager.getCachedComicPath(comic)
        if (cachedFile != null && cachedFile.exists()) {
            val deleted = cachedFile.delete()
            if (deleted) {
                Timber.tag(TAG).d("Cache cleared successfully")
                // Update database to remove cache path
                repository.updateCachePath(comic.driveFileId, "")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete cache file"))
            }
        } else {
            Timber.tag(TAG).d("No cached file to clear")
            Result.success(Unit)
        }

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Error clearing cache")
        Result.failure(e)
    }
}
