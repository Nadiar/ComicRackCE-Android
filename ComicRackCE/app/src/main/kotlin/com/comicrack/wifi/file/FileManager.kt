package com.comicrack.wifi.file

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import java.io.InputStream

/**
 * Interface for file operations used by WiFi sync.
 * Implementation will use SAF (Storage Access Framework) for file access.
 */
interface FileManager {
    /**
     * List all sync files (.cbp and .cbp.xml) in the sync folder.
     * @return List of filenames
     */
    suspend fun listFiles(): List<String>

    /**
     * Read file content.
     * @param filename Name of the file to read
     * @return File content as byte array, or null if file doesn't exist
     */
    suspend fun readFile(filename: String): ByteArray?

    /**
     * Write a file to the sync folder.
     * @param filename Name of the file to write
     * @param size Size of the file in bytes
     * @param inputStream Stream to read file data from
     * @return true if write succeeded
     */
    suspend fun writeFile(
        filename: String,
        size: Long,
        inputStream: InputStream,
    ): Boolean

    /**
     * Delete a file from the sync folder.
     * @param filename Name of the file to delete
     * @return true if delete succeeded
     */
    suspend fun deleteFile(filename: String): Boolean

    /**
     * Check if a file exists in the sync folder.
     * @param filename Name of the file to check
     * @return true if file exists
     */
    suspend fun fileExists(filename: String): Boolean

    /**
     * Get available free space in the sync folder.
     * @return Free space in bytes
     */
    suspend fun getFreeSpace(): Long
}
