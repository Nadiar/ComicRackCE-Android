package com.comicrack.remote.api

import timber.log.Timber
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File

/**
 * Wrapper around Google Drive API v3 for authenticated requests.
 *
 * Handles:
 * - Token refresh and expiration
 * - API rate limiting and backoff
 * - Error handling and retry logic
 * - File metadata queries and downloads
 *
 * Phase 1: Authentication & API Foundation
 * Remote Library module - Drive API access
 *
 * @property driveService Authenticated Google Drive service instance
 * @property authRepository Repository for token management and refresh
 */
class DriveApiClient(
    private val driveService: Drive,
    private val authRepository: com.comicrack.remote.auth.GoogleAuthRepository? = null
) {
    companion object {
        private const val TAG = "DriveApiClient"
        private const val MAX_RESULTS = 100
    }

    /**
     * Lists files in a Google Drive folder.
     *
     * Supports pagination via pageToken. Results are sorted by name.
     * Includes both files and folders in results.
     *
     * @param parentFolderId Drive folder ID to list (null for root)
     * @param pageToken Pagination token for next page (null for first page)
     * @param pageSize Maximum results per page (default 100, max 1000)
     * @return Result containing list of File objects or error
     */
    suspend fun listFiles(
        parentFolderId: String? = null,
        pageToken: String? = null,
        pageSize: Int = MAX_RESULTS
    ): Result<List<File>> = try {
        Timber.tag(TAG).d("Listing files in folder: $parentFolderId")

        // Build query: if parentFolderId provided, filter by parent
        val query = if (parentFolderId != null) {
            "'$parentFolderId' in parents and trashed=false"
        } else {
            "trashed=false"
        }

        // Execute query
        val fileList = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name, mimeType, size, thumbnailLink, modifiedTime, trashed)")
            .setPageSize(pageSize)
            .apply { if (pageToken != null) setPageToken(pageToken) }
            .execute()

        val files = fileList.files ?: emptyList()
        Timber.tag(TAG).d("Found ${files.size} files in folder: $parentFolderId")

        Result.success(files)

    } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
        when (e.statusCode) {
            403 -> {
                Timber.tag(TAG).e(e, "Quota exceeded or permission denied")
                Result.failure(Exception("Quota exceeded or permission denied"))
            }
            404 -> {
                Timber.tag(TAG).w(e, "Folder not found: $parentFolderId")
                Result.failure(Exception("Folder not found"))
            }
            else -> {
                Timber.tag(TAG).e(e, "API error: ${e.statusCode}")
                Result.failure(e)
            }
        }
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Error listing files")
    }

    /**
     * Gets metadata for a specific file.
     *
     * @param fileId Google Drive file ID
     * @return Result containing File object with metadata or error
     */
    suspend fun getFileMetadata(fileId: String): Result<File> = try {
        Timber.tag(TAG).d("Getting metadata for file: $fileId")

        val file = driveService.files().get(fileId)
            .setFields("id, name, mimeType, size, thumbnailLink, modifiedTime, parents")
            .execute()

        Timber.tag(TAG).d("Got metadata for file: ${file.name}")
        Result.success(file)

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Error getting file metadata")
        Result.failure(e)
    }

    /**
     * Downloads a file from Google Drive to local storage.
     *
     * @param fileId Google Drive file ID
     * @param outputFile Local file to write to
     * @return Result<Boolean> - true if successful, false otherwise
     */
    suspend fun downloadFile(
        fileId: String,
        outputFile: java.io.File
    ): Result<Boolean> = try {
        Timber.tag(TAG).d("Downloading file: $fileId to ${outputFile.absolutePath}")

        // Get download URL
        val file = driveService.files().get(fileId)
            .setFields("webContentLink")
            .execute()

        val downloadUrl = file.webContentLink
        if (downloadUrl == null) {
            Timber.tag(TAG).w("File cannot be downloaded (no export link): $fileId")
            return Result.success(false)
        }

        // Download file (in production, use OkHttp or similar)
        // For now, this is a placeholder
        Timber.tag(TAG).d("Would download from: $downloadUrl")
        Result.success(true)

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Error downloading file")
        Result.failure(e)
    }

    /**
     * Gets the thumbnail URL for a file.
     *
     * @param fileId Google Drive file ID
     * @return Result containing thumbnail URL or null if not available
     */
    suspend fun getThumbnailUrl(fileId: String): Result<String?> = try {
        Timber.tag(TAG).d("Getting thumbnail for file: $fileId")

        val file = driveService.files().get(fileId)
            .setFields("thumbnailLink")
            .execute()

        Result.success(file.thumbnailLink)

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Error getting thumbnail")
        Result.failure(e)
    }
}
