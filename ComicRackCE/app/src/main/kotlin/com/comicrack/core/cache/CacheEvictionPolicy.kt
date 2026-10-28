package com.comicrack.core.cache

import com.comicrack.data.database.dao.ComicBookDao
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRU eviction policy for cached remote comic files.
 *
 * Queries comics by `syncedAt ASC` (oldest synced first) and deletes
 * cached files until the cache directory is under the size limit.
 *
 * Phase 5: Remote Sync and Caching (SRC-05-007 v2)
 */
@Singleton
class CacheEvictionPolicy @Inject constructor(
    private val comicDao: ComicBookDao,
) {
    companion object {
        private const val TAG = "CacheEvictionPolicy"

        /** Default max cache size: 2 GB. */
        const val DEFAULT_MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024
    }

    /**
     * Evict cached files until the cache directory is under [maxBytes].
     *
     * Evicts the least-recently-synced remote comics first.
     *
     * @param cacheDir The cache root directory
     * @param maxBytes Maximum allowed cache size in bytes
     * @return Number of bytes freed
     */
    suspend fun evictIfNeeded(cacheDir: File, maxBytes: Long = DEFAULT_MAX_CACHE_BYTES): Long {
        val currentSize = calculateDirectorySize(cacheDir)
        if (currentSize <= maxBytes) {
            Timber.tag(TAG).d("Cache within limit: ${currentSize / (1024 * 1024)}MB / ${maxBytes / (1024 * 1024)}MB")
            return 0
        }

        val targetFree = currentSize - maxBytes
        Timber.tag(TAG).i("Cache over limit by ${targetFree / (1024 * 1024)}MB, starting eviction")

        var freedBytes = 0L
        val cachedComics = comicDao.getCachedRemoteComics()

        for (comic in cachedComics) {
            if (freedBytes >= targetFree) break

            val cachePath = comic.cacheLocalPath ?: continue
            val file = File(cachePath)

            if (file.exists()) {
                val fileSize = file.length()
                if (file.delete()) {
                    freedBytes += fileSize
                    comicDao.updateCachePath(comic.id, null)
                    Timber.tag(TAG).d("Evicted: ${comic.title} (${fileSize / 1024}KB)")
                }
            } else {
                comicDao.updateCachePath(comic.id, null)
            }
        }

        Timber.tag(TAG).i("Eviction complete: freed ${freedBytes / (1024 * 1024)}MB")
        return freedBytes
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
