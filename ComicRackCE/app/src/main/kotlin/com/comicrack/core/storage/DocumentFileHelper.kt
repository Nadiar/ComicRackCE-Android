package com.comicrack.core.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import timber.log.Timber

/**
 * High-level helpers for DocumentFile operations.
 * Provides convenient methods for common SAF patterns.
 */
object DocumentFileHelper {
    private const val TAG = "DocumentFileHelper"

    /**
     * Wraps a SAF tree URI in a DocumentFile.
     * @param context The application context
     * @param treeUri The SAF tree URI
     * @return DocumentFile representing the root, or null if invalid
     */
    fun getDocumentFileFromTree(
        context: Context,
        treeUri: Uri?,
    ): DocumentFile? {
        if (treeUri == null) return null
        return try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "getDocumentFileFromTree failed")
            null
        }
    }

    /**
     * Wraps a single file Uri (from ACTION_OPEN_DOCUMENT) in a DocumentFile.
     * @param context The application context
     * @param fileUri The single file URI
     * @return DocumentFile representing the file, or null if invalid
     */
    fun getDocumentFileFromSingleUri(
        context: Context,
        fileUri: Uri?,
    ): DocumentFile? {
        if (fileUri == null) return null
        return try {
            DocumentFile.fromSingleUri(context, fileUri)
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "getDocumentFileFromSingleUri failed")
            null
        }
    }

    /**
     * Lists all files in a directory matching an optional filter.
     * @param dir The parent DocumentFile directory
     * @param filter Optional filename filter predicate
     * @return List of matching DocumentFiles
     */
    fun listFiles(
        dir: DocumentFile?,
        filter: ((name: String) -> Boolean)? = null,
    ): List<DocumentFile> {
        if (dir == null || !dir.isDirectory) return emptyList()
        return try {
            dir
                .listFiles()
                .filter { filter == null || (it.name?.let { name -> filter(name) } ?: false) }
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "listFiles failed for ${dir.name}")
            emptyList()
        }
    }

    /**
     * Lists all files recursively in a directory.
     * @param dir The parent DocumentFile directory
     * @param maxDepth Maximum recursion depth
     * @param filter Optional filename filter predicate
     * @return List of all matching DocumentFiles in directory tree
     */
    fun listFilesRecursively(
        dir: DocumentFile?,
        maxDepth: Int = 5,
        filter: ((name: String) -> Boolean)? = null,
    ): List<DocumentFile> {
        if (dir == null || !dir.isDirectory || maxDepth < 0) return emptyList()
        val foundFiles = mutableListOf<DocumentFile>()
        try {
            dir.listFiles().forEach { file ->
                if (filter == null || (file.name?.let { name -> filter(name) } ?: false)) {
                    foundFiles.add(file)
                }
                if (file.isDirectory && maxDepth > 0) {
                    foundFiles.addAll(listFilesRecursively(file, maxDepth - 1, filter))
                }
            }
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "listFilesRecursively failed for ${dir.name}")
        }
        return foundFiles
    }

    /**
     * Finds a single file by name in a directory tree.
     * @param dir The root directory to search
     * @param fileName The name of the file to find
     * @param maxDepth Maximum recursion depth
     * @return The DocumentFile if found, null otherwise
     */
    fun findFile(
        dir: DocumentFile?,
        fileName: String,
        maxDepth: Int = 5,
    ): DocumentFile? {
        if (dir == null || !dir.isDirectory || maxDepth < 0) return null
        return try {
            // Check direct children
            dir.listFiles().forEach { file ->
                if (file.name == fileName) return file
            }
            // Recurse into subdirectories
            dir.listFiles().forEach { file ->
                if (file.isDirectory) {
                    val found = findFile(file, fileName, maxDepth - 1)
                    if (found != null) return found
                }
            }
            null
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "findFile failed for $fileName")
            null
        }
    }

    /**
     * Gets the file tree structure as a formatted string.
     * Useful for debugging and logging.
     * @param dir The root directory
     * @param maxDepth Maximum recursion depth
     * @param indent Current indentation level
     * @return Formatted tree structure
     */
    fun getFileTreeString(
        dir: DocumentFile?,
        maxDepth: Int = 3,
        indent: String = "",
    ): String {
        if (dir == null || !dir.isDirectory || maxDepth < 0) return ""
        val sb = StringBuilder()
        sb
            .append(indent)
            .append("📁 ")
            .append(dir.name ?: "ROOT")
            .append("\n")
        try {
            dir.listFiles().sortedBy { it.name }.forEach { file ->
                val icon = if (file.isDirectory) "📁" else "📄"
                sb
                    .append(indent)
                    .append("  ")
                    .append(icon)
                    .append(" ")
                    .append(file.name)
                    .append("\n")
                if (file.isDirectory && maxDepth > 0) {
                    sb.append(getFileTreeString(file, maxDepth - 1, "$indent    "))
                }
            }
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "getFileTreeString failed")
        }
        return sb.toString()
    }

    /**
     * Safely reads text from a file Uri using ContentResolver.
     * @param context The application context
     * @param uri The file Uri
     * @return The file contents, or null if read failed
     */
    fun readTextFile(
        context: Context,
        uri: Uri?,
    ): String? {
        if (uri == null) return null
        return UriFileUtils.openInputStream(context, uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }
    }

    /**
     * Safely writes text to a file Uri using ContentResolver.
     * @param context The application context
     * @param uri The file Uri
     * @param content The text to write
     * @return True if write was successful
     */
    fun writeTextFile(
        context: Context,
        uri: Uri?,
        content: String,
    ): Boolean {
        if (uri == null) return false
        return try {
            UriFileUtils.openOutputStream(context, uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { it.write(content) }
            }
            true
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "writeTextFile failed for $uri")
            false
        }
    }

    /**
     * Gets information about a file as a formatted string.
     * @param documentFile The DocumentFile
     * @param context Optional context for additional details
     * @return Formatted file information
     */
    fun getFileInfo(
        documentFile: DocumentFile?,
        _context: Context? = null,
    ): String {
        if (documentFile == null) return "null"
        return try {
            val sb = StringBuilder()
            sb.append("Name: ").append(documentFile.name).append("\n")
            sb.append("Type: ").append(if (documentFile.isDirectory) "Directory" else "File").append("\n")
            sb.append("Size: ").append(documentFile.length()).append(" bytes\n")
            sb.append("Readable: ").append(documentFile.canRead()).append("\n")
            sb.append("Writable: ").append(documentFile.canWrite()).append("\n")
            sb.append("Last Modified: ").append(documentFile.lastModified()).append("\n")
            sb.append("Uri: ").append(documentFile.uri).append("\n")
            sb.toString()
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "getFileInfo failed")
            "Error: ${e.message}"
        }
    }

    /**
     * Safely deletes a file or directory.
     * For directories, recursively deletes all contents if requested.
     * @param documentFile The DocumentFile to delete
     * @param recursive If true, recursively delete directory contents
     * @return True if deletion was successful
     */
    fun deleteFileOrDirectory(
        documentFile: DocumentFile?,
        recursive: Boolean = false,
    ): Boolean {
        if (documentFile == null) return false
        return try {
            if (documentFile.isDirectory && recursive) {
                documentFile.listFiles().forEach { child ->
                    deleteFileOrDirectory(child, true)
                }
            }
            documentFile.delete()
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "deleteFileOrDirectory failed for ${documentFile.name}")
            false
        }
    }

    /**
     * Checks if a directory is empty.
     * @param dir The DocumentFile directory
     * @return True if the directory exists and is empty
     */
    fun isDirectoryEmpty(dir: DocumentFile?): Boolean {
        if (dir == null || !dir.isDirectory) return true
        return try {
            val files = dir.listFiles()
            files.isEmpty()
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "isDirectoryEmpty check failed")
            true
        }
    }

    /**
     * Counts files in a directory (non-recursive).
     * @param dir The DocumentFile directory
     * @return The number of files, or 0 if directory is empty or invalid
     */
    fun countFiles(dir: DocumentFile?): Int {
        if (dir == null || !dir.isDirectory) return 0
        return try {
            dir.listFiles().size
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "countFiles failed")
            0
        }
    }
}
