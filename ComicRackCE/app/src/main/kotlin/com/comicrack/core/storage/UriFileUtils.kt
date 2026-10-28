package com.comicrack.core.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Modern SAF (Storage Access Framework) utilities for working with Uris instead of File paths.
 * Replaces legacy java.io.File operations with Uri-based access for Android 10+ compatibility.
 *
 * Key methods:
 * - listFilesInUri: Replace File.listFiles() with DocumentFile traversal
 * - openInputStream: Replace FileInputStream with ContentResolver
 * - openOutputStream: Replace FileOutputStream with ContentResolver
 * - findFile: Recursive file search using SAF
 */
object UriFileUtils {
    private const val TAG = "UriFileUtils"

    /**
     * Lists all files in a SAF tree URI.
     * Replaces File.listFiles() for user-provided directories.
     *
     * @param context The application context
     * @param treeUri The SAF tree URI (e.g., from ACTION_OPEN_DOCUMENT_TREE)
     * @return List of DocumentFile objects, or empty list if invalid
     */
    @JvmStatic
    fun listFilesInUri(
        context: Context,
        treeUri: Uri?,
    ): List<DocumentFile> {
        if (treeUri == null) {
            Timber.Forest.tag(TAG).w("listFilesInUri: treeUri is null")
            return emptyList()
        }
        return try {
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            dir.listFiles().toList()
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "listFilesInUri failed for $treeUri")
            emptyList()
        }
    }

    /**
     * Finds a file or directory recursively within a SAF tree.
     * Replaces legacy File.findFile() for user content.
     *
     * @param context The application context
     * @param treeUri The root SAF tree URI
     * @param fileName The name of the file to find
     * @param maxDepth Maximum recursion depth
     * @param writable If true, only return writable files
     * @return The DocumentFile if found, null otherwise
     */
    @JvmStatic
    fun findFile(
        context: Context,
        treeUri: Uri?,
        fileName: String,
        maxDepth: Int = 5,
        writable: Boolean = false,
    ): DocumentFile? {
        if (treeUri == null || maxDepth < 0) {
            return null
        }
        return try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            findFileRecursive(root, fileName, maxDepth, writable)
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "findFile failed for $fileName in $treeUri")
            null
        }
    }

    /**
     * Recursive helper to search for a file or directory within a [DocumentFile] tree.
     *
     * This performs a depth-limited DFS (children first) and respects the [writable]
     * flag by only returning files that are writable when requested.
     *
     * @param dir The current directory to search
     * @param fileName The filename to match (exact match)
     * @param maxDepth Remaining recursion depth (stop when < 0)
     * @param writable If true, only return entries where `canWrite()` is true
     * @return The matching [DocumentFile] if found, otherwise `null` when not found
     *         or when recursion depth is exhausted.
     */
    private fun findFileRecursive(
        dir: DocumentFile,
        fileName: String,
        maxDepth: Int,
        writable: Boolean,
    ): DocumentFile? {
        if (maxDepth < 0 || !dir.isDirectory) {
            return null
        }

        // Check direct children first
        dir.listFiles().forEach { file ->
            if (file.name == fileName && (!writable || file.canWrite())) {
                return file
            }
        }

        // Recurse into subdirectories
        if (maxDepth > 0) {
            dir.listFiles().forEach { file ->
                if (file.isDirectory && !file.name.isNullOrEmpty() && file.name?.get(0) != '.') {
                    val found = findFileRecursive(file, fileName, maxDepth - 1, writable)
                    if (found != null) {
                        return found
                    }
                }
            }
        }

        return null
    }

    /**
     * Opens an input stream from a Uri.
     * Replaces new FileInputStream(path) for all content access.
     *
     * NOTE: The caller is responsible for closing the returned stream when finished.
     *
     * @param context The application context
     * @param uri The content URI to open
     * @return An [java.io.InputStream] for reading the content, or `null` if the URI was null
     *         or opening failed (e.g., permission denied).
     */
    @JvmStatic
    fun openInputStream(
        context: Context,
        uri: Uri?,
    ): InputStream? {
        if (uri == null) {
            Timber.Forest.tag(TAG).w("openInputStream: uri is null")
            return null
        }
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "openInputStream failed for $uri")
            null
        }
    }

    /**
     * Opens an output stream to a Uri.
     * Replaces new FileOutputStream(path) for writing user content.
     *
     * NOTE: The caller is responsible for closing the returned stream when finished.
     * The [mode] parameter follows ContentResolver semantics (e.g., "w" to create/truncate,
     * "wa" to append when supported).
     *
     * @param context The application context
     * @param uri The content URI to write to
     * @param mode Write mode passed to the [android.content.ContentResolver.openOutputStream] call
     * @return An [java.io.OutputStream] for writing, or `null` if the URI was null or opening failed
     *         (e.g., permission denied).
     */
    @JvmStatic
    fun openOutputStream(
        context: Context,
        uri: Uri?,
        mode: String = "w",
    ): OutputStream? {
        if (uri == null) {
            Timber.Forest.tag(TAG).w("openOutputStream: uri is null")
            return null
        }
        return try {
            context.contentResolver.openOutputStream(uri, mode)
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "openOutputStream failed for $uri")
            null
        }
    }

    /**
     * Checks if a file exists in a SAF tree.
     * @param context The application context
     * @param treeUri The SAF tree root
     * @param fileName The file name to check
     * @return True if the file exists
     */
    @JvmStatic
    fun fileExists(
        context: Context,
        treeUri: Uri?,
        fileName: String,
    ): Boolean {
        if (treeUri == null) return false
        return try {
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            dir.findFile(fileName) != null
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "fileExists check failed for $fileName")
            false
        }
    }

    /**
     * Deletes a file in a SAF tree.
     *
     * Note: Deletion can fail for permissions or transient IO errors; callers should treat
     * a return value of `false` as an indication that the file may still exist.
     *
     * @param documentFile The [DocumentFile] to delete
     * @return `true` if deletion completed successfully, `false` otherwise
     */
    @JvmStatic
    fun deleteFile(documentFile: DocumentFile?): Boolean {
        if (documentFile == null) return false
        return try {
            documentFile.delete()
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "deleteFile failed for ${documentFile.name}")
            false
        }
    }

    /**
     * Creates a new file in a SAF tree directory.
     *
     * @param documentDir The parent [DocumentFile] directory where the file should be created
     * @param mimeType The MIME type for the new file (e.g., "text/plain", "application/octet-stream")
     * @param fileName The name for the new file
     * @return The created [DocumentFile], or `null` if creation failed (invalid directory,
     *         unsupported mime type, or permission error).
     */
    @JvmStatic
    fun createFile(
        documentDir: DocumentFile?,
        mimeType: String,
        fileName: String,
    ): DocumentFile? {
        if (documentDir == null || !documentDir.isDirectory) return null
        return try {
            documentDir.createFile(mimeType, fileName)
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "createFile failed for $fileName")
            null
        }
    }

    /**
     * Copies data from one stream to another.
     * Utility for replicating the legacy FileUtils.copy() behavior.
     * @param inputStream Source stream
     * @param outputStream Destination stream
     * @return Number of bytes copied
     */
    @JvmStatic
    fun copyStream(
        inputStream: InputStream,
        outputStream: OutputStream,
    ): Long =
        try {
            val buffer = ByteArray(65536)
            var totalBytes = 0L
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                totalBytes += read
            }
            totalBytes
        } catch (e: IOException) {
            Timber.Forest.tag(TAG).e(e, "copyStream failed")
            0L
        }

    /**
     * Generates a [Uri] from a [DocumentFile] for use with [android.content.ContentResolver].
     *
     * @param documentFile The [DocumentFile] to inspect
     * @return The [Uri] of the document, or `null` if [documentFile] is `null`
     */
    @JvmStatic
    fun getUri(documentFile: DocumentFile?): Uri? = documentFile?.uri

    /**
     * Gets the file name from a Uri.
     *
     * The method uses SAF metadata when available (OpenableColumns.DISPLAY_NAME) and
     * falls back to the last path segment for non-document URIs.
     *
     * @param context The application context
     * @param uri The content URI to inspect
     * @return The file name if available, otherwise `null` when it cannot be resolved
     */
    @JvmStatic
    fun getFileName(
        context: Context,
        uri: Uri?,
    ): String? {
        if (uri == null) return null
        return try {
            when {
                DocumentsContract.isDocumentUri(context, uri) -> {
                    DocumentsContract
                        .getDocumentId(uri)
                        .substringAfterLast(':')
                }
                uri.scheme == ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index =
                                cursor.getColumnIndex(
                                    OpenableColumns.DISPLAY_NAME,
                                )
                            if (index >= 0) cursor.getString(index) else null
                        } else {
                            null
                        }
                    }
                }
                else -> uri.lastPathSegment
            }
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "getFileName failed for $uri")
            null
        }
    }

    /**
     * Gets the file size from a Uri.
     * @param context The application context
     * @param uri The content URI
     * @return The file size in bytes, or -1 if unavailable
     */
    @JvmStatic
    fun getFileSize(
        context: Context,
        uri: Uri?,
    ): Long {
        if (uri == null) return -1L
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index =
                        cursor.getColumnIndex(
                            OpenableColumns.SIZE,
                        )
                    if (index >= 0) cursor.getLong(index) else -1L
                } else {
                    -1L
                }
            } ?: -1L
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "getFileSize failed for $uri")
            -1L
        }
    }

    /**
     * Helper to get user-friendly name from URI.
     * Used extensively in UI to show folder names.
     *
     * @param context App context
     * @param uriString String representation of the URI
     * @return A friendly display name for the URI when available, `"Not set"` for
     *         blank values, or the original `uriString` on error or when no name can
     *         be derived.
     */
    @JvmStatic
    fun getFriendlyPath(
        context: Context,
        uriString: String?,
    ): String {
        if (uriString.isNullOrEmpty()) return "Not set"
        return try {
            val uri = Uri.parse(uriString)
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc?.name ?: uri.lastPathSegment ?: uriString
        } catch (e: Exception) {
            uriString
        }
    }
}
