package com.comicrack.remote.cache

import android.content.Context
import timber.log.Timber
import com.comicrack.remote.api.DriveApiClient
import com.common.db.dao.RemoteComicDao
import com.common.db.entities.RemoteComic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages downloading and caching of remote comic files from Google Drive.
 *
 * Responsibilities:
 * - Download comic files on-demand
 * - Download and cache thumbnails
 * - Manage local cache directory
 * - Trigger LRU cache eviction when limit exceeded
 * - Update database with cache paths and access times
 *
 * Cache Storage:
 * - Uses context.cacheDir for app-private storage
 * - Automatically cleared by system when app is uninstalled
 * - Safe for large files (not subject to storage restrictions)
 *
 * Flow for getting a comic:
 * 1. Check if already cached (fast path)
 * 2. If not cached, check available space
 * 3. Evict LRU files if needed
 * 4. Download file
 * 5. Update database with cache path
 *
 * Phase 3: Caching & Download Management
 * Remote Library module - Comic download and cache management
 */
class RemoteCacheManager(
    private val context: Context,
    private val driveApiClient: DriveApiClient,
    private val remoteComicDao: RemoteComicDao,
    private val evictionPolicy: CacheEvictionPolicy,
    private val cacheConfig: CacheConfig = CacheConfig()
) {
    companion object {
        private const val TAG = "RemoteCacheManager"
        // Use timestamp suffix to avoid collisions when same file is cached multiple times
        private const val CACHE_FILE_SUFFIX = "_remote"
    }

    /**
     * Download a comic file from Google Drive to local cache.
     *
     * @param remoteComic The RemoteComic to download
     * @param progressCallback Optional callback for download progress (0-100)
     * @return Result containing the cached File or error
     */
    suspend fun downloadComicFile(
        remoteComic: RemoteComic,
        progressCallback: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Timber.tag(TAG).i("Downloading comic: ${remoteComic.name} (${formatBytes(remoteComic.sizeBytes)})")

            // Create cache directory if it doesn't exist
            val cacheDir = ensureCacheDir()

            // Check if we need to evict to make space
            if (cacheConfig.enableAutoEviction) {
                val currentSize = remoteComicDao.getTotalCacheSizeBytes()
                if (currentSize + remoteComic.sizeBytes > cacheConfig.maxCacheSizeBytes) {
                    Timber.tag(TAG).d("Cache would exceed limit, triggering eviction. Current: ${formatBytes(currentSize)}")
                    val evicted = evictionPolicy.evictLRU(
                        cacheConfig.maxCacheSizeBytes,
                        currentSize
                    )
                    evicted.onSuccess { freed ->
                        Timber.tag(TAG).i("Evicted ${formatBytes(freed.toLong())} to make space")
                    }
                }
            }

            // Generate cache filename (use Drive file ID to ensure uniqueness)
            val cacheFilename = "${remoteComic.driveFileId}$CACHE_FILE_SUFFIX"
            val cachedFile = File(cacheDir, cacheFilename)

            // Download from Drive
            Timber.tag(TAG).d("Downloading to: ${cachedFile.absolutePath}")
            val downloadResult = driveApiClient.downloadFile(
                remoteComic.driveFileId,
                cachedFile
            )

            downloadResult.onFailure {
                Timber.tag(TAG).e(it, "Download failed for ${remoteComic.name}")
                return@withContext Result.failure(it)
            }

            // Verify file exists and has content
            if (!cachedFile.exists() || cachedFile.length() == 0L) {
                Timber.tag(TAG).e("Downloaded file is empty or missing: ${cachedFile.absolutePath}")
                cachedFile.delete()
                return@withContext Result.failure(Exception("Downloaded file is empty"))
            }

            Timber.tag(TAG).d("Download complete: ${cachedFile.absolutePath} (${formatBytes(cachedFile.length())})")

            // Update database with cache path
            remoteComicDao.updateCachePath(
                remoteComic.driveFileId,
                cachedFile.absolutePath,
                System.currentTimeMillis()
            )

            progressCallback(100)
            Timber.tag(TAG).i("Successfully cached comic: ${remoteComic.name}")
            Result.success(cachedFile)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error downloading comic: ${remoteComic.name}")
            Result.failure(e)
        }
    }

    /**
     * Get a cached comic file, or download it if not cached.
     *
     * This is the main entry point for accessing comics. It implements the
     * "get or download" pattern to provide seamless access.
     *
     * @param remoteComic The RemoteComic to access
     * @param onProgress Optional callback for download progress (0-100)
     * @return Result containing the File or error
     */
    suspend fun getOrDownloadComic(
        remoteComic: RemoteComic,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Fast path: already cached
            val cachedPath = remoteComic.cacheLocalPath
            if (cachedPath != null) {
                val cachedFile = File(cachedPath)
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    Timber.tag(TAG).d("Comic already cached: ${remoteComic.name}")

                    // Update last accessed time for LRU tracking
                    remoteComicDao.updateLastAccessedTime(
                        remoteComic.driveFileId,
                        System.currentTimeMillis()
                    )

                    return@withContext Result.success(cachedFile)
                } else {
                    Timber.tag(TAG).w("Cached file missing or empty: $cachedPath, will re-download")
                    remoteComicDao.clearCachePath(remoteComic.driveFileId)
                }
            }

            // Slow path: download
            downloadComicFile(remoteComic, onProgress)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in getOrDownloadComic")
            Result.failure(e)
        }
    }

    /**
     * Download and cache a thumbnail image for a comic.
     *
     * Thumbnails are fetched from the remote thumbnail URL and cached locally.
     * Useful for displaying cover images in UI without downloading full comic.
     *
     * @param remoteComic The RemoteComic whose thumbnail to download
     * @return Result containing path to cached thumbnail or error
     */
    suspend fun downloadThumbnail(remoteComic: RemoteComic): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val thumbnailUrl = remoteComic.thumbnailRemoteUrl
                if (thumbnailUrl.isNullOrEmpty()) {
                    Timber.tag(TAG).d("No thumbnail URL for: ${remoteComic.name}")
                    return@withContext Result.success("")
                }

                // Check if already cached
                val cachedPath = remoteComic.thumbnailLocalPath
                if (cachedPath != null) {
                    val cachedFile = File(cachedPath)
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        Timber.tag(TAG).d("Thumbnail already cached: ${remoteComic.name}")
                        return@withContext Result.success(cachedPath)
                    }
                }

                // Create thumbnail cache directory
                val thumbnailDir = File(context.cacheDir, cacheConfig.thumbnailCachePath)
                if (!thumbnailDir.exists()) {
                    if (!thumbnailDir.mkdirs()) {
                        Timber.tag(TAG).e("Failed to create thumbnail cache directory: ${thumbnailDir.absolutePath}")
                        return@withContext Result.failure(Exception("Cannot create thumbnail directory"))
                    }
                }

                // Download thumbnail
                Timber.tag(TAG).d("Downloading thumbnail for: ${remoteComic.name}")
                val thumbnailFile =
                    File(thumbnailDir, "${remoteComic.driveFileId}_thumb.jpg")

                // Use simple HTTP download for thumbnail (small file)
                try {
                    val url = URL(thumbnailUrl)
                    url.openStream().use { input ->
                        Files.copy(input, thumbnailFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
                        // Update database with thumbnail path
                        remoteComicDao.updateThumbnailPath(
                            remoteComic.driveFileId,
                            thumbnailFile.absolutePath
                        )

                        Timber.tag(TAG).i("Successfully cached thumbnail: ${thumbnailFile.absolutePath}")
                        Result.success(thumbnailFile.absolutePath)
                    } else {
                        Timber.tag(TAG).w("Thumbnail download resulted in empty file")
                        thumbnailFile.delete()
                        Result.failure(Exception("Thumbnail is empty"))
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to download thumbnail from URL")
                    thumbnailFile.delete()
                    Result.failure(e)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error downloading thumbnail")
                Result.failure(e)
            }
        }

    /**
     * Evict cached files if total cache size exceeds limit.
     *
     * Triggers LRU eviction policy to remove oldest-accessed files
     * until cache fits within the configured limit.
     *
     * @param maxCacheSizeBytes Maximum cache size in bytes (uses config default if null)
     * @return Result containing total bytes freed
     */
    suspend fun evictCacheIfNeeded(maxCacheSizeBytes: Long? = null): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val limit = maxCacheSizeBytes ?: cacheConfig.maxCacheSizeBytes
                val currentSize = remoteComicDao.getTotalCacheSizeBytes()

                if (currentSize <= limit) {
                    Timber.tag(TAG).d("Cache within limit: ${formatBytes(currentSize)}/${formatBytes(limit)}")
                    return@withContext Result.success(0)
                }

                Timber.tag(TAG).i("Cache exceeds limit, triggering eviction")
                evictionPolicy.evictLRU(limit, currentSize)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error in evictCacheIfNeeded")
                Result.failure(e)
            }
        }

    /**
     * Get cached comic file if it exists locally.
     *
     * Non-blocking check - returns immediately.
     *
     * @param remoteComic The RemoteComic to check
     * @return Cached File if it exists, null otherwise
     */
    fun getCachedComicPath(remoteComic: RemoteComic): File? {
        val cachedPath = remoteComic.cacheLocalPath ?: return null
        val file = File(cachedPath)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Get cache statistics.
     *
     * @return CacheStats with current usage
     */
    suspend fun getCacheStats(): Result<CacheStats> = withContext(Dispatchers.IO) {
        try {
            val totalSize = remoteComicDao.getTotalCacheSizeBytes()
            val cachedCount = remoteComicDao.getCachedComicCount()
            val totalComics = remoteComicDao.getTotalComicCount()

            Result.success(
                CacheStats(
                    totalSizeBytes = totalSize,
                    maxSizeBytes = cacheConfig.maxCacheSizeBytes,
                    cachedComicCount = cachedCount,
                    totalComicCount = totalComics,
                    percentFull = if (cacheConfig.maxCacheSizeBytes > 0) {
                        (totalSize * 100 / cacheConfig.maxCacheSizeBytes).toInt()
                    } else {
                        0
                    }
                )
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get cache stats")
            Result.failure(e)
        }
    }

    /**
     * Clear entire cache and update database.
     *
     * Dangerous operation - clears all cached files.
     *
     * @return Result with total bytes freed
     */
    suspend fun clearCache(): Result<Int> = evictionPolicy.clearAllCache()

    /**
     * Ensure cache directories exist.
     *
     * @return File pointing to the cache directory
     */
    private fun ensureCacheDir(): File {
        val cacheDir = File(context.cacheDir, cacheConfig.fileCacheDir)
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                Timber.tag(TAG).e("Failed to create cache directory: ${cacheDir.absolutePath}")
            }
        }
        return cacheDir
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }

    /**
     * Cache statistics snapshot.
     */
    data class CacheStats(
        val totalSizeBytes: Long,
        val maxSizeBytes: Long,
        val cachedComicCount: Int,
        val totalComicCount: Int,
        val percentFull: Int
    ) {
        fun isFull(): Boolean = percentFull >= 100
        fun isNearlyFull(): Boolean = percentFull >= 85
    }
}
