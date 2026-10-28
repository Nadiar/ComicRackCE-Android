package com.comicrack.core.storage.saf

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.sink
import okio.source
import timber.log.Timber
import java.io.FileNotFoundException

/**
 * An Okio FileSystem implementation backed by Android's Storage Access Framework (SAF).
 *
 * This provides a consistent file-system-like API over SAF URIs, handling:
 * - Document ID resolution via ContentResolver queries (not DocumentFile.listFiles())
 * - Caching of document IDs for performance
 * - Proper read/write streams via ContentResolver
 *
 * Usage:
 * ```kotlin
 * val fs = SAFFileSystem(context, userSelectedTreeUri)
 * val root = "".toPath()
 *
 * fs.list(root).forEach { path ->
 *     if (fs.metadataOrNull(path)?.isDirectory == true) {
 *         // It's a directory
 *     } else {
 *         // It's a file - read it
 *         fs.read(path) { readByteString() }
 *     }
 * }
 * ```
 *
 * @param context Android context for ContentResolver access
 * @param treeUri The URI from ACTION_OPEN_DOCUMENT_TREE
 */
class SAFFileSystem(
    private val context: Context,
    private val treeUri: Uri,
) : FileSystem() {
    companion object {
        private const val TAG = "SAFFileSystem"
    }

    /**
     * Cache mapping Path -> DocumentId to avoid repeated database lookups.
     * The root path ("") maps to the tree's root document ID.
     */
    private val idCache =
        mutableMapOf<Path, String>().apply {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            Timber.tag(TAG).d("SAFFileSystem init: treeUri=$treeUri, rootDocId=$rootDocId")
            put("".toPath(), rootDocId)
        }

    /**
     * Cache for file metadata to avoid repeated queries.
     */
    private val metadataCache = mutableMapOf<Path, CachedMetadata>()

    private data class CachedMetadata(
        val documentId: String,
        val displayName: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    /**
     * Get the document ID for a path, resolving it if not cached.
     */
    private fun getDocumentId(path: Path): String = idCache[path] ?: resolveIdFromPath(path)

    /**
     * Get the document URI for a path.
     * This is the content:// URI that can be used with ContentResolver.
     */
    fun getDocumentUri(path: Path): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, getDocumentId(path))

    /**
     * Resolve a document ID by querying the parent directory.
     * Includes a rescue mechanism that clears caches and forces a refresh on failure.
     */
    private fun resolveIdFromPath(path: Path): String {
        return try {
            resolveIdFromPathInternal(path)
        } catch (e: FileNotFoundException) {
            Timber.tag(TAG).d("resolveIdFromPath($path): Initial resolution failed, attempting rescue...")
            clearCaches()
            val parent = path.parent ?: "".toPath()
            refresh(parent)
            try {
                resolveIdFromPathInternal(path)
            } catch (ex: FileNotFoundException) {
                Timber.tag(TAG).w("resolveIdFromPath($path): Rescue failed: name '${path.name}' not found")
                throw ex
            }
        }
    }

    private fun resolveIdFromPathInternal(path: Path): String {
        val parent = path.parent ?: "".toPath()
        val parentId = getDocumentId(parent)
        val name = path.name

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)

        Timber.tag(TAG).v("resolveIdFromPathInternal($path): querying children of $parentId")
        context.contentResolver
            .query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIdx)
                    val docId = cursor.getString(idIdx)
                    if (displayName == name) {
                        Timber.tag(TAG).v("resolveIdFromPathInternal($path): matched name '$name' -> id=$docId")
                        idCache[path] = docId
                        return docId
                    }
                }
            }

        throw FileNotFoundException("Path not found: $path")
    }

    /**
     * Build the document URI for a given path.
     */
    private fun buildDocumentUri(path: Path): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, getDocumentId(path))

    override fun canonicalize(path: Path): Path = path.normalized()

    override fun metadataOrNull(path: Path): FileMetadata? {
        val cached = metadataCache[path]
        if (cached != null) {
            return FileMetadata(
                isRegularFile = !cached.isDirectory,
                isDirectory = cached.isDirectory,
                size = if (cached.isDirectory) null else cached.size,
                lastModifiedAtMillis = cached.lastModified.takeIf { it > 0 },
            )
        }

        return try {
            val docUri = buildDocumentUri(path)
            queryMetadata(path, docUri)
        } catch (e: Exception) {
            Timber.tag(TAG).d("metadataOrNull($path): Query failed, attempting refresh: ${e.message}")
            try {
                refresh(path)
                val docUri = buildDocumentUri(path)
                queryMetadata(path, docUri)
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun queryMetadata(path: Path, docUri: Uri): FileMetadata? {
        context.contentResolver
            .query(
                docUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val docId =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                        )
                    val displayName =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        )
                    val mimeType =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE),
                        )
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                    val lastModified =
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                        )

                    val metadata = CachedMetadata(docId, displayName, mimeType, size, lastModified)
                    metadataCache[path] = metadata
                    idCache[path] = docId

                    return FileMetadata(
                        isRegularFile = !metadata.isDirectory,
                        isDirectory = metadata.isDirectory,
                        size = if (metadata.isDirectory) null else size,
                        lastModifiedAtMillis = lastModified.takeIf { it > 0 },
                    )
                }
            }
        throw FileNotFoundException("Metadata not found")
    }

    override fun list(dir: Path): List<Path> = list(dir, throwOnFailure = true)

    override fun listOrNull(dir: Path): List<Path>? =
        try {
            list(dir, throwOnFailure = false)
        } catch (e: Exception) {
            null
        }

    private fun list(
        dir: Path,
        throwOnFailure: Boolean,
    ): List<Path> {
        val dirId =
            try {
                getDocumentId(dir)
            } catch (e: FileNotFoundException) {
                if (throwOnFailure) throw IOException("Directory not found: $dir", e)
                return emptyList()
            }

        // Force cache refresh for Android 16+
        refresh(dir)

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        Timber.tag(TAG).d("list($dir): dirId=$dirId, childrenUri=$childrenUri")
        val foundPaths = mutableListOf<Path>()

        try {
            val cursor = context.contentResolver
                .query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null,
                    null,
                    null,
                )

            if (cursor == null) {
                Timber.tag(TAG).w("Query returned null cursor for $dir")
            } else {
                Timber.tag(TAG).d("Query returned cursor with ${cursor.count} rows for $dir")
                cursor.use {
                    val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idIdx)
                        val name = cursor.getString(nameIdx)
                        val mimeType = cursor.getString(mimeIdx)
                        val size = cursor.getLong(sizeIdx)
                        val lastModified = cursor.getLong(modifiedIdx)

                        val childPath = dir / name

                        // Pre-populate caches for faster subsequent access
                        idCache[childPath] = docId
                        metadataCache[childPath] = CachedMetadata(docId, name, mimeType, size, lastModified)

                        foundPaths.add(childPath)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "list() failed for $dir")
            if (throwOnFailure) throw IOException("Failed to list directory: $dir", e)
        }

        Timber.tag(TAG).d("list($dir): found ${foundPaths.size} entries")
        return foundPaths
    }

    override fun source(file: Path): Source {
        val docUri = buildDocumentUri(file)
        return context.contentResolver.openInputStream(docUri)?.source()
            ?: throw IOException("Could not open $file for reading")
    }

    override fun sink(
        file: Path,
        mustCreate: Boolean,
    ): Sink {
        if (mustCreate) {
            // Check if file exists
            val existing = metadataOrNull(file)
            if (existing != null) {
                throw IOException("File already exists: $file")
            }
            // Create the file
            val parent = file.parent ?: "".toPath()
            
            // Force refresh of parent before checking/creating to reduce collision risk
            refresh(parent)
            
            val parentId = getDocumentId(parent)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)

            val newUri =
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    "application/octet-stream",
                    file.name,
                ) ?: throw IOException("Could not create file: $file")

            // Verify the created file has the expected name
            // SAF auto-renames on collision (e.g., "file.cbp" â€”> "file (1).cbp")
            val createdName =
                context.contentResolver
                    .query(
                        newUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)
                        } else {
                            null
                        }
                    }

            if (createdName != null && createdName != file.name) {
                // SAF auto-renamed due to collision
                // This happens if our cache missed an existing file
                Timber.w(
                    "sink: SAF renamed '${file.name}' to '$createdName' " +
                        "due to collision, deleting duplicate URI=$newUri",
                )
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, newUri)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete auto-renamed file duplicate")
                }

                // Clear caches and try to open the existing file for writing instead
                clearCaches()
                refresh(parent)
                
                // Re-resolve and use the existing file
                val existingUri = buildDocumentUri(file)
                return context.contentResolver.openOutputStream(existingUri, "wt")?.sink()
                    ?: throw IOException("Could not open existing $file after collision")
            }

            // Update caches with the newly created file
            val newDocId = DocumentsContract.getDocumentId(newUri)
            idCache[file] = newDocId

            return context.contentResolver.openOutputStream(newUri, "wt")?.sink()
                ?: throw IOException("Could not open $file for writing")
        }

        val docUri = buildDocumentUri(file)
        return context.contentResolver.openOutputStream(docUri, "wt")?.sink()
            ?: throw IOException("Could not open $file for writing")
    }

    override fun appendingSink(
        file: Path,
        mustExist: Boolean,
    ): Sink {
        if (mustExist && metadataOrNull(file) == null) {
            throw IOException("File does not exist: $file")
        }

        val docUri = buildDocumentUri(file)
        return context.contentResolver.openOutputStream(docUri, "wa")?.sink()
            ?: throw IOException("Could not open $file for appending")
    }

    override fun createDirectory(
        dir: Path,
        mustCreate: Boolean,
    ) {
        val existing = metadataOrNull(dir)
        if (existing != null) {
            if (mustCreate) throw IOException("Directory already exists: $dir")
            if (!existing.isDirectory) throw IOException("Not a directory: $dir")
            return
        }

        val parent = dir.parent ?: "".toPath()
        val parentId = getDocumentId(parent)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)

        val newUri =
            DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                dir.name,
            ) ?: throw IOException("Could not create directory: $dir")

        // Verify the created directory has the expected name
        // SAF auto-renames on collision (e.g., "_sync" → "_sync (1)")
        // This detects race conditions where another process created the directory
        val createdName =
            context.contentResolver
                .query(
                    newUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }

        if (createdName != null && createdName != dir.name) {
            // SAF auto-renamed due to collision - delete the duplicate and use existing
            Timber.w(
                "createDirectory: SAF renamed '${dir.name}' to '$createdName' " +
                    "due to collision, deleting duplicate URI=$newUri",
            )
            try {
                DocumentsContract.deleteDocument(context.contentResolver, newUri)
                Timber.d("createDirectory: Deleted duplicate '$createdName'")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete auto-renamed duplicate: $createdName")
            }

            // Clear caches and resolve the existing directory
            clearCaches()

            // Refresh parent and parent's parent just in case, then clear caches
            refresh(dir.parent ?: "".toPath())
            clearCaches()

            try {
                // Re-resolve the ID. The improved resolveIdFromPath will refresh and find it.
                val existingId = resolveIdFromPath(dir)
                idCache[dir] = existingId
                Timber.d("createDirectory: Found existing '${dir.name}' (id=$existingId) after collision")
                return
            } catch (e: Exception) {
                Timber.w(e, "Failed to resolve existing directory after collision: $dir")
            }

            throw IOException(
                "Directory collision detected for '$dir', " +
                    "but original folder could not be found after refresh."
            )
        }

        // Update cache with the newly created directory
        val newDocId = DocumentsContract.getDocumentId(newUri)
        idCache[dir] = newDocId
    }

    override fun atomicMove(
        source: Path,
        target: Path,
    ) {
        // SAF doesn't support atomic moves across providers
        // For same-provider moves, we could use DocumentsContract.moveDocument (API 24+)
        // but it requires additional permissions and provider support

        // For now, throw unsupported
        throw IOException("Atomic move not supported by SAF")
    }

    override fun delete(
        path: Path,
        mustExist: Boolean,
    ) {
        val metadata = metadataOrNull(path)
        if (metadata == null) {
            if (mustExist) throw IOException("File not found: $path")
            return
        }

        val docUri = buildDocumentUri(path)
        val deleted = try {
            DocumentsContract.deleteDocument(context.contentResolver, docUri)
        } catch (e: Exception) {
            Timber.tag(TAG).w("delete($path): Document deletion failed: ${e.message}")
            false
        }

        if (!deleted && mustExist) {
            throw IOException("Could not delete: $path")
        }

        // Clear from caches
        idCache.remove(path)
        metadataCache.remove(path)
        
        // Also clear parent's children cache if we might have it
        path.parent?.let { parent ->
            // Invalidate SAF cache for the parent so list() and others see the change
            refresh(parent)
        }
    }

    override fun createSymlink(
        source: Path,
        target: Path,
    ): Unit = throw IOException("Symlinks not supported by SAF")

    override fun openReadOnly(file: Path): FileHandle {
        val docUri = buildDocumentUri(file)
        val pfd =
            context.contentResolver.openFileDescriptor(docUri, "r")
                ?: throw IOException("Could not open $file")

        return SAFFileHandle(pfd, readWrite = false)
    }

    override fun openReadWrite(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean,
    ): FileHandle {
        if (mustCreate && mustExist) {
            throw IllegalArgumentException("Cannot require both mustCreate and mustExist")
        }

        val existing = metadataOrNull(file)
        if (mustCreate && existing != null) {
            throw IOException("File already exists: $file")
        }
        if (mustExist && existing == null) {
            throw IOException("File does not exist: $file")
        }

        // Create if needed
        if (existing == null) {
            val parent = file.parent ?: "".toPath()
            val parentId = getDocumentId(parent)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)

            DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                "application/octet-stream",
                file.name,
            ) ?: throw IOException("Could not create file: $file")
        }

        val docUri = buildDocumentUri(file)
        val pfd =
            context.contentResolver.openFileDescriptor(docUri, "rw")
                ?: throw IOException("Could not open $file for read/write")

        return SAFFileHandle(pfd, readWrite = true)
    }

    /**
     * Clear all caches. Useful if underlying storage has changed.
     */
    fun clearCaches() {
        idCache.clear()
        metadataCache.clear()
        // Re-add root
        idCache["".toPath()] = DocumentsContract.getTreeDocumentId(treeUri)
    }

    /**
     * Force a refresh of the cached data for the given path.
     * Uses DocumentsContract.METHOD_NOTIFY_REFRESH which is the official way
     * to invalidate SAF caches on Android 16+.
     */
    private fun refresh(path: Path) {
        try {
            val docId = getDocumentId(path)
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            
            // Refresh document metadata
            Timber.tag(TAG).v("refresh($path): Refreshing document $docId")
            context.contentResolver.refresh(uri, null, null)
            
            // Also refresh children list
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            Timber.tag(TAG).v("refresh($path): Refreshing children of $docId")
            context.contentResolver.refresh(childrenUri, null, null)
            
            Timber.tag(TAG).d("Refreshed SAF cache for $path")
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to refresh SAF cache for $path: ${e.message}")
        }
    }
}
