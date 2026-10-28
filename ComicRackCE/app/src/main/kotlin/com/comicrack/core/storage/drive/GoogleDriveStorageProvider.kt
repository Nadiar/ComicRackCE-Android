package com.comicrack.core.storage.drive

import com.comicrack.core.storage.provider.StorageFileEntry
import com.comicrack.core.storage.provider.StorageProvider
import com.comicrack.core.storage.provider.StorageType
import com.google.api.services.drive.model.File
import timber.log.Timber
import java.io.InputStream

/**
 * [StorageProvider] implementation for Google Drive via Drive API v3.
 *
 * Uses [DriveApiClient] for all API calls. Supports paginated directory listing,
 * BFS folder traversal, and comic format filtering.
 *
 * Phase 4: Google Drive Storage Provider (SRC-05-007 v2)
 *
 * @property driveApiClient Authenticated Drive API wrapper
 * @property accountEmail User email for display paths
 */
class GoogleDriveStorageProvider(
    private val driveApiClient: DriveApiClient,
    private val accountEmail: String = "",
) : StorageProvider {

    companion object {
        private const val TAG = "GoogleDriveStorageProvider"

        /** Supported comic MIME types (matches LibraryScannerService). */
        private val COMIC_MIME_TYPES = setOf(
            "application/zip",
            "application/x-rar-compressed",
            "application/x-rar",
            "application/pdf",
            "application/x-cbz",
            "application/x-cbr",
            "application/x-cb7",
            "application/x-7z-compressed",
            "application/x-tar",
        )

        /** Supported comic file extensions (matches LibraryScannerService.COMIC_EXTENSIONS). */
        val COMIC_EXTENSIONS = setOf(
            "cbz", "cbr", "cb7", "cbt", "cbp", "zip", "rar", "7z", "tar",
        )
    }

    override val type: StorageType = StorageType.GOOGLE_DRIVE

    /**
     * List files in a Google Drive folder (single level, no recursion).
     *
     * Handles pagination transparently - returns all files in the folder.
     *
     * @param directoryIdentifier Google Drive folder ID
     */
    override suspend fun listFiles(directoryIdentifier: String): Result<List<StorageFileEntry>> {
        val allEntries = mutableListOf<StorageFileEntry>()
        var pageToken: String? = null

        try {
            do {
                val result = driveApiClient.listFiles(
                    parentFolderId = directoryIdentifier,
                    pageToken = pageToken,
                )

                val pagedList = result.getOrElse { return Result.failure(it) }

                pagedList.files.forEach { file ->
                    allEntries.add(file.toStorageFileEntry(directoryIdentifier))
                }

                pageToken = pagedList.nextPageToken
            } while (pageToken != null)

            Timber.tag(TAG).d("Listed ${allEntries.size} entries in folder $directoryIdentifier")
            return Result.success(allEntries)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error listing Drive folder $directoryIdentifier")
            return Result.failure(e)
        }
    }

    override suspend fun getMetadata(fileIdentifier: String): Result<StorageFileEntry> {
        return driveApiClient.getFileMetadata(fileIdentifier).map { file ->
            file.toStorageFileEntry(parentIdentifier = file.parents?.firstOrNull())
        }
    }

    override suspend fun openInputStream(fileIdentifier: String): Result<InputStream> =
        driveApiClient.openFileStream(fileIdentifier)

    override suspend fun isAccessible(): Boolean = driveApiClient.isAccessible()

    override suspend fun resolveDisplayPath(identifier: String): String {
        val prefix = if (accountEmail.isNotEmpty()) "Google Drive ($accountEmail)" else "Google Drive"
        val metadata = driveApiClient.getFileMetadata(identifier).getOrNull()
        return if (metadata != null) {
            "$prefix/${metadata.name}"
        } else {
            "$prefix/$identifier"
        }
    }

    /**
     * Check if a file is a supported comic format by MIME type or extension.
     */
    fun isSupportedComicFormat(mimeType: String?, fileName: String): Boolean {
        if (mimeType != null && mimeType in COMIC_MIME_TYPES) return true
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in COMIC_EXTENSIONS
    }

    /**
     * Convert a Drive [File] to a [StorageFileEntry].
     */
    private fun File.toStorageFileEntry(parentIdentifier: String?): StorageFileEntry {
        val isFolder = mimeType == DriveApiClient.FOLDER_MIME_TYPE
        val providerSpecific = mutableMapOf<String, String>()
        thumbnailLink?.let { providerSpecific["thumbnailUrl"] = it }

        return StorageFileEntry(
            identifier = id,
            displayName = name ?: "Unknown",
            isDirectory = isFolder,
            size = if (isFolder) 0L else (getSize() ?: 0L),
            lastModified = modifiedTime?.value ?: 0L,
            mimeType = mimeType,
            parentIdentifier = parentIdentifier,
            providerSpecific = providerSpecific,
        )
    }
}
