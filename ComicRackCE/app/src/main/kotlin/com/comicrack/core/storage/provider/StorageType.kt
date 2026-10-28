package com.comicrack.core.storage.provider

/**
 * Identifies the storage backend for a library path.
 *
 * Each type maps to a specific [StorageProvider] implementation:
 * - [LOCAL] -> [LocalFileStorageProvider] (java.io.File)
 * - [SAF] -> [SAFStorageProvider] (SAFFileSystem / Okio)
 * - [GOOGLE_DRIVE] -> GoogleDriveStorageProvider (Drive API v3)
 */
enum class StorageType(val value: String) {
    /** Local file system (app-specific directories, direct file paths). */
    LOCAL("LOCAL"),

    /** Android Storage Access Framework (content:// URIs). */
    SAF("SAF"),

    /** Google Drive cloud storage via Drive API v3. */
    GOOGLE_DRIVE("GOOGLE_DRIVE"),
    ;

    companion object {
        /** Parse a stored string back to [StorageType], defaulting to [SAF] for legacy paths. */
        fun fromValue(value: String): StorageType =
            entries.firstOrNull { it.value == value } ?: SAF
    }
}
