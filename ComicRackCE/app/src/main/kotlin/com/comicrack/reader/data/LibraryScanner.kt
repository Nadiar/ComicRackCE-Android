package com.comicrack.reader.data

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.comicrack.core.storage.saf.SAFFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.File

/**
 * Comic library scanner for finding comic archive files.
 *
 * Supports scanning both traditional file paths and SAF URIs.
 * Recursively searches directories for supported comic formats.
 *
 * Supported formats: CBZ, CBR, CB7, CBT, RAR, ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2
 */
object LibraryScanner {
    private const val TAG = "LibraryScanner"

    private val supportedExtensions =
        setOf(
            "cbz",
            "zip",
            "cbp", // ZIP-based (cbp = WiFi sync format)
            "cbr",
            "rar", // RAR
            "cb7",
            "7z", // 7zip
            "cbt",
            "tar",
            "tgz",
            "tbz", // Tar variants
        )

    /**
     * Comic file with metadata extracted from path.
     *
     * @param path File path or URI string
     * @param filename Name of the file
     * @param extension File extension
     * @param isDirectory Whether this is a directory (for SAF)
     * @param uri Optional DocumentFile URI (for SAF files)
     */
    data class ComicFile(
        val path: String,
        val filename: String,
        val extension: String,
        val isDirectory: Boolean = false,
        val uri: String? = null,
    ) : Parcelable {
        /**
         * Extract series name and issue number from filename.
         *
         * Examples:
         * - "Amazing Spider-Man 001.cbz" â†’ ("Amazing Spider-Man", "001")
         * - "saga_2024_vol1.cbz" â†’ ("saga 2024 vol1", "")
         * - "comic.cbz" â†’ ("comic", "")
         */
        fun extractMetadata(): Pair<String, String> {
            val nameWithoutExt = filename.substringBeforeLast(".")

            // Try to extract issue number from the end
            val regex = Regex("""(.+?)\s*(\d+)\s*$""")
            val match = regex.find(nameWithoutExt)

            return if (match != null) {
                val (series, issue) = match.destructured
                Pair(series.trim(), issue)
            } else {
                Pair(nameWithoutExt.trim(), "")
            }
        }

        override fun writeToParcel(
            parcel: Parcel,
            flags: Int,
        ) {
            parcel.writeString(path)
            parcel.writeString(filename)
            parcel.writeString(extension)
            parcel.writeBoolean(isDirectory)
            parcel.writeString(uri)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<ComicFile> {
            override fun createFromParcel(parcel: Parcel): ComicFile =
                ComicFile(
                    path = parcel.readString() ?: "",
                    filename = parcel.readString() ?: "",
                    extension = parcel.readString() ?: "",
                    isDirectory = parcel.readBoolean(),
                    uri = parcel.readString(),
                )

            override fun newArray(size: Int): Array<ComicFile?> = arrayOfNulls(size)
        }
    }

    /**
     * Scan a file path for comic archives.
     * Supports both regular file paths and SAF URIs.
     *
     * @param context Android context
     * @param pathOrUri File path or SAF URI
     * @param recursive If true, scan subdirectories
     * @return List of found comic files
     */
    suspend fun scanPath(
        context: Context,
        pathOrUri: String,
        recursive: Boolean = true,
    ): List<ComicFile> =
        withContext(Dispatchers.IO) {
            try {
                when {
                    pathOrUri.startsWith("content://") -> scanSafUri(context, pathOrUri, recursive)
                    else -> scanFilePath(pathOrUri, recursive)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error scanning path: $pathOrUri")
                emptyList()
            }
        }

    /**
     * Scan a regular file path for comic archives.
     *
     * @param path Directory path to scan
     * @param recursive If true, scan subdirectories
     * @return List of found comic files
     */
    private fun scanFilePath(
        path: String,
        recursive: Boolean,
    ): List<ComicFile> {
        val comics = mutableListOf<ComicFile>()
        val directory = File(path)

        if (!directory.exists() || !directory.isDirectory) {
            Timber.tag(TAG).w("Path does not exist or is not a directory: $path")
            return emptyList()
        }

        try {
            (directory.listFiles() ?: emptyArray()).forEach { file ->
                when {
                    file.isFile && isSupportedFormat(file.extension) -> {
                        comics.add(
                            ComicFile(
                                path = file.absolutePath,
                                filename = file.name,
                                extension = file.extension,
                                isDirectory = false,
                            ),
                        )
                    }
                    file.isDirectory && recursive && shouldScanDirectory(file.name) -> {
                        // Recursively scan subdirectories (including _sync for WiFi synced comics)
                        comics.addAll(scanFilePath(file.absolutePath, recursive))
                    }
                }
            }
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "Permission denied scanning: $path")
        }

        return comics.sortedBy { it.filename.lowercase() }
    }

    /**
     * Scan a SAF (Storage Access Framework) URI for comic archives.
     * Uses SAFFileSystem (Okio FileSystem) for reliable directory listing
     * across all Android versions including Android 16+.
     *
     * @param context Android context
     * @param uri SAF URI to scan
     * @param recursive If true, scan subdirectories
     * @return List of found comic files
     */
    private fun scanSafUri(
        context: Context,
        uri: String,
        recursive: Boolean,
    ): List<ComicFile> {
        val comics = mutableListOf<ComicFile>()

        try {
            val treeUri = Uri.parse(uri)
            val fs = SAFFileSystem(context, treeUri)
            val root = "".toPath()

            comics.addAll(scanSafDirectory(fs, root, recursive))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error scanning SAF URI: $uri")
        }

        return comics.sortedBy { it.filename.lowercase() }
    }

    /**
     * Recursively scan a directory using SAFFileSystem.
     */
    private fun scanSafDirectory(
        fs: SAFFileSystem,
        dir: okio.Path,
        recursive: Boolean,
    ): List<ComicFile> {
        val comics = mutableListOf<ComicFile>()

        try {
            val entries = fs.listOrNull(dir).orEmpty()
            Timber.tag(TAG).d("SAFFileSystem.list($dir) returned ${entries.size} entries")

            for (path in entries) {
                val metadata = fs.metadataOrNull(path)
                val name = path.name

                if (metadata?.isDirectory == true) {
                    // It's a directory
                    if (recursive && shouldScanDirectory(name)) {
                        comics.addAll(scanSafDirectory(fs, path, recursive))
                    }
                } else if (metadata?.isRegularFile == true) {
                    // It's a file - check extension
                    val extension = name.substringAfterLast(".", "")
                    if (isSupportedFormat(extension)) {
                        // Get the document URI from SAFFileSystem (properly resolves document ID)
                        val docUri = fs.getDocumentUri(path)

                        comics.add(
                            ComicFile(
                                path = docUri.toString(),
                                filename = name,
                                extension = extension,
                                isDirectory = false,
                                uri = docUri.toString(),
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error scanning SAF directory: $dir")
        }

        return comics
    }

    /**
     * Recursively scan a DocumentFile (SAF) directory.
     *
     * @deprecated Use scanSafDirectory with SAFFileSystem instead.
     * Kept for fallback compatibility.
     */
    @Deprecated("Use scanSafDirectory with SAFFileSystem")
    @Suppress("unused")
    private fun scanDocumentFile(
        context: Context,
        documentFile: DocumentFile,
        recursive: Boolean,
    ): List<ComicFile> {
        val comics = mutableListOf<ComicFile>()

        try {
            // Try DocumentFile.listFiles() first
            var files = documentFile.listFiles()
            Timber.tag(TAG).d("listFiles() returned ${files.size} items for ${documentFile.name}")

            // ANDROID 16 WORKAROUND: If empty, try ContentResolver query directly
            if (files.isEmpty()) {
                Timber.tag(TAG).d("Android 16 workaround: Using ContentResolver query")
                files = listFilesViaContentResolver(context, documentFile)
                Timber.tag(TAG).d("ContentResolver returned ${files.size} items")
            }

            files.forEach { file ->
                when {
                    file.isFile && isSupportedFormat(file.name?.substringAfterLast(".") ?: "") -> {
                        comics.add(
                            ComicFile(
                                path = file.uri.toString(),
                                filename = file.name ?: "unknown",
                                extension = file.name?.substringAfterLast(".") ?: "",
                                isDirectory = false,
                                uri = file.uri.toString(),
                            ),
                        )
                    }
                    file.isDirectory && recursive && shouldScanDirectory(file.name) -> {
                        // Recursively scan subdirectories (including _sync for WiFi synced comics)
                        comics.addAll(scanDocumentFile(context, file, recursive))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error scanning DocumentFile")
        }

        return comics
    }

    /**
     * ANDROID 16 WORKAROUND: List files using ContentResolver directly.
     *
     * On Android 16+, DocumentFile.listFiles() may return empty arrays due to
     * aggressive SAF caching. Direct ContentResolver queries still work.
     */
    private fun listFilesViaContentResolver(
        context: Context,
        parent: DocumentFile,
    ): Array<DocumentFile> {
        val results = mutableListOf<DocumentFile>()

        try {
            val treeUri = parent.uri
            val documentId =
                if (DocumentsContract.isDocumentUri(context, treeUri)) {
                    DocumentsContract.getDocumentId(treeUri)
                } else {
                    DocumentsContract.getTreeDocumentId(treeUri)
                }

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

            // Try to refresh to bypass caches
            try {
                context.contentResolver.refresh(childrenUri, null, null)
            } catch (e: Exception) {
                // Ignore if not supported
            }

            context.contentResolver
                .query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    if (idIdx != -1 && nameIdx != -1 && mimeIdx != -1) {
                        Timber.tag(TAG).d("ContentResolver query returned ${cursor.count} rows")
                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(idIdx)
                            val name = cursor.getString(nameIdx)
                            val mime = cursor.getString(mimeIdx)

                            Timber.tag(TAG).d("  Found: $name (mime=$mime)")

                            // Build URI for this child document
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val childDoc = DocumentFile.fromSingleUri(context, childUri)
                            if (childDoc != null) {
                                results.add(childDoc)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "ContentResolver query failed")
        }

        return results.toTypedArray()
    }

    /**
     * Check if a directory should be scanned.
     *
     * By default, hidden directories (starting with '.') are skipped,
     * but '_sync' is a special case for WiFi synced comics.
     */
    private fun shouldScanDirectory(name: String?): Boolean {
        if (name == null) return false
        // Special case: _sync folder contains WiFi synced comics
        if (name.equals("_sync", ignoreCase = true)) return true
        // Skip hidden directories
        if (name.startsWith(".")) return false
        return true
    }

    /**
     * Check if a file extension is a supported comic format.
     */
    private fun isSupportedFormat(extension: String): Boolean {
        val ext = extension.lowercase()
        return supportedExtensions.contains(ext) ||
            ext.endsWith(".gz") ||
            // .tar.gz, .cbz.gz, etc.
            ext.endsWith(".bz2") // .tar.bz2, etc.
    }

    /**
     * Get all supported file extensions.
     */
    fun getSupportedExtensions(): List<String> = supportedExtensions.toList()
}
