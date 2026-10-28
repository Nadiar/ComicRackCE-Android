package com.comicrack.core.storage.provider

/**
 * Represents a file or directory entry returned by a [StorageProvider].
 *
 * This is the common data class used across all storage backends (local, SAF, Google Drive, etc.)
 * to describe a discovered file or directory.
 *
 * @property identifier Backend-specific identifier (file path, SAF URI, Drive file ID)
 * @property displayName Human-readable filename (e.g., "Spider-Man #001.cbz")
 * @property isDirectory True if this entry is a directory/folder
 * @property size File size in bytes (0 for directories)
 * @property lastModified Last modification timestamp in milliseconds since epoch
 * @property mimeType MIME type if available (e.g., "application/x-cbz")
 * @property parentIdentifier Identifier of the parent directory (null for root entries)
 * @property providerSpecific Additional provider-specific metadata (e.g., "thumbnailUrl" for Drive)
 */
data class StorageFileEntry(
    val identifier: String,
    val displayName: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val mimeType: String? = null,
    val parentIdentifier: String? = null,
    val providerSpecific: Map<String, String> = emptyMap(),
)
