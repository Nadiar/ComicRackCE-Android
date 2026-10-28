package com.comicrack.remote.cache

import timber.log.Timber
import com.common.db.dao.RemoteComicDao
import com.common.db.entities.RemoteComic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LRU (Least Recently Used) cache eviction policy for remote comic files.
 *
 * Implements eviction strategy that deletes oldest-accessed files when cache
 * exceeds size limit. Tracks access time via lastAccessedLocal timestamp in database.
 *
 * Algorithm:
 * 1. Query database for cached comics sorted by lastAccessedLocal (oldest first)
 * 2. Delete files from oldest until cache size drops below limit
 * 3. Update database to clear cache paths
 *
 * Phase 3: Caching & Download Management
 * Remote Library module - LRU cache eviction
 */
class CacheEvictionPolicy(
    private val remoteComicDao: RemoteComicDao
) {
    companion object {
        private const val TAG = "CacheEvictionPolicy"
    }

    /**
     * Evict least recently used cached comics until cache is within limit.
     *
     * @param maxCacheSizeBytes Maximum allowed cache size in bytes
     * @param currentCacheSizeBytes Current total cache size in bytes
     * @return Result containing total bytes freed by eviction
     */
    suspend fun evictLRU(
        maxCacheSizeBytes: Long,
        currentCacheSizeBytes: Long
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // If we're within limit, nothing to evict
            if (currentCacheSizeBytes <= maxCacheSizeBytes) {
                Timber.tag(TAG).d("Cache size within limit: ${formatBytes(currentCacheSizeBytes)}/${formatBytes(maxCacheSizeBytes)}")
                return@withContext Result.success(0)
            }

            Timber.tag(TAG).i(
                "Cache exceeds limit: ${formatBytes(currentCacheSizeBytes)}/${formatBytes(maxCacheSizeBytes)}, starting eviction"
            )

            var bytesFreed = 0L
            var cacheSizeNow = currentCacheSizeBytes

            // Query LRU-sorted comics (oldest accessed first)
            val cachedComics = remoteComicDao.getLRUCachedComics(limit = 1000)

            if (cachedComics.isEmpty()) {
                Timber.tag(TAG).d("No cached comics to evict")
                return@withContext Result.success(0)
            }

            Timber.tag(TAG).d("Found ${cachedComics.size} cached comics for potential eviction")

            // Delete files from oldest accessed until we drop below limit
            for (comic in cachedComics) {
                if (cacheSizeNow <= maxCacheSizeBytes) {
                    break  // Cache is now within limit
                }

                val cacheLocalPath = comic.cacheLocalPath ?: continue

                try {
                    val cachedFile = File(cacheLocalPath)
                    val fileSize = if (cachedFile.exists()) cachedFile.length() else comic.sizeBytes

                    // Delete the physical cache file
                    if (cachedFile.exists()) {
                        if (cachedFile.delete()) {
                            bytesFreed += fileSize
                            cacheSizeNow -= fileSize

                            Timber.tag(TAG).d(
                                "Evicted: ${comic.name} (${formatBytes(fileSize)}), LRU timestamp: ${comic.lastAccessedLocal}"
                            )
                        } else {
                            Timber.tag(TAG).w("Failed to delete cache file: $cacheLocalPath")
                        }
                    } else {
                        // File doesn't exist but DB says it's cached, just update DB
                        bytesFreed += comic.sizeBytes
                        cacheSizeNow -= comic.sizeBytes
                        Timber.tag(TAG).d("Cache file missing: $cacheLocalPath, DB updated")
                    }

                    // Clear the cache path in database
                    remoteComicDao.clearCachePath(comic.driveFileId)

                    // Update last accessed time to reflect eviction
                    remoteComicDao.updateLastAccessedTime(
                        comic.driveFileId,
                        System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error evicting comic ${comic.driveFileId}")
                    // Continue with next comic on error
                }
            }

            Timber.tag(TAG).i("Eviction complete. Freed: ${formatBytes(bytesFreed)}, new size: ${formatBytes(cacheSizeNow)}")
            Result.success(bytesFreed.toInt())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Eviction failed")
            Result.failure(e)
        }
    }

    /**
     * Force eviction of a specific comic from cache.
     *
     * Useful for manually removing a single comic without affecting others.
     *
     * @param comic The RemoteComic to evict
     * @return Result with bytes freed
     */
    suspend fun evictComic(comic: RemoteComic): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val cacheLocalPath = comic.cacheLocalPath
                    ?: return@withContext Result.success(0)

                val cachedFile = File(cacheLocalPath)
                if (!cachedFile.exists()) {
                    Timber.tag(TAG).w("Cache file not found: $cacheLocalPath")
                    remoteComicDao.clearCachePath(comic.driveFileId)
                    return@withContext Result.success(0)
                }

                val fileSize = cachedFile.length()
                if (cachedFile.delete()) {
                    remoteComicDao.clearCachePath(comic.driveFileId)
                    Timber.tag(TAG).i("Evicted comic: ${comic.name} (${formatBytes(fileSize)})")
                    Result.success(fileSize.toInt())
                } else {
                    Timber.tag(TAG).e("Failed to delete cache file: $cacheLocalPath")
                    Result.failure(Exception("Failed to delete cache file"))
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error evicting comic")
                Result.failure(e)
            }
        }

    /**
     * Clear all cached files and update database.
     *
     * Dangerous operation - clears entire cache. Use with caution.
     *
     * @return Result with total bytes freed
     */
    suspend fun clearAllCache(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Timber.tag(TAG).w("Clearing all cached comics")
            var bytesFreed = 0L

            val cachedComics = remoteComicDao.getCachedComics().firstOrNull() ?: emptyList()

            for (comic in cachedComics) {
                val cacheLocalPath = comic.cacheLocalPath ?: continue

                try {
                    val cachedFile = File(cacheLocalPath)
                    if (cachedFile.exists()) {
                        bytesFreed += cachedFile.length()
                        cachedFile.delete()
                    }
                    remoteComicDao.clearCachePath(comic.driveFileId)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error clearing comic ${comic.driveFileId}")
                }
            }

            Timber.tag(TAG).i("All cache cleared. Freed: ${formatBytes(bytesFreed)}")
            Result.success(bytesFreed.toInt())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear all cache")
            Result.failure(e)
        }
    }

    /**
     * Get current total cache size by scanning files on disk.
     *
     * More reliable than database value, but slower.
     * Use for debugging or verification.
     *
     * @param cacheDir Base cache directory
     * @return Result with cache size in bytes
     */
    suspend fun getActualCacheSize(cacheDir: File): Result<Long> =
        withContext(Dispatchers.IO) {
            try {
                if (!cacheDir.exists()) {
                    return@withContext Result.success(0)
                }

                var totalSize = 0L
                cacheDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        totalSize += file.length()
                    }
                }

                Timber.tag(TAG).d("Actual cache size: ${formatBytes(totalSize)}")
                Result.success(totalSize)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to calculate actual cache size")
                Result.failure(e)
            }
        }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
