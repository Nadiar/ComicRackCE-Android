package com.comicrack.core.cache

import android.content.Context
import com.comicrack.core.storage.drive.DownloadProgress
import com.comicrack.core.storage.drive.DriveFileDownloader
import com.comicrack.data.database.dao.ComicBookDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the local cache directory for remote comic files.
 *
 * Downloads on demand via [DriveFileDownloader], updates `cacheLocalPath`
 * in the database, and delegates eviction to [CacheEvictionPolicy].
 *
 * Cache directory: `{appCacheDir}/remote_comics/`
 *
 * Phase 5: Remote Sync and Caching (SRC-05-007 v2)
 */
@Singleton
class RemoteCacheManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloader: DriveFileDownloader,
    private val comicDao: ComicBookDao,
    private val evictionPolicy: CacheEvictionPolicy,
) {
    companion object {
        private const val TAG = "RemoteCacheManager"
        private const val CACHE_DIR_NAME = "remote_comics"
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }
    }

    /**
     * Get the cached file for a comic, or null if not cached.
     *
     * @param comicId Database ID of the comic
     * @return The cached file if it exists, null otherwise
     */
    suspend fun getCachedFile(comicId: Long): File? {
        val comic = comicDao.getComicBookById(comicId) ?: return null
        val path = comic.cacheLocalPath ?: return null
        val file = File(path)
        return if (file.exists()) file else null
    }

    /**
     * Download a remote comic file to local cache.
     *
     * Runs eviction if needed before downloading. Updates `cacheLocalPath`
     * in the database on completion.
     *
     * @param comicId Database ID of the comic
     * @param remoteId Drive file ID
     * @param fileName Display filename (for local cache naming)
     * @param expectedSize Expected file size in bytes (for progress)
     * @return Flow of [DownloadProgress] events
     */
    fun downloadToCache(
        comicId: Long,
        remoteId: String,
        fileName: String,
        expectedSize: Long = 0,
    ): Flow<DownloadProgress> {
        val safeFileName = sanitizeFileName(fileName)
        val destinationFile = File(cacheDir, "${remoteId}_$safeFileName")

        Timber.tag(TAG).i("Downloading $fileName to cache: ${destinationFile.absolutePath}")

        return downloader.download(
            fileId = remoteId,
            destinationFile = destinationFile,
            expectedSize = expectedSize,
        )
    }

    /**
     * Update the database with the cache path after a successful download.
     *
     * @param comicId Database ID of the comic
     * @param remoteId Drive file ID used in the cache filename
     * @param fileName Display filename
     */
    suspend fun onDownloadComplete(comicId: Long, remoteId: String, fileName: String) {
        val safeFileName = sanitizeFileName(fileName)
        val destinationFile = File(cacheDir, "${remoteId}_$safeFileName")
        if (destinationFile.exists()) {
            comicDao.updateCachePath(comicId, destinationFile.absolutePath)
            Timber.tag(TAG).d("Cache path updated for comic $comicId")
        }
    }

    /**
     * Run cache eviction if the cache exceeds the size limit.
     *
     * @param maxBytes Maximum cache size (defaults to policy default)
     * @return Bytes freed
     */
    suspend fun evictIfNeeded(maxBytes: Long = CacheEvictionPolicy.DEFAULT_MAX_CACHE_BYTES): Long =
        evictionPolicy.evictIfNeeded(cacheDir, maxBytes)

    /**
     * Clear the entire cache directory and reset all cache paths in the database.
     */
    suspend fun clearAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
        val cached = comicDao.getCachedRemoteComics()
        cached.forEach { comicDao.updateCachePath(it.id, null) }
        Timber.tag(TAG).i("Cache cleared: removed ${cached.size} entries")
    }

    /**
     * Get total cache size in bytes.
     */
    fun getCacheSize(): Long =
        cacheDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
