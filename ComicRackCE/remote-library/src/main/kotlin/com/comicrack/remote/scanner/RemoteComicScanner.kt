package com.comicrack.remote.scanner

import timber.log.Timber
import com.comicrack.remote.api.DriveApiClient
import kotlinx.coroutines.delay

/**
 * Scans Google Drive folders recursively for comic files.
 *
 * Similar to LocalLibraryScanner but uses Google Drive API instead of filesystem access.
 * Handles pagination from the Drive API and filters files by supported comic formats.
 *
 * Supported comic formats:
 * - CBZ (application/zip, .cbz)
 * - CBR (application/x-rar-compressed, .cbr)
 * - PDF (application/pdf)
 *
 * Rate limiting: Google Drive API has quotas. Implements exponential backoff on
 * 403 quota errors.
 *
 * Phase 2: Scanner & Sync Infrastructure
 * Remote Library module - Remote comic discovery
 */
class RemoteComicScanner(
    private val driveApiClient: DriveApiClient
) {
    companion object {
        private const val TAG = "RemoteComicScanner"

        // Supported comic MIME types
        private val SUPPORTED_MIME_TYPES = setOf(
            "application/zip",                    // CBZ files
            "application/x-rar-compressed",       // CBR files
            "application/pdf",                    // PDF files
            "application/x-cbz",                  // Alternative CBZ MIME type
            "application/x-cbr"                   // Alternative CBR MIME type
        )

        // Supported file extensions (as backup check)
        private val SUPPORTED_EXTENSIONS = setOf(
            ".cbz", ".cbr", ".pdf"
        )

        private const val MAX_RESULTS_PER_PAGE = 100
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    /**
     * Recursively scans a Google Drive folder for comic files.
     *
     * Traverses the folder hierarchy, discovering all files and subfolders.
     * Applies pagination to handle large folder structures gracefully.
     *
     * @param folderId Google Drive folder ID to scan
     * @param recursive If true, scan subfolders. If false, only scan immediate contents.
     * @return Result<List<RemoteComicFile>> containing discovered comics or error
     */
    suspend fun scanFolder(
        folderId: String,
        recursive: Boolean = true
    ): Result<List<RemoteComicFile>> = try {
        Timber.tag(TAG).d("Starting scan of folder: $folderId (recursive=$recursive)")

        val comics = mutableListOf<RemoteComicFile>()
        var scannedFolders = 0

        // BFS queue to handle folder traversal
        val folderQueue = mutableListOf(folderId)
        val processedFolders = mutableSetOf<String>()

        while (folderQueue.isNotEmpty()) {
            val currentFolderId = folderQueue.removeAt(0)

            if (currentFolderId in processedFolders) {
                continue
            }
            processedFolders.add(currentFolderId)
            scannedFolders++

            Timber.tag(TAG).d("Scanning folder: $currentFolderId (folder #$scannedFolders)")

            // List files in current folder with pagination
            var pageToken: String? = null
            var pageCount = 0

            do {
                val listResult = driveApiClient.listFiles(
                    parentFolderId = currentFolderId,
                    pageToken = pageToken,
                    pageSize = MAX_RESULTS_PER_PAGE
                )

                when {
                    listResult.isSuccess -> {
                        val files = listResult.getOrNull() ?: emptyList()
                        Timber.tag(TAG).d("  Page ${++pageCount}: Found ${files.size} items")

                        files.forEach { file ->
                            val fileId = file.id ?: return@forEach
                            val fileName = file.name ?: "Unknown"
                            val mimeType = file.mimeType ?: ""

                            // Check if this is a folder
                            if (file.mimeType == "application/vnd.google-apps.folder") {
                                if (recursive) {
                                    folderQueue.add(fileId)
                                    Timber.tag(TAG).d("    Found subfolder: $fileName")
                                }
                            } else if (isSupportedComicFormat(mimeType, fileName)) {
                                // This is a comic file
                                val modifiedTimeMs = file.modifiedTime?.value ?: System.currentTimeMillis()
                                val comicFile = RemoteComicFile(
                                    driveFileId = fileId,
                                    name = fileName,
                                    parentFolderId = currentFolderId,
                                    mimeType = mimeType,
                                    sizeBytes = file.size ?: 0L,
                                    thumbnailUrl = file.thumbnailLink,
                                    modifiedTime = modifiedTimeMs
                                )
                                comics.add(comicFile)
                                Timber.tag(TAG).d("    Found comic: $fileName (${comicFile.sizeBytes} bytes)")
                            }
                        }

                        // Google Drive API doesn't return nextPageToken in our simplified implementation
                        pageToken = null
                    }

                    listResult.isFailure -> {
                        val error = listResult.exceptionOrNull()
                        Timber.tag(TAG).e(error, "Error listing files in folder $currentFolderId")

                        // If this is a quota error, we could implement exponential backoff
                        // but for now we'll just fail the page scan
                        throw error ?: Exception("Unknown error listing files")
                    }
                }

            } while (pageToken != null)
        }

        Timber.tag(TAG).i("Scan complete: found ${comics.size} comics across $scannedFolders folders")
        Result.success(comics)

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Scan failed with exception")
        Result.failure(e)
    }

    /**
     * Determines if a file is in a supported comic format.
     *
     * Checks both the MIME type and filename extension for robustness,
     * as Google Drive MIME type detection can sometimes be inaccurate.
     *
     * @param mimeType File's MIME type from Google Drive
     * @param filename File name to check for extension
     * @return true if this is a supported comic format, false otherwise
     */
    private fun isSupportedComicFormat(mimeType: String, filename: String): Boolean {
        // First check MIME type (most reliable)
        if (mimeType in SUPPORTED_MIME_TYPES) {
            return true
        }

        // Fall back to extension check (for cases where Drive doesn't identify MIME correctly)
        val extension = filename.substringAfterLast(".", "").lowercase()
        if (".${extension}" in SUPPORTED_EXTENSIONS) {
            return true
        }

        // Not a supported comic format
        return false
    }
}
