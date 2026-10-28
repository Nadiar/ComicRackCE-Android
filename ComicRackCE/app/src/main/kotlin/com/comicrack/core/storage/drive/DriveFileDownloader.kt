package com.comicrack.core.storage.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress state for a file download.
 */
sealed interface DownloadProgress {
    /** Download is starting; total size may be unknown (0). */
    data class Started(val totalBytes: Long) : DownloadProgress

    /** Bytes received so far. */
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress {
        val percent: Int
            get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
    }

    /** Download completed successfully. */
    data class Completed(val file: File) : DownloadProgress

    /** Download failed. */
    data class Failed(val error: Throwable) : DownloadProgress
}

/**
 * Downloads files from Google Drive with progress reporting.
 *
 * Uses [DriveApiClient.openFileStream] and streams to local storage,
 * emitting [DownloadProgress] events via a Flow.
 *
 * Phase 4: Google Drive Storage Provider (SRC-05-007 v2)
 */
@Singleton
class DriveFileDownloader @Inject constructor(
    private val driveApiClient: DriveApiClient,
) {
    companion object {
        private const val TAG = "DriveFileDownloader"
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Download a Drive file to the specified local path.
     *
     * @param fileId Google Drive file ID
     * @param destinationFile Local file to write to
     * @param expectedSize Expected file size in bytes (for progress calculation). Use 0 if unknown.
     * @return Flow emitting [DownloadProgress] events
     */
    fun download(
        fileId: String,
        destinationFile: File,
        expectedSize: Long = 0,
    ): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Started(expectedSize))

        val streamResult = driveApiClient.openFileStream(fileId)
        val inputStream = streamResult.getOrElse { error ->
            Timber.tag(TAG).e(error, "Failed to open stream for $fileId")
            emit(DownloadProgress.Failed(error))
            return@flow
        }

        try {
            destinationFile.parentFile?.mkdirs()

            inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesDownloaded = 0L

                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        emit(DownloadProgress.InProgress(bytesDownloaded, expectedSize))
                    }

                    output.flush()
                }
            }

            Timber.tag(TAG).i("Download complete: ${destinationFile.absolutePath}")
            emit(DownloadProgress.Completed(destinationFile))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Download failed for $fileId")
            destinationFile.delete()
            emit(DownloadProgress.Failed(e))
        }
    }.flowOn(Dispatchers.IO)
}
