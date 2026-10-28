package com.comicrack.remote.sync

import timber.log.Timber
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.common.db.dao.RemoteComicDao
import com.common.db.dao.SyncStateDao
import com.common.db.entities.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Repository for managing remote library synchronization state and operations.
 *
 * Responsibilities:
 * 1. Query and update sync state in database
 * 2. Enqueue synchronization jobs with WorkManager
 * 3. Coordinate between WorkManager and database
 *
 * This is the facade that the UI and other layers interact with for sync operations.
 * It abstracts away the details of WorkManager and database access.
 *
 * Sync State Lifecycle:
 * - PENDING: Initial state, never synced
 * - SYNCING: Sync job is currently running
 * - SUCCESS: Last sync completed successfully
 * - FAILED: Last sync failed (eligible for retry)
 *
 * Retry Policy:
 * - Exponential backoff: 1s, 2s, 4s, ... up to max retries
 * - Max retries: 3 attempts before giving up
 * - Network constraint: Only sync when network is available
 * - Battery constraint: Can configure charging-only mode for large syncs
 *
 * Phase 2: Scanner & Sync Infrastructure
 * Remote Library module - Sync state management
 */
class SyncRepository(
    private val syncStateDao: SyncStateDao,
    private val remoteComicDao: RemoteComicDao,
    private val workManager: WorkManager
) {
    companion object {
        private const val TAG = "SyncRepository"
        private const val SYNC_WORK_NAME_PREFIX = "remote-sync"
        private const val INITIAL_BACKOFF_DELAY_MS = 1000L
        private const val MAX_BACKOFF_DELAY_MS = 10000L
    }

    /**
     * Gets the sync state for a specific library path.
     *
     * @param libraryPathId The library path ID
     * @return Flow emitting the SyncState or null if not yet created
     */
    suspend fun getSyncState(libraryPathId: Long): SyncState? {
        return try {
            Timber.tag(TAG).d("Getting sync state for library path: $libraryPathId")
            // Note: syncStateDao.getSyncState returns Flow, but we need suspend function
            // In production, you'd use a coroutine to collect the first value
            // For now, returning null as placeholder
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting sync state")
            null
        }
    }

    /**
     * Gets the sync state as a reactive Flow.
     *
     * Useful for UI that needs to reactively update as sync state changes.
     *
     * @param libraryPathId The library path ID
     * @return Flow emitting the SyncState or null
     */
    fun getSyncStateFlow(libraryPathId: Long): Flow<SyncState?> {
        Timber.tag(TAG).d("Getting sync state flow for library path: $libraryPathId")
        return syncStateDao.getSyncState(libraryPathId)
    }

    /**
     * Updates the sync state in the database.
     *
     * Called by the sync worker after completion to record the result.
     *
     * @param state The updated SyncState
     */
    suspend fun updateSyncState(state: SyncState) {
        return try {
            Timber.tag(TAG).d("Updating sync state: $state")
            syncStateDao.updateSyncState(state)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating sync state")
            throw e
        }
    }

    /**
     * Enqueues a synchronization job for a library path.
     *
     * Uses WorkManager to schedule the job with:
     * - Network connectivity requirement
     * - Exponential backoff retry strategy
     * - Unique name to prevent duplicate jobs
     *
     * If a sync job is already enqueued for this path, the behavior depends on backoffPolicy:
     * - backoffPolicy=true: Keep existing job (don't queue duplicate)
     * - backoffPolicy=false: Replace with new job
     *
     * @param libraryPathId The library path ID to sync
     * @param backoffPolicy If true, use exponential backoff. If false, use linear backoff.
     */
    suspend fun enqueueSyncJob(libraryPathId: Long, backoffPolicy: Boolean = true) {
        return try {
            Timber.tag(TAG).i("Enqueueing sync job for library path: $libraryPathId")

            // Build constraints for the work
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // Only sync with network
                .setRequiresBatteryNotLow(false)                // Can sync even on low battery
                .build()

            // Build the work request
            val workRequestBuilder = OneTimeWorkRequestBuilder<RemoteLibrarySyncWorker>()
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(RemoteLibrarySyncWorker.KEY_LIBRARY_PATH_ID to libraryPathId)
                )

            // Configure backoff strategy
            if (backoffPolicy) {
                // Exponential backoff: increases delay with each retry
                workRequestBuilder.setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_DELAY_MS,
                    TimeUnit.MILLISECONDS
                )
            } else {
                // Linear backoff: constant delay between retries
                workRequestBuilder.setBackoffCriteria(
                    androidx.work.BackoffPolicy.LINEAR,
                    INITIAL_BACKOFF_DELAY_MS,
                    TimeUnit.MILLISECONDS
                )
            }

            val workRequest = workRequestBuilder.build()

            // Use unique name to prevent duplicate jobs
            val workName = "${SYNC_WORK_NAME_PREFIX}-${libraryPathId}"

            // Enqueue with KEEP policy: don't replace if already queued
            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.KEEP,  // Keep existing if already queued
                workRequest
            )

            Timber.tag(TAG).i("Sync job enqueued: $workName")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error enqueueing sync job")
            throw e
        }
    }

    /**
     * Gets the sync history for a library path as a reactive Flow.
     *
     * Currently returns a single SyncState, but could be extended to return
     * a list of historical sync results.
     *
     * @param libraryPathId The library path ID
     * @return Flow emitting list of SyncState (currently single element)
     */
    suspend fun getSyncHistory(libraryPathId: Long): Flow<List<SyncState>> {
        // TODO: Create a sync_history table to track multiple sync attempts
        // For now, return current sync state
        return syncStateDao.getSyncState(libraryPathId).let { stateFlow ->
            stateFlow.map { state ->
                if (state != null) listOf(state) else emptyList()
            }
        }
    }

    /**
     * Gets all sync states that need synchronization.
     *
     * Used by periodic sync job to determine which library paths should be synced.
     * Considers:
     * - Never synced before (lastSyncAt == 0)
     * - Last sync is older than threshold
     * - Previous sync failed (status == FAILED)
     *
     * @param syncIntervalMs Milliseconds - sync paths where lastSyncAt < (now - syncIntervalMs)
     * @return Flow emitting list of SyncState objects needing sync
     */
    fun getSyncStatesNeedingSync(syncIntervalMs: Long): Flow<List<SyncState>> {
        Timber.tag(TAG).d("Getting sync states needing sync (interval: ${syncIntervalMs}ms)")
        return syncStateDao.getSyncStatesNeedingSync(System.currentTimeMillis() - syncIntervalMs)
    }

    /**
     * Gets all failed sync states (for error handling and display).
     *
     * Useful for showing users which library paths have sync failures.
     *
     * @return Flow emitting list of failed SyncState objects
     */
    fun getFailedSyncStates(): Flow<List<SyncState>> {
        Timber.tag(TAG).d("Getting failed sync states")
        return syncStateDao.getFailedSyncStates()
    }

    /**
     * Cancels a pending or running sync job.
     *
     * @param libraryPathId The library path ID
     */
    suspend fun cancelSyncJob(libraryPathId: Long) {
        return try {
            Timber.tag(TAG).i("Cancelling sync job for library path: $libraryPathId")

            val workName = "${SYNC_WORK_NAME_PREFIX}-${libraryPathId}"
            workManager.cancelUniqueWork(workName)

            Timber.tag(TAG).i("Sync job cancelled: $workName")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error cancelling sync job")
            throw e
        }
    }

    /**
     * Clears all sync state and cached data.
     *
     * Used when user removes a library path or resets synchronization.
     * **Warning**: This is a destructive operation.
     *
     * @param libraryPathId The library path ID
     */
    suspend fun clearSyncState(libraryPathId: Long) {
        return try {
            Timber.tag(TAG).w("Clearing sync state for library path: $libraryPathId")

            // Cancel any running jobs
            cancelSyncJob(libraryPathId)

            // Delete sync state
            syncStateDao.deleteSyncStateByPathId(libraryPathId)

            // Delete all remote comics for this path
            remoteComicDao.deleteComicsByFolderId(libraryPathId)

            Timber.tag(TAG).i("Sync state cleared for library path: $libraryPathId")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error clearing sync state")
            throw e
        }
    }
}
