package com.comicrack.core.storage.provider

import java.io.InputStream

/**
 * Abstraction for storage backends that can list, read, and download files.
 *
 * Each storage type (local file system, SAF, Google Drive, future providers) implements
 * this interface so the scanner pipeline can work uniformly regardless of where files live.
 *
 * All operations are suspend functions because remote providers are inherently async.
 */
interface StorageProvider {

    /** The type of storage this provider handles. */
    val type: StorageType

    /**
     * List all files and directories under [directoryIdentifier].
     *
     * @param directoryIdentifier Backend-specific identifier for the directory
     *   (file path, SAF URI, Drive folder ID, etc.)
     * @return Result containing the list of entries, or a failure with error details
     */
    suspend fun listFiles(directoryIdentifier: String): Result<List<StorageFileEntry>>

    /**
     * Get metadata for a single file.
     *
     * @param fileIdentifier Backend-specific file identifier
     * @return Result containing the file's metadata
     */
    suspend fun getMetadata(fileIdentifier: String): Result<StorageFileEntry>

    /**
     * Open an input stream for reading a file's content.
     *
     * For local/SAF providers this is a direct file stream.
     * For remote providers this may trigger a network download.
     *
     * @param fileIdentifier Backend-specific file identifier
     * @return Result containing an InputStream for the file content
     */
    suspend fun openInputStream(fileIdentifier: String): Result<InputStream>

    /**
     * Check whether this provider can currently access its storage.
     *
     * For local storage: always true.
     * For SAF: checks URI permission is still held.
     * For remote: checks authentication and connectivity.
     */
    suspend fun isAccessible(): Boolean

    /**
     * Resolve a provider-specific identifier into a user-friendly display path.
     *
     * @param identifier Backend-specific identifier
     * @return Human-readable path (e.g., "Internal Storage/Comics" or "Google Drive/My Comics")
     */
    suspend fun resolveDisplayPath(identifier: String): String
}
