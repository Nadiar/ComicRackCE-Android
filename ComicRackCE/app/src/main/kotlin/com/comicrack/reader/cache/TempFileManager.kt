package com.comicrack.reader.cache

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton to manage temporary files created during archive processing.
 *
 * Tracks temp files from SAF URI resolution and cleans them up
 * when the app pauses or stops, preventing cache directory bloat.
 *
 * Usage:
 * - ArchiveProviderFactory.create() registers temp files
 * - Activity registers cleanup callback on pause/destroy
 * - Automatic cleanup on app lifecycle events
 *
 * Phase 3: Task 3.2 â€” Temp File Cleanup Implementation
 */
@Singleton
class TempFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "TempFileManager"
    }
    private val tempFiles = mutableSetOf<String>() // Absolute paths
    private val appCacheDir: File = context.cacheDir

    init {
        Timber.tag(TAG).d("Initialized with cache dir: ${appCacheDir.absolutePath}")
    }

    /**
     * Register a temporary file for tracking.
     * File will be cleaned up on cleanup() call.
     */
    fun registerTempFile(filePath: String) {
        if (filePath.isNotEmpty()) {
            tempFiles.add(filePath)
            Timber.tag(TAG).d("Registered temp file: $filePath (total: ${tempFiles.size})")
        }
    }

    /**
     * Register multiple temporary files.
     */
    fun registerTempFiles(filePaths: List<String>) {
        filePaths.forEach { registerTempFile(it) }
    }

    /**
     * Clean up all registered temporary files.
     * Called on activity pause or destroy.
     */
    fun cleanup() {
        var deletedCount = 0
        var failedCount = 0
        var totalSize = 0L

        val filesToRemove = mutableListOf<String>()

        tempFiles.forEach { filePath ->
            try {
                val file = File(filePath)
                if (file.exists()) {
                    totalSize += file.length()
                    if (file.delete()) {
                        deletedCount++
                        Timber.tag(TAG).d("Deleted: $filePath")
                    } else {
                        failedCount++
                        Timber.tag(TAG).w("Failed to delete: $filePath")
                    }
                }
                filesToRemove.add(filePath)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error deleting temp file: $filePath")
                failedCount++
                filesToRemove.add(filePath)
            }
        }

        filesToRemove.forEach { tempFiles.remove(it) }

        Timber.tag(TAG).d(
            "Cleanup complete: deleted $deletedCount files, " +
                "failed $failedCount, freed ${formatBytes(totalSize)}",
        )
    }

    /**
     * Cleanup files from a specific archive.
     * Called when loading a different comic.
     */
    fun cleanupArchive(archivePath: String) {
        val filesToRemove = tempFiles.filter { it.contains(archivePath) }
        filesToRemove.forEach { filePath ->
            try {
                val file = File(filePath)
                if (file.exists() && file.delete()) {
                    Timber.tag(TAG).d("Cleaned archive temp: $filePath")
                }
                tempFiles.remove(filePath)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to clean archive temp: $filePath")
            }
        }
    }

    /**
     * Get current tracked temp file count.
     */
    fun getTempFileCount(): Int = tempFiles.size

    /**
     * Clear all tracked files without deleting them.
     * Use cleanup() to actually delete.
     */
    fun clear() {
        tempFiles.clear()
        Timber.tag(TAG).d("Cleared temp file tracking")
    }

    private fun formatBytes(bytes: Long): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
}
