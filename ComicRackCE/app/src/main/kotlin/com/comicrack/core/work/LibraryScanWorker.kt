package com.comicrack.core.work

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.comicrack.core.scanner.LibraryService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import timber.log.Timber

/**
 * WorkManager background job for library scanning with metadata loading.
 *
 * Triggers the complete library scan (file discovery + metadata parsing) as a background task.
 * Updates progress via WorkManager and provides result summary on completion.
 *
 * Created by HiltWorkerFactory which injects dependencies from the Hilt component.
 *
 * **Usage**:
 * ```kotlin
 * WorkManager.getInstance(context).enqueueUniqueWork(
 *     "library-scan",
 *     ExistingWorkPolicy.KEEP,  // Don't queue if already running
 *     OneTimeWorkRequestBuilder<LibraryScanWorker>().build()
 * )
 * ```
 *
 * **Progress Tracking**:
 * Observe via WorkInfo:
 * ```kotlin
 * WorkManager.getInstance(context)
 *     .getWorkInfoByIdLiveData(workRequest.id)
 *     .observe(owner) { workInfo ->
 *         when (workInfo.state) {
 *             WorkInfo.State.RUNNING -> updateProgressUI(workInfo.progress)
 *             WorkInfo.State.SUCCEEDED -> handleResults(workInfo.outputData)
 *             WorkInfo.State.FAILED -> handleError()
 *         }
 *     }
 * ```
 *
 * Phase 4, Task 4.7: Dependency Injection
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@HiltWorker
class LibraryScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val libraryService: LibraryService,
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "LibraryScanWorker"

        // Retry strategy constants
        private const val MAX_RETRY_ATTEMPTS = 3

        // Progress keys for WorkInfo
        const val KEY_TOTAL_FOUND = "totalFound"
        const val KEY_METADATA_LOADED = "metadataLoaded"
        const val KEY_ERROR_COUNT = "errorCount"
        const val KEY_DURATION_MS = "durationMs"
        const val KEY_ERROR_MESSAGE = "errorMessage"

        // Output keys for completed job
        const val KEY_SUCCESS = "success"
        const val KEY_SUCCESS_PERCENTAGE = "successPercentage"
        const val KEY_DURATION_STRING = "durationString"
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.tag(TAG).i("Starting library scan job")
            setProgressAsync(workDataOf("status" to "Initializing scan..."))

            // Phase 4: Targeted scan support
            val pathId = inputData.getLong("pathId", -1L)
            val forced = inputData.getBoolean("forced", false)

            // LibraryService is injected by Hilt
            // Run the scan
            val result =
                if (pathId != -1L) {
                    Timber.tag(TAG).i("Running targeted refresh (forced=$forced) for path ID: $pathId")
                    libraryService.refreshSpecificPath(pathId, forced)
                } else {
                    Timber.tag(TAG).i("Running global refresh (forced=$forced)")
                    libraryService.refreshLibrary(forced)
                }

            if (result.isSuccessful) {
                Timber.tag(TAG).i(
                    "Scan completed: ${result.totalFound} found, " +
                        "${result.metadataLoaded} with metadata",
                )

                // Return success with statistics
                val outputData =
                    workDataOf(
                        KEY_SUCCESS to true,
                        KEY_TOTAL_FOUND to result.totalFound,
                        KEY_METADATA_LOADED to result.metadataLoaded,
                        KEY_ERROR_COUNT to result.errorCount,
                        KEY_DURATION_MS to result.duration,
                        KEY_SUCCESS_PERCENTAGE to result.successPercentage,
                        KEY_DURATION_STRING to result.durationString,
                    )

                return Result.success(outputData)
            } else {
                Timber.tag(TAG).w("Scan completed with errors: ${result.errorCount}")

                val errorMessage = result.errors.joinToString("; ")
                val outputData =
                    workDataOf(
                        KEY_SUCCESS to false,
                        KEY_TOTAL_FOUND to result.totalFound,
                        KEY_METADATA_LOADED to result.metadataLoaded,
                        KEY_ERROR_COUNT to result.errorCount,
                        KEY_ERROR_MESSAGE to errorMessage,
                        KEY_DURATION_MS to result.duration,
                    )

                // Retry on transient failures
                return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Timber.tag(TAG).i("Retrying scan (attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS)")
                    Result.retry()
                } else {
                    Timber.tag(TAG).e("Scan failed after $MAX_RETRY_ATTEMPTS attempts")
                    Result.failure(outputData)
                }
            }
        } catch (exception: Exception) {
            Timber.tag(TAG).e(exception, "Scan job failed with exception")

            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Timber.tag(TAG).i("Retrying after exception (attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS)")
                Result.retry()
            } else {
                Timber.tag(TAG).e("Scan job permanently failed after $MAX_RETRY_ATTEMPTS attempts")
                val outputData =
                    workDataOf(
                        KEY_SUCCESS to false,
                        KEY_ERROR_COUNT to 1,
                        KEY_ERROR_MESSAGE to (exception.message ?: "Unknown error"),
                    )
                Result.failure(outputData)
            }
        }
    }
}
