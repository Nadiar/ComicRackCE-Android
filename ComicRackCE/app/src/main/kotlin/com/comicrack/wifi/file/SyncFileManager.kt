package com.comicrack.wifi.file

// DOCFIX: doc-only pass – 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.net.Uri
import com.comicrack.core.storage.saf.SAFFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileNotFoundException
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.sink
import okio.source
import timber.log.Timber
import java.io.InputStream

/**
 * SAF-based implementation of FileManager for WiFi sync operations.
 *
 * Handles .cbp (portable comic archive) and .cbp.xml (metadata sidecar) files.
 *
 * Uses SAFFileSystem exclusively for core file operations, eliminating dependency
 * on deprecated DocumentFile API which is unreliable on Android 16+.
 *
 * @param context Android application context
 * @param fs SAF filesystem abstraction (provides consistent Okio FileSystem API)
 * @param syncRoot The sync subdirectory path (relative to the root of fs)
 */
class SyncFileManager(
    private val context: Context,
    private val fs: SAFFileSystem,
    private val syncRoot: Path,
) : FileManager {
    companion object {
        private const val TAG = "SyncFileManager"
        private const val CBP_EXTENSION = ".cbp"

        // Cleanup timing: 10 minutes
        private const val SYNC_STALE_FILE_THRESHOLD_MS = 10 * 60 * 1000L
        private const val MS_PER_MINUTE = 60000

        /**
         * Create a SyncFileManager from a tree URI (e.g., library root).
         *
         * Uses SAFFileSystem to handle sync directory creation and management,
         * avoiding Android 16+ issues with DocumentFile API.
         *
         * @param treeUri The tree URI with SAF permissions
         * @param syncDirName The name of the sync subdirectory (default "_sync")
         */
        fun fromTreeUri(
            context: Context,
            treeUri: Uri,
            syncDirName: String = "_sync",
        ): SyncFileManager? =
            try {
                val fs = SAFFileSystem(context, treeUri)
                val syncDir = syncDirName.toPath()

                // Create sync directory if it doesn't exist (idempotent)
                fs.createDirectory(syncDir, mustCreate = false)

                SyncFileManager(context, fs, syncDir)
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "Cannot create/access sync directory: $syncDirName")
                null
            } catch (e: SecurityException) {
                Timber.tag(TAG).e(e, "Permission denied creating sync directory: $syncDirName")
                null
            } catch (e: IllegalArgumentException) {
                Timber.tag(TAG).e(e, "Invalid path for sync directory: $syncDirName")
                null
            } catch (e: IllegalStateException) {
                Timber.tag(TAG).e(e, "Invalid state while initializing sync directory: $syncDirName")
                null
            }
    }

    override suspend fun listFiles(): List<String> =
        withContext(Dispatchers.IO) {
            try {
                fs
                    .list(syncRoot)
                    .filter { path ->
                        val metadata = fs.metadataOrNull(path)
                        metadata?.isRegularFile == true && isSyncFile(path.name)
                    }.map { it.name }
            } catch (e: SecurityException) {
                Timber.tag(TAG).e(e, "Permission denied listing files")
                emptyList()
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "Failed to list files")
                emptyList()
            }
        }

    override suspend fun readFile(filename: String): ByteArray? =
        withContext(Dispatchers.IO) {
            if (!isValidFilename(filename)) {
                Timber.tag(TAG).w("Invalid filename: $filename")
                return@withContext null
            }

            try {
                val path = syncRoot / filename
                fs.source(path).buffer().use { source ->
                    source.readByteArray()
                }
            } catch (e: FileNotFoundException) {
                Timber.tag(TAG).w("File not found: $filename")
                null
            } catch (e: SecurityException) {
                Timber.tag(TAG).e(e, "Permission denied reading file")
                null
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "Failed to read file: $filename")
                null
            }
        }

    override suspend fun writeFile(
        filename: String,
        size: Long,
        inputStream: InputStream,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (!isValidFilename(filename)) {
                Timber.tag(TAG).w("Invalid filename for write: $filename")
                return@withContext false
            }

            try {
                val path = syncRoot / filename

                // Delete existing file if present (SAF doesn't support atomic overwrites)
                fs.delete(path, mustExist = false)

                // Create new file and write content
                fs.sink(path, mustCreate = true).buffer().use { sink ->
                    inputStream.source().buffer().use { source ->
                        sink.writeAll(source)
                    }
                }

                Timber.tag(TAG).d("Wrote file: $filename ($size bytes)")
                true
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "Failed to write file: $filename")
                false
            }
        }

    override suspend fun deleteFile(filename: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!isValidFilename(filename)) {
                Timber.tag(TAG).w("Invalid filename for delete: $filename")
                return@withContext false
            }

            try {
                val path = syncRoot / filename
                fs.delete(path, mustExist = false)
                Timber.tag(TAG).d("Deleted file: $filename")
                true
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "Failed to delete file: $filename")
                false
            }
        }

    override suspend fun fileExists(filename: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!isValidFilename(filename)) {
                return@withContext false
            }

            try {
                val path = syncRoot / filename
                fs.metadataOrNull(path) != null
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "Failed to check file existence: $filename")
                false
            }
        }

    override suspend fun getFreeSpace(): Long =
        withContext(Dispatchers.IO) {
            // SAF URIs don't provide reliable free space information
            // Return a large default value - the desktop will verify before sending
            Long.MAX_VALUE / 2
        }

    /**
     * Clean up orphaned .cbp files that don't have a corresponding .cbp.xml file.
     * Also cleans up SAF collision duplicates (e.g. "file (1).xml").
     *
     * Only deletes files older than [staleThresholdMs] to avoid deleting files
     * that are currently being written by an active sync.
     */
    suspend fun cleanupStaleSyncFiles(staleThresholdMs: Long = SYNC_STALE_FILE_THRESHOLD_MS) {
        withContext(Dispatchers.IO) {
            Timber.tag(TAG).i("Starting cleanup of stale sync files (threshold=${staleThresholdMs / MS_PER_MINUTE}m)")
            try {
                val allFiles = fs.list(syncRoot)
                val now = System.currentTimeMillis()
                var deletedCount = 0

                // 1. Find all .cbp files and check for their .cbp.xml counterpart
                val cbpFiles = allFiles.filter { it.name.lowercase().endsWith(CBP_EXTENSION) }
                for (cbpPath in cbpFiles) {
                    val xmlName = cbpPath.name + ".xml"
                    val xmlPath = syncRoot / xmlName
                    
                    val cbpMetadata = fs.metadataOrNull(cbpPath) ?: continue
                    val lastModified = cbpMetadata.lastModifiedAtMillis ?: 0L
                    
                    if (now - lastModified > staleThresholdMs) {
                        // File is stale. Check if XML exists.
                        if (fs.metadataOrNull(xmlPath) == null) {
                            val ageMs = now - lastModified
                            Timber.tag(TAG).d(
                                "Deleting orphaned stale .cbp: ${cbpPath.name} " +
                                    "(last modified: ${ageMs}ms ago)"
                            )
                            fs.delete(cbpPath, mustExist = false)
                            deletedCount++
                        }
                    }
                }

                // 2. Find and delete SAF collision files (e.g. "file (1).cbp.xml" or "file.cbp.xml (1)")
                // These occur when the OS or SAF provider renames a file due to a perceived collision.
                // Robust regex for " (1)" pattern: supports multiple extensions and trailing indexes.
                val collisionPattern = Regex(""".* \(\d+\)(\..*)?|.* \(\d+\)$""")
                val collisionFiles = allFiles.filter { collisionPattern.matches(it.name) }
                for (path in collisionFiles) {
                    val metadata = fs.metadataOrNull(path) ?: continue
                    val lastModified = metadata.lastModifiedAtMillis ?: 0L
                    if (now - lastModified > staleThresholdMs) {
                        Timber.tag(TAG).d("Deleting stale collision file: ${path.name}")
                        fs.delete(path, mustExist = false)
                        deletedCount++
                    }
                }

                if (deletedCount > 0) {
                    Timber.tag(TAG).i("Cleanup complete: deleted $deletedCount stale files")
                } else {
                    Timber.tag(TAG).d("Cleanup complete: no stale files found")
                }
            } catch (e: SecurityException) {
                Timber.tag(TAG).e(e, "Permission denied during sync folder cleanup")
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "I/O error during sync folder cleanup")
            } catch (e: IllegalStateException) {
                Timber.tag(TAG).e(e, "Invalid state during sync folder cleanup")
            }
        }
    }

    // ==========================================================================
    // Helper functions
    // ==========================================================================

    /**
     * Check if a filename is a sync metadata file (.cbl, .xml, .ini).
     *
     * Sync files are metadata only - NOT comic archives.
     * Comic archives (.cbz, .cbr, .cbp, etc.) should remain in parent directory,
     * not be replicated to _sync folder.
     *
     * Metadata files:
     * - .cbl = ComicRack Library metadata
     * - .xml = Generic XML metadata (including .cbp.xml)
     * - .ini = ComicRack configuration (comicrack.ini)
     */
    private fun isSyncFile(filename: String): Boolean {
        val lower = filename.lowercase()
        return lower.endsWith(".cbl") ||
            lower.endsWith(".xml") ||
            lower.endsWith(".ini") ||
            lower.endsWith(CBP_EXTENSION)
    }

    /**
     * Validate that a filename is safe (no path traversal)
     */
    private fun isValidFilename(filename: String): Boolean = when {
        filename.isBlank() -> false
        filename.contains("..") -> false
        filename.contains("/") || filename.contains("\\") -> false
        else -> true
    }
}
