package com.comicrack.core.storage.drive

import android.content.Context
import com.comicrack.core.auth.GoogleAuthManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around Google Drive API v3 for authenticated file operations.
 *
 * Handles credential setup, paginated file listing, metadata retrieval,
 * and file downloads. Uses [GoogleAuthManager] for the signed-in account.
 *
 * Phase 4: Google Drive Storage Provider (SRC-05-007 v2)
 */
@Singleton
class DriveApiClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authManager: GoogleAuthManager,
) {
    companion object {
        private const val TAG = "DriveApiClient"
        private const val APP_NAME = "ComicRackCE"
        private const val DEFAULT_PAGE_SIZE = 100

        /** Fields requested for file listings. */
        private const val LIST_FIELDS =
            "nextPageToken, files(id, name, mimeType, size, thumbnailLink, modifiedTime)"

        /** Fields requested for single file metadata. */
        private const val METADATA_FIELDS =
            "id, name, mimeType, size, thumbnailLink, modifiedTime, parents"

        /** Google Drive MIME type for folders. */
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }

    private var driveService: Drive? = null

    /**
     * Get or create an authenticated [Drive] service instance.
     *
     * @return Authenticated Drive service, or null if not signed in
     */
    private fun getDriveService(): Drive? {
        driveService?.let { return it }

        val account = GoogleSignIn.getLastSignedInAccount(context) ?: run {
            Timber.tag(TAG).w("No signed-in Google account")
            return null
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_READONLY),
        )
        credential.selectedAccount = account.account

        val service = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        )
            .setApplicationName(APP_NAME)
            .build()

        driveService = service
        return service
    }

    /**
     * Invalidate the cached Drive service (e.g., after sign-out).
     */
    fun invalidateService() {
        driveService = null
    }

    /**
     * List files in a Google Drive folder with pagination support.
     *
     * @param parentFolderId Drive folder ID (null for root "My Drive")
     * @param pageToken Pagination token for the next page (null for first page)
     * @param pageSize Maximum results per page (default 100, max 1000)
     * @return Result containing a [PagedFileList] with files and optional next page token
     */
    suspend fun listFiles(
        parentFolderId: String? = null,
        pageToken: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Result<PagedFileList> = withContext(Dispatchers.IO) {
        val service = getDriveService()
            ?: return@withContext Result.failure(Exception("Not signed in to Google Drive"))

        try {
            val query = buildQuery(parentFolderId)

            val request = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields(LIST_FIELDS)
                .setPageSize(pageSize)
                .setOrderBy("name")

            if (pageToken != null) {
                request.pageToken = pageToken
            }

            val fileList: FileList = request.execute()
            val files = fileList.files ?: emptyList()

            Timber.tag(TAG).d("Listed ${files.size} items in folder $parentFolderId")

            Result.success(
                PagedFileList(
                    files = files,
                    nextPageToken = fileList.nextPageToken,
                ),
            )
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            Timber.tag(TAG).e(e, "Drive API error ${e.statusCode} listing folder $parentFolderId")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error listing files in folder $parentFolderId")
            Result.failure(e)
        }
    }

    /**
     * Get metadata for a specific file.
     *
     * @param fileId Google Drive file ID
     * @return Result containing the Drive [File] metadata
     */
    suspend fun getFileMetadata(fileId: String): Result<File> = withContext(Dispatchers.IO) {
        val service = getDriveService()
            ?: return@withContext Result.failure(Exception("Not signed in to Google Drive"))

        try {
            val file = service.files().get(fileId)
                .setFields(METADATA_FIELDS)
                .execute()

            Timber.tag(TAG).d("Got metadata for: ${file.name}")
            Result.success(file)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting metadata for file $fileId")
            Result.failure(e)
        }
    }

    /**
     * Open an [InputStream] for downloading a file's content.
     *
     * The caller is responsible for closing the stream.
     *
     * @param fileId Google Drive file ID
     * @return Result containing the file content InputStream
     */
    suspend fun openFileStream(fileId: String): Result<InputStream> = withContext(Dispatchers.IO) {
        val service = getDriveService()
            ?: return@withContext Result.failure(Exception("Not signed in to Google Drive"))

        try {
            val inputStream = service.files().get(fileId)
                .executeMediaAsInputStream()

            Timber.tag(TAG).d("Opened stream for file $fileId")
            Result.success(inputStream)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error opening stream for file $fileId")
            Result.failure(e)
        }
    }

    /**
     * Check if the Drive API is accessible (user is signed in and has scope).
     */
    fun isAccessible(): Boolean = authManager.hasDriveScope()

    private fun buildQuery(parentFolderId: String?): String =
        if (parentFolderId != null) {
            "'$parentFolderId' in parents and trashed=false"
        } else {
            "'root' in parents and trashed=false"
        }
}

/**
 * Paginated result from a Drive file listing.
 *
 * @property files The files in this page
 * @property nextPageToken Token for fetching the next page, or null if this is the last page
 */
data class PagedFileList(
    val files: List<File>,
    val nextPageToken: String?,
)
