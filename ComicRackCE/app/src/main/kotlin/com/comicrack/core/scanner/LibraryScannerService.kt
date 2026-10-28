package com.comicrack.core.scanner

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.comicrack.core.storage.provider.StorageProvider
import com.comicrack.core.storage.provider.StorageProviderFactory
import com.comicrack.core.storage.provider.StorageType
import com.comicrack.core.storage.saf.SAFFileSystem
import com.comicrack.data.database.ComicRackDatabase
import com.comicrack.data.database.dao.ComicBookDao
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.LibraryPath
import okio.Path
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.File

/**
 * Service for scanning library paths and discovering comic files.
 *
 * Supports both SAF URIs (DocumentFile) and legacy File paths.
 * Uses recursive directory traversal to find comic files (.cbz, .cbr, .cb7, .cbt, .zip, .rar).
 * Persists results to Room database (LibraryPath + ComicBook entities).
 *
 * Phase 3: Task 3.3 â€” Library Scanner SAF Migration
 * **Migrated to Kotlin** for idiomatic coroutine integration and null safety.
 */
class LibraryScannerService(
    private val context: Context,
    private val pathDao: LibraryPathDao,
    private val comicDao: ComicBookDao,
    private val database: ComicRackDatabase? = null,
    private val storageProviderFactory: StorageProviderFactory? = null,
) {
    companion object {
        private const val TAG = "LibraryScannerService"

        // Supported comic file extensions
        // Note: "cbp" is the WiFi sync portable comic format (ZIP-based)
        private val COMIC_EXTENSIONS =
            setOf(
                "cbz",
                "cbr",
                "cb7",
                "cbt",
                "cbp",
                "zip",
                "rar",
                "7z",
                "tar",
            )

        /**
         * Create a LibraryScannerService instance using ComicRackDatabase singleton.
         *
         * @param context Application context
         * @return Initialized LibraryScannerService
         */
        fun create(context: Context): LibraryScannerService {
            val db = ComicRackDatabase.getInstance(context)
            return LibraryScannerService(context, db.libraryPathDao(), db.comicBookDao(), db)
        }
    }

    /**
     * Get the underlying ComicRackDatabase instance.
     *
     * Used by orchestration layers (LibraryService) to access other DAOs.
     */
    fun getDatabase(): ComicRackDatabase = database ?: ComicRackDatabase.getInstance(context)

    /**
     * Refresh a library path: discover comic files and sync with database.
     *
     * Terminology:
     * - **Refresh**: Sync database with filesystem (Detect additions/removals).
     *
     * @param path The LibraryPath entity with path URI/string
     * @param onFileDiscovered Optional callback called when a comic is newly discovered or updated.
     *                         Parameters: (ComicBook, isNewOrChanged)
     * @return Pair of (All discovered comics) and (Subset that are new or modified)
     * @throws Exception if path is invalid or refresh fails
     */
    suspend fun refreshLibraryPath(
        path: LibraryPath,
        onFileDiscovered: (suspend (ComicBook, Boolean) -> Unit)? = null
    ): Pair<List<ComicBook>, List<ComicBook>> {
        if (path.path == null || path.path.isEmpty()) {
            throw IllegalArgumentException("LibraryPath must have a valid path")
        }

        val comics = mutableListOf<ComicBook>()

        // Route to appropriate scan method based on storage type
        when (StorageType.fromValue(path.storageType)) {
            StorageType.SAF -> scanSafUri(path, comics)
            StorageType.LOCAL -> scanLocalPath(path, comics)
            StorageType.GOOGLE_DRIVE -> scanWithProvider(path, comics)
        }

        // Collect callback data during transaction, invoke after
        val callbackData = mutableListOf<Pair<ComicBook, Boolean>>()
        var insertedCount = 0
        var updatedCount = 0
        val changedComics = mutableListOf<ComicBook>()

        // Wrap all database operations in a transaction to prevent race conditions
        getDatabase().withTransaction {
            // Clean up any duplicate entries from previous bugs
            comicDao.deleteDuplicates()

            // Insert or update comics - avoid duplicates by checking filePath
            if (comics.isNotEmpty()) {
                for (comic in comics) {
                    val existing = comicDao.getComicBookByPath(comic.filePath)
                    if (existing != null) {
                        // Smart change detection: check if metadata needs re-parsing
                        // We check file size and modification date
                        val hasChanged = existing.fileSize != comic.fileSize ||
                            existing.dateModified != comic.dateModified ||
                            existing.source.isNullOrEmpty()

                        // Update existing entry, preserve ID and reading progress
                        comic.id = existing.id
                        comic.currentPage = existing.currentPage
                        comic.lastPageRead = existing.lastPageRead
                        comic.rating = existing.rating
                        comic.openedCount = existing.openedCount

                        comicDao.updateComicBook(comic)
                        updatedCount++

                        if (hasChanged) {
                            changedComics.add(comic)
                            Timber.tag(TAG).d(
                                "Change detected for: ${comic.fileName} " +
                                    "(Size: ${existing.fileSize}->${comic.fileSize}, " +
                                    "Date: ${existing.dateModified}->${comic.dateModified})"
                            )
                        }

                        // Collect callback data for after transaction
                        callbackData.add(comic to hasChanged)
                    } else {
                        comic.added = System.currentTimeMillis()
                        comicDao.insertComicBook(comic)
                        insertedCount++
                        changedComics.add(comic)
                        Timber.tag(TAG).d("New comic discovered: ${comic.fileName}")

                        // Collect callback data for after transaction
                        callbackData.add(comic to true)
                    }
                }
            }

            // Update scan metadata
            path.lastScannedAt = System.currentTimeMillis()
            path.comicCount = comics.size
            pathDao.updateLibraryPath(path)

            // Remove stale entries that were not found in this scan
            val validPaths = comics.map { it.filePath }
            if (validPaths.isNotEmpty()) {
                Timber.tag(TAG).d("Cleaning up stale entries for ${path.displayName} (keeping ${validPaths.size} files)")
                comicDao.deleteStaleComicsInPath(path.id, validPaths)
            } else if (comics.isEmpty()) {
                // If nothing found and path exists, clean all in this path
                Timber.tag(TAG).d("No comics found in ${path.displayName}, clearing all entries for this path")
                comicDao.deleteComicsByLibraryPath(path.id)
            }
        }

        // Invoke callbacks after transaction completes (interleaved metadata scan)
        for ((comic, hasChanged) in callbackData) {
            onFileDiscovered?.invoke(comic, hasChanged)
        }

        Timber.tag(TAG).d(
            "Refresh complete: $insertedCount inserted, $updatedCount updated, " +
                "${changedComics.size} total changes/new"
        )
        return comics to changedComics
    }

    /**
     * Scan a SAF DocumentFile URI (external storage with scoped access).
     * Uses SAFFileSystem (Okio FileSystem) for reliable directory listing
     * across all Android versions including Android 16+.
     *
     * @param path The LibraryPath with SAF URI
     * @param comics Accumulator list for discovered comics
     * @throws Exception if URI is invalid or access fails
     */
    private suspend fun scanSafUri(
        path: LibraryPath,
        comics: MutableList<ComicBook>,
    ) {
        val pathUri = Uri.parse(path.path)

        Timber.tag(TAG).d("Starting SAF scan with SAFFileSystem: ${path.displayName} (${path.path})")

        try {
            val fs = SAFFileSystem(context, pathUri)
            scanSafDirectory(fs, "".toPath(), path.id, comics)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "SAFFileSystem scan failed, falling back to DocumentFile")
            // Fallback to DocumentFile for older Android versions or edge cases
            val rootDir = DocumentFile.fromTreeUri(context, pathUri)
            if (rootDir != null && rootDir.isDirectory) {
                scanDocumentFileDirectory(rootDir, path.id, comics)
            } else {
                throw IllegalArgumentException("Invalid SAF URI or not a directory: ${path.path}")
            }
        }
    }

    /**
     * Scan a local file path (app cache/data directories only).
     *
     * @param path The LibraryPath with local file path
     * @param comics Accumulator list for discovered comics
     */
    private suspend fun scanLocalPath(
        path: LibraryPath,
        comics: MutableList<ComicBook>,
    ) {
        val rootDir = File(path.path)

        if (!rootDir.isDirectory) {
            Timber.tag(TAG).w("Local path is not a directory: ${path.path}")
            return
        }

        Timber.tag(TAG).d("Starting local scan: ${path.displayName} (${path.path})")
        scanLocalFileDirectory(rootDir, path.id, comics)
    }

    /**
     * Scan a library path using the StorageProvider abstraction.
     *
     * Used for remote storage types (Google Drive, future providers) where
     * the StorageProvider interface handles paginated/async directory listing.
     * Falls back to SAF/local scan if no provider factory is available.
     *
     * @param path The LibraryPath entity
     * @param comics Accumulator list for discovered comics
     */
    private suspend fun scanWithProvider(
        path: LibraryPath,
        comics: MutableList<ComicBook>,
    ) {
        val factory = storageProviderFactory
        if (factory == null) {
            Timber.tag(TAG).w("No StorageProviderFactory available, cannot scan: ${path.storageType}")
            return
        }

        val provider = factory.createProvider(path)
        if (provider == null) {
            Timber.tag(TAG).w("No provider available for storage type: ${path.storageType}")
            return
        }

        if (!provider.isAccessible()) {
            Timber.tag(TAG).w("Storage not accessible: ${path.displayName} (${path.storageType})")
            return
        }

        Timber.tag(TAG).d(
            "Starting provider scan: ${path.displayName} (${path.storageType})",
        )
        scanProviderDirectory(provider, "", path.id, comics)
    }

    /**
     * Recursively scan a directory using StorageProvider.
     *
     * @param provider The storage provider to query
     * @param directoryId Provider-specific directory identifier (empty string for root)
     * @param pathId Library path ID
     * @param comics Accumulator list
     */
    private suspend fun scanProviderDirectory(
        provider: StorageProvider,
        directoryId: String,
        pathId: Long,
        comics: MutableList<ComicBook>,
    ) {
        val result = provider.listFiles(directoryId)
        val entries = result.getOrElse { e ->
            Timber.tag(TAG).w(e, "Failed to list provider directory: $directoryId")
            return
        }

        Timber.tag(TAG).d("Provider listed $directoryId: ${entries.size} entries")

        for (entry in entries) {
            try {
                if (entry.isDirectory) {
                    scanProviderDirectory(provider, entry.identifier, pathId, comics)
                } else if (isComicFileName(entry.displayName)) {
                    val comic = ComicBook().apply {
                        libraryPathId = pathId
                        title = sanitizeTitle(entry.displayName)
                        filePath = entry.identifier
                        source = entry.identifier
                        fileName = entry.displayName
                        fileSize = entry.size
                        dateModified = entry.lastModified
                        format = detectFormat(entry.displayName)
                        storageType = provider.type.value
                        remoteId = entry.identifier
                    }
                    comics.add(comic)
                    Timber.tag(TAG).v("Found remote comic: ${comic.fileName}")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Error processing provider entry: ${entry.displayName}")
            }
        }
    }

    /**
     * Recursively scan a SAF directory using SAFFileSystem (Okio FileSystem).
     * This is the preferred method for Android 16+ where DocumentFile.listFiles() may fail.
     *
     * @param fs SAFFileSystem instance
     * @param dir Current directory path (relative to tree root)
     * @param pathId Library path ID
     * @param comics Accumulator list
     */
    private suspend fun scanSafDirectory(
        fs: SAFFileSystem,
        dir: Path,
        pathId: Long,
        comics: MutableList<ComicBook>,
    ) {
        val entries = fs.listOrNull(dir)
        if (entries == null) {
            Timber.tag(TAG).w("Failed to list SAF directory: $dir")
            return
        }

        Timber.tag(TAG).d("SAFFileSystem.list($dir) returned ${entries.size} entries")

        for (path in entries) {
            processSafEntry(fs, path, pathId, comics, dir)
        }
    }

    /**
     * Process a single SAF entry (file or directory). Reduces nesting in scanSafDirectory.
     */
    private suspend fun processSafEntry(
        fs: SAFFileSystem,
        path: Path,
        pathId: Long,
        comics: MutableList<ComicBook>,
        _parentDir: Path,
    ) {
        try {
            val metadata = fs.metadataOrNull(path)
            val name = path.name

            if (metadata?.isDirectory == true) {
                // Recursively scan subdirectory
                scanSafDirectory(fs, path, pathId, comics)
            } else if (metadata?.isRegularFile == true && isComicFileName(name)) {
                addComicFromSafEntry(fs, path, pathId, comics, name, metadata)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error processing file ${path.name}")
        }
    }

    /**
     * Add a comic file from SAF entry. Extracted to reduce nesting.
     */
    private suspend fun addComicFromSafEntry(
        fs: SAFFileSystem,
        path: Path,
        pathId: Long,
        comics: MutableList<ComicBook>,
        name: String,
        metadata: okio.FileMetadata,
    ) {
        val docUri = fs.getDocumentUri(path)

        val comic = ComicBook().apply {
            libraryPathId = pathId
            title = sanitizeTitle(name)
            filePath = docUri.toString() // SAF URI
            source = docUri.toString() // Sync source for cleanup
            this.fileName = name
            fileSize = metadata.size ?: 0
            dateModified = metadata.lastModifiedAtMillis ?: 0
            format = detectFormat(name)
        }

        comics.add(comic)
        Timber.tag(TAG).v("Found comic: ${comic.fileName}")
    }

    /**
     * Recursively scan a DocumentFile directory for comic files (SAF).
     *
     * @param dir Current directory to scan
     * @param pathId Library path ID
     * @param comics Accumulator list
     */
    @Deprecated("Use scanSafDirectory with SAFFileSystem for Android 16+ compatibility")
    private suspend fun scanDocumentFileDirectory(
        dir: DocumentFile,
        pathId: Long,
        comics: MutableList<ComicBook>,
    ) {
        val files = dir.listFiles()
        if (files == null) {
            Timber.tag(TAG).w("Failed to list files in SAF directory: ${dir.name}")
            return
        }

        for (file in files) {
            processDocumentFileEntry(file, pathId, comics)
        }
    }

    /**
     * Process a single DocumentFile entry. Reduces nesting in scanDocumentFileDirectory.
     */
    private suspend fun processDocumentFileEntry(
        file: DocumentFile,
        pathId: Long,
        comics: MutableList<ComicBook>,
    ) {
        try {
            if (file.isDirectory) {
                // Recursively scan subdirectory
                scanDocumentFileDirectory(file, pathId, comics)
            } else if (isComicFile(file)) {
                addComicFromDocumentFile(file, pathId, comics)
            }
        } catch (parseException: Exception) {
            Timber.tag(TAG).w(parseException, "Error processing file ${file?.name ?: "unknown"}")
        }
    }

    /**
     * Add a comic from DocumentFile. Extracted to reduce nesting.
     */
    private suspend fun addComicFromDocumentFile(
        file: DocumentFile,
        pathId: Long,
        comics: MutableList<ComicBook>,
    ) {
        val fileName = file.name ?: "unknown"
        val comic = ComicBook().apply {
            libraryPathId = pathId
            title = sanitizeTitle(fileName)
            filePath = file.uri.toString() // SAF URI
            source = file.uri.toString() // Sync source for cleanup
            this.fileName = fileName
            fileSize = file.length()
            dateModified = file.lastModified()
            format = detectFormat(fileName)
        }

        comics.add(comic)
        Timber.tag(TAG).v("Found comic: ${comic.fileName}")
    }

    /**
     * Recursively scan a local File directory for comic files.
     *
     * @param dir Current directory to scan
     * @param pathId Library path ID
     * @param comics Accumulator list
     */
    private suspend fun scanLocalFileDirectory(
        dir: File,
        pathId: Long,
        comics: MutableList<ComicBook>,
    ) {
        val files = dir.listFiles()
        if (files == null) {
            Timber.tag(TAG).w("Failed to list files in local directory: ${dir.name}")
            return
        }

        for (file in files) {
            processLocalFileEntry(file, pathId, comics)
        }
    }

    /**
     * Process a single local file entry. Reduces nesting in scanLocalFileDirectory.
     */
    private suspend fun processLocalFileEntry(
        file: File,
        pathId: Long,
        comics: MutableList<ComicBook>,
    ) {
        try {
            if (file.isDirectory) {
                // Recursively scan subdirectory
                scanLocalFileDirectory(file, pathId, comics)
            } else if (isComicFile(file)) {
                addComicFromLocalFile(file, pathId, comics)
            }
        } catch (fileProcessException: Exception) {
            Timber.tag(TAG).w(fileProcessException, "Error processing file ${file.name}")
        }
    }

    /**
     * Add a comic from a local file. Extracted to reduce nesting.
     */
    private suspend fun addComicFromLocalFile(
        file: File,
        pathId: Long,
        comics: MutableList<ComicBook>,
    ) {
        val comic = ComicBook().apply {
            libraryPathId = pathId
            title = sanitizeTitle(file.name)
            filePath = file.absolutePath // Local file path
            source = file.absolutePath // Sync source for cleanup
            this.fileName = file.name
            fileSize = file.length()
            dateModified = file.lastModified()
            format = detectFormat(file.name)
        }

        comics.add(comic)
        Timber.tag(TAG).v("Found comic: ${comic.fileName}")
    }

    /**
     * Check if a DocumentFile is a comic file based on extension.
     *
     * @param file DocumentFile to check
     * @return true if file has a comic extension
     */
    private fun isComicFile(file: DocumentFile): Boolean {
        val name = file.name ?: return false
        return isComicFileName(name)
    }

    /**
     * Check if a File is a comic file based on extension.
     *
     * @param file File to check
     * @return true if file has a comic extension
     */
    private fun isComicFile(file: File): Boolean {
        val name = file.name ?: return false
        return isComicFileName(name)
    }

    /**
     * Check if a file name has a comic extension.
     *
     * @param name File name to check
     * @return true if file has a comic extension
     */
    private fun isComicFileName(name: String): Boolean =
        name.lowercase().let { lowerName ->
            COMIC_EXTENSIONS.any { lowerName.endsWith(".$it") }
        }

    /**
     * Detect comic format from file name extension.
     *
     * @param fileName File name
     * @return Format string (e.g., "cbz", "cbr", "zip")
     */
    private fun detectFormat(fileName: String?): String {
        if (fileName == null) return "unknown"

        val dot = fileName.lastIndexOf('.')
        return if (dot > 0 && dot < fileName.length - 1) {
            fileName.substring(dot + 1).lowercase()
        } else {
            "unknown"
        }
    }

    /**
     * Sanitize comic title from file name (remove extension).
     *
     * @param fileName File name
     * @return Title without extension
     */
    private fun sanitizeTitle(fileName: String?): String {
        if (fileName == null) return ""

        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) {
            fileName.substring(0, dot)
        } else {
            fileName
        }
    }

    /**
     * Refresh all active library paths and return complete results.
     *
     * @param onFileDiscovered Optional callback for interleaved metadata scanning.
     * @return Pair of (All discovered comics) and (Consolidated subset of new/modified)
     */
    suspend fun refreshAllActivePaths(
        onFileDiscovered: (suspend (ComicBook, Boolean) -> Unit)? = null
    ): Pair<List<ComicBook>, List<ComicBook>> {
        val allComics = mutableListOf<ComicBook>()
        val allChanged = mutableListOf<ComicBook>()
        val activePaths = pathDao.getActiveLibraryPaths()

        for (path in activePaths) {
            try {
                val (pathComics, pathChanged) = refreshLibraryPath(path, onFileDiscovered)
                allComics.addAll(pathComics)
                allChanged.addAll(pathChanged)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to refresh library path: ${path.displayName}")
                // Continue scanning other paths
            }
        }

        return allComics to allChanged
    }
}
