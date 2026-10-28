package com.comicrack.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.comicrack.core.sync.RemoteSyncService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker for background Google Drive sync.
 *
 * Triggers [RemoteSyncService.syncAllRemotePaths] on a configurable interval
 * (default 60 minutes). Respects each path's `syncIntervalMinutes`.
 *
 * Phase 5: Remote Sync and Caching (SRC-05-007 v2)
 */
@HiltWorker
class RemoteSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncService: RemoteSyncService,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RemoteSyncWorker"
        const val WORK_NAME = "remote-library-sync"
        private const val DEFAULT_INTERVAL_MINUTES = 60L
        private const val MAX_RETRY_ATTEMPTS = 3

        // Output keys
        const val KEY_PATHS_SYNCED = "pathsSynced"
        const val KEY_TOTAL_COMICS = "totalComics"
        const val KEY_NEW_COMICS = "newComics"

        /**
         * Schedule periodic sync via WorkManager.
         *
         * @param context Application context
         * @param intervalMinutes Sync interval in minutes
         */
        fun schedule(context: Context, intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES) {
            val request = PeriodicWorkRequestBuilder<RemoteSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )

            Timber.tag(TAG).i("Scheduled remote sync every $intervalMinutes minutes")
        }

        /**
         * Cancel scheduled periodic sync.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.tag(TAG).i("Cancelled remote sync schedule")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.tag(TAG).i("Starting remote library sync")

            val results = syncService.syncAllRemotePaths()

            val totalComics = results.sumOf { it.totalComicsFound }
            val newComics = results.sumOf { it.newComicsAdded }
            val hasErrors = results.any { !it.isSuccessful }

            Timber.tag(TAG).i(
                "Sync complete: ${results.size} paths, $totalComics comics, $newComics new",
            )

            val outputData = workDataOf(
                KEY_PATHS_SYNCED to results.size,
                KEY_TOTAL_COMICS to totalComics,
                KEY_NEW_COMICS to newComics,
            )

            if (hasErrors && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Timber.tag(TAG).w("Some syncs failed, retrying (attempt ${runAttemptCount + 1})")
                Result.retry()
            } else {
                Result.success(outputData)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Remote sync failed")

            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf("error" to (e.message ?: "Unknown error")),
                )
            }
        }
    }
}
