package com.comicrack.data.database.entities

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Room entity for library path storage.
 *
 * Stores SAF URIs or local directory paths that have been added to the library.
 * Each library path can contain multiple comic files discovered during scan.
 *
 * Phase 3: Task 3.1 â€” Room Database Schema
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 *
 * @property id Auto-generated primary key
 * @property path The path to the library directory (SAF URI or local path)
 * @property displayName Human-readable name (e.g., "Comics", "My Books")
 * @property createdAt Timestamp when the path was added (milliseconds since epoch)
 * @property lastScannedAt Timestamp of the last successful library scan
 * @property comicCount Number of comic files discovered in this library path
 * @property isActive Whether this library path is active and should be scanned
 */
@Entity(tableName = "library_paths")
data class LibraryPath(
    /**
     * The path to the library directory.
     * Can be:
     *   - SAF URI: "content://com.android.externalstorage.documents/tree/primary%3AComics"
     *   - Local path: "/data/data/com.comicrack.viewer/cache/library" (app-specific, safe)
     */
    /**
     * The path to the library directory.
     * Can be:
     *   - SAF URI: "content://com.android.externalstorage.documents/tree/primary%3AComics"
     *   - Local path: "/data/data/com.comicrack.viewer/cache/library" (app-specific, safe)
     */
    @JvmField
    var path: String = "",
    /**
     * Display name for the library path (e.g., "Comics", "My Books", "External Storage").
     */
    @JvmField
    var displayName: String = "",
    /**
     * Timestamp when the path was added to the library.
     */
    @JvmField
    var createdAt: Long = System.currentTimeMillis(),
    /**
     * Timestamp of the last successful library scan.
     */
    @JvmField
    var lastScannedAt: Long = 0,
    /**
     * Number of comic files discovered in this library path.
     * Updated after each scan.
     */
    @JvmField
    var comicCount: Int = 0,
    /**
     * Whether this library path is active and should be scanned.
     * Set to false to temporarily disable scanning without deleting the record.
     */
    @JvmField
    var isActive: Boolean = true,
    /**
     * Storage backend type for this library path.
     * Values: "SAF" (default/legacy), "LOCAL", "GOOGLE_DRIVE".
     * Determines which StorageProvider to use for scanning.
     */
    @ColumnInfo(defaultValue = "'SAF'")
    @JvmField
    var storageType: String = "SAF",
    /**
     * JSON blob containing provider-specific configuration.
     * - For GOOGLE_DRIVE: {"folderId": "...", "accountEmail": "..."}
     * - For SAF/LOCAL: null (path field is sufficient)
     */
    @JvmField
    var providerConfig: String? = null,
    /**
     * How often (in minutes) to background-sync this library path.
     * Only relevant for remote storage types. Default: 60 minutes.
     */
    @ColumnInfo(defaultValue = "60")
    @JvmField
    var syncIntervalMinutes: Int = 60,
    /**
     * Whether this library path requires authentication before scanning.
     * True for cloud providers (Google Drive, etc.), false for local/SAF.
     */
    @ColumnInfo(defaultValue = "0")
    @JvmField
    var requiresAuth: Boolean = false,
    /**
     * Auto-generated primary key
     */
    @PrimaryKey(autoGenerate = true)
    @JvmField
    var id: Long = 0,
) {
    @Ignore
    constructor(path: String, displayName: String) : this(
        path = path,
        displayName = displayName,
        createdAt = System.currentTimeMillis(),
        lastScannedAt = 0,
        comicCount = 0,
        isActive = true,
        storageType = "SAF",
        providerConfig = null,
        syncIntervalMinutes = 60,
        requiresAuth = false,
        id = 0,
    )

    override fun toString(): String =
        "LibraryPath(id=$id, path='$path', displayName='$displayName', storageType='$storageType', comicCount=$comicCount, isActive=$isActive)"
}
