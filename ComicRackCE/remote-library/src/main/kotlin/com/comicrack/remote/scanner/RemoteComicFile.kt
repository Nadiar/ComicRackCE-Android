package com.comicrack.remote.scanner

/**
 * Data class representing metadata of a remote (Google Drive) comic file.
 *
 * Captured during the Google Drive folder scanning process. Contains all the
 * information needed to display the comic in the UI or download it later.
 *
 * This is an immutable value object - used only for transfer, not stored directly
 * in the database. RemoteComic entity is the persistent database representation.
 *
 * Phase 2: Scanner & Sync Infrastructure
 * Remote Library module - Remote comic file metadata
 *
 * @property driveFileId Google Drive file ID (unique identifier on Drive)
 * @property name Human-readable filename (e.g., "Amazing Spider-Man #1.cbz")
 * @property parentFolderId Google Drive folder ID where this file is located
 * @property mimeType MIME type indicating file format (application/zip for CBR/CBZ, application/pdf, etc.)
 * @property sizeBytes File size in bytes (used for cache management and transfer estimation)
 * @property thumbnailUrl Google Drive thumbnail URL (may be temporary/expiring)
 * @property modifiedTime Timestamp when file was last modified on Google Drive (milliseconds)
 */
data class RemoteComicFile(
    val driveFileId: String,
    val name: String,
    val parentFolderId: String,
    val mimeType: String,
    val sizeBytes: Long,
    val thumbnailUrl: String? = null,
    val modifiedTime: Long = System.currentTimeMillis()
) {
    override fun toString(): String =
        "RemoteComicFile(name='$name', driveFileId='$driveFileId', mimeType='$mimeType', sizeBytes=$sizeBytes)"
}
