package com.comicrack.remote.sync

import android.content.Context
import timber.log.Timber
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager background job for synchronizing remote (Google Drive) library.
 *
 * Periodically synchronizes the local database with Google Drive content.
 * Discovers new comics, updates metadata, and removes deleted files.
 *
 * Runs in the background to keep the remote library up-to-date without blocking
 * the UI. Provides progress updates and handles retries on transient failures.
 *
 * Template pattern based on LibraryScanWorker.kt:
 * - Uses @HiltWorker for dependency injection with WorkManager
 * - @AssistedInject constructor with Context and WorkerParameters from WorkManager
 * - Outputs result data with success flag and statistics
 * - Implements retry logic with exponential backoff
 * - Logs progress at each stage
 *
 * **Usage**:
 * ```kotlin
 * val syncRequest = OneTimeWorkRequestBuilder<RemoteLibrarySyncWorker>()
 *     .setInputData(workDataOf("libraryPathId" to pathId))
 *     .build()
 *
 * WorkManager.getInstance(context).enqueueUniqueWork(
 *     "remote-sync-$pathId",
 *     ExistingWorkPolicy.KEEP,
 *     syncRequest
 * )
 * ```
 *
 * **Progress Tracking**:
 * ```kotlin
 * WorkManager.getInstance(context)
 *     .getWorkInfoByIdLiveData(syncRequest.id)
 *     .observe(owner) { workInfo ->
 *         when (workInfo.state) {
 *             WorkInfo.State.RUNNING -> {
 *                 val status = workInfo.progress.getString("status")
 *             }
 *             WorkInfo.State.SUCCEEDED -> {
 *                 val comicsFound = workInfo.outputData.getInt("comicsFound", 0)
 *             }
 *         }
 *     }
 * ```
 *
 * Phase 2: Scanner & Sync Infrastructure
 * Remote Library module - Background synchronization
 */
@HiltWorker
class RemoteLibrarySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val remoteLibraryService: RemoteLibraryService
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RemoteLibrarySyncWorker"
        private const val MAX_RETRIES = 3
        const val KEY_LIBRARY_PATH_ID = "libraryPathId"

        // Progress keys
        const val KEY_STATUS = "status"

        // Output keys
        const val KEY_SUCCESS = "success"
        const val KEY_TOTAL_FOUND = "totalFound"
        const val KEY_COMICS_FOUND = "comicsFound"
        const val KEY_DURATION_MS = "durationMs"
        const val KEY_ERROR_MESSAGE = "errorMessage"
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.tag(TAG).i("Starting remote library sync job")

            // Get input parameters
            val libraryPathId = inputData.getLong(KEY_LIBRARY_PATH_ID, -1L)
            if (libraryPathId == -1L) {
                Timber.tag(TAG).e("Invalid libraryPathId: $libraryPathId")
                return Result.failure(
                    workDataOf(
                        KEY_SUCCESS to false,
                        KEY_ERROR_MESSAGE to "Invalid libraryPathId"
                    )
                )
            }

            Timber.tag(TAG).d("Syncing library path: $libraryPathId")

            // Update progress: syncing
            setProgressAsync(workDataOf(KEY_STATUS to "Syncing..."))

            // Perform the sync
            val syncResult = remoteLibraryService.syncLibraryPath(libraryPathId)

            Timber.tag(TAG).i(
                "Sync completed: success=${syncResult.isSuccessful}, " +
                "totalFiles=${syncResult.totalFilesFound}, " +
                "comics=${syncResult.totalComicsFound}, " +
                "duration=${syncResult.durationMs}ms"
            )

            // Prepare output data
            val outputData = workDataOf(
                KEY_SUCCESS to syncResult.isSuccessful,
                KEY_TOTAL_FOUND to syncResult.totalFilesFound,
                KEY_COMICS_FOUND to syncResult.totalComicsFound,
                KEY_DURATION_MS to syncResult.durationMs
            )

            // Return result based on success
            return if (syncResult.isSuccessful) {
                Timber.tag(TAG).i("Sync succeeded for library path: $libraryPathId")
                Result.success(outputData)
            } else {
                // On failure, decide whether to retry
                if (runAttemptCount < MAX_RETRIES) {
                    Timber.tag(TAG).w("Sync failed, retrying (attempt ${runAttemptCount + 1}/$MAX_RETRIES)")
                    Result.retry()
                } else {
                    Timber.tag(TAG).e("Sync failed after $MAX_RETRIES attempts")
                    val failureData = outputData.putString(
                        KEY_ERROR_MESSAGE,
                        syncResult.errorMessage ?: "Unknown error"
                    )
                    Result.failure(failureData)
                }
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Sync job failed with exception")

            // On exception, retry if we haven't exceeded max attempts
            return if (runAttemptCount < MAX_RETRIES) {
                Timber.tag(TAG).w("Retrying after exception (attempt ${runAttemptCount + 1}/$MAX_RETRIES)")
                Result.retry()
            } else {
                Timber.tag(TAG).e("Sync job permanently failed after $MAX_RETRIES attempts")
                val outputData = workDataOf(
                    KEY_SUCCESS to false,
                    KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")
                )
                Result.failure(outputData)
            }
        }
    }
}

/**
 * Result data class for a sync operation.
 *
 * Returned by RemoteLibraryService.syncLibraryPath() to indicate
 * success/failure and provide statistics about the sync.
 *
 * @property isSuccessful true if sync completed successfully
 * @property totalFilesFound Total files discovered in Drive folder
 * @property totalComicsFound Total comic files identified (after filtering by format)
 * @property durationMs How long the sync took in milliseconds
 * @property errorMessage Error description if sync failed
 */
data class SyncResult(
    val isSuccessful: Boolean,
    val totalFilesFound: Int,
    val totalComicsFound: Int,
    val durationMs: Long,
    val errorMessage: String? = null
)
