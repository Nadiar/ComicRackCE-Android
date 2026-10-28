package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.comicrack.data.database.entities.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for SyncState entity.
 *
 * Manages synchronization state tracking for remote library paths.
 * Stores last sync timestamp, status, and error information.
 *
 * Phase 1: Authentication & API Foundation
 * Remote Library module - Sync state persistence
 */
@Dao
interface SyncStateDao {
    /**
     * Insert or replace a sync state record.
     *
     * @param state The SyncState to insert
     * @return The row ID (libraryPathId)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncState(state: SyncState): Long

    /**
     * Update an existing sync state record.
     * Called during and after synchronization.
     *
     * @param state The SyncState with updated fields
     */
    @Update
    suspend fun updateSyncState(state: SyncState)

    /**
     * Delete a sync state record.
     *
     * @param state The SyncState to delete
     */
    @Delete
    suspend fun deleteSyncState(state: SyncState)

    /**
     * Delete sync state for a specific library path.
     *
     * @param libraryPathId The library path ID
     */
    @Query("DELETE FROM sync_state WHERE libraryPathId = :libraryPathId")
    suspend fun deleteSyncStateByPathId(libraryPathId: Long)

    /**
     * Get the sync state for a specific library path.
     * Reactive Flow for observing changes.
     *
     * @param libraryPathId The library path ID
     * @return Flow emitting the SyncState or null if not found
     */
    @Query("SELECT * FROM sync_state WHERE libraryPathId = :libraryPathId")
    fun getSyncState(libraryPathId: Long): Flow<SyncState?>

    /**
     * Get all sync states.
     * Reactive Flow for observing changes.
     *
     * @return Flow emitting list of all SyncState objects
     */
    @Query("SELECT * FROM sync_state")
    fun getAllSyncStates(): Flow<List<SyncState>>

    /**
     * Get sync states for paths that need to be synced (last sync older than threshold).
     * Used to determine which paths need attention.
     *
     * @param thresholdMs Milliseconds - sync paths where lastSyncAt < (now - thresholdMs)
     * @return Flow emitting list of SyncState objects needing sync
     */
    @Query("SELECT * FROM sync_state WHERE (lastSyncAt = 0 OR lastSyncAt < :thresholdMs)")
    fun getSyncStatesNeedingSync(thresholdMs: Long): Flow<List<SyncState>>

    /**
     * Get failed sync states (for error handling/retry).
     * Reactive Flow for observing changes.
     *
     * @return Flow emitting list of SyncState objects with FAILED status
     */
    @Query("SELECT * FROM sync_state WHERE lastSyncStatus = 'FAILED'")
    fun getFailedSyncStates(): Flow<List<SyncState>>

    /**
     * Get the last sync time for a library path.
     *
     * @param libraryPathId The library path ID
     * @return The lastSyncAt timestamp, or 0 if never synced
     */
    @Query("SELECT lastSyncAt FROM sync_state WHERE libraryPathId = :libraryPathId")
    suspend fun getLastSyncTime(libraryPathId: Long): Long

    /**
     * Get the sync status for a library path.
     *
     * @param libraryPathId The library path ID
     * @return The status string (PENDING, SYNCING, SUCCESS, FAILED), or null if not found
     */
    @Query("SELECT lastSyncStatus FROM sync_state WHERE libraryPathId = :libraryPathId")
    suspend fun getSyncStatus(libraryPathId: Long): String?

    /**
     * Update sync status to SYNCING.
     * Called when sync job starts.
     *
     * @param libraryPathId The library path ID
     * @param timestamp Current time in milliseconds
     */
    @Query("UPDATE sync_state SET lastSyncStatus = 'SYNCING' WHERE libraryPathId = :libraryPathId")
    suspend fun setSyncStatusSyncing(libraryPathId: Long)

    /**
     * Update sync status to SUCCESS with stats.
     * Called when sync completes successfully.
     *
     * @param libraryPathId The library path ID
     * @param timestamp Current time in milliseconds
     * @param totalFilesFound Total files discovered
     * @param totalComicsFound Total comics identified
     * @param durationMs Sync duration in milliseconds
     */
    @Query(
        """
        UPDATE sync_state
        SET lastSyncStatus = 'SUCCESS',
            lastSyncAt = :timestamp,
            totalFilesFound = :totalFilesFound,
            totalComicsFound = :totalComicsFound,
            durationMs = :durationMs,
            lastErrorMessage = NULL
        WHERE libraryPathId = :libraryPathId
    """,
    )
    suspend fun setSyncStatusSuccess(
        libraryPathId: Long,
        timestamp: Long = System.currentTimeMillis(),
        totalFilesFound: Int,
        totalComicsFound: Int,
        durationMs: Long,
    )

    /**
     * Update sync status to FAILED with error message.
     * Called when sync fails.
     *
     * @param libraryPathId The library path ID
     * @param errorMessage Error description
     * @param durationMs Sync duration in milliseconds
     */
    @Query(
        """
        UPDATE sync_state
        SET lastSyncStatus = 'FAILED',
            lastErrorMessage = :errorMessage,
            durationMs = :durationMs
        WHERE libraryPathId = :libraryPathId
    """,
    )
    suspend fun setSyncStatusFailed(
        libraryPathId: Long,
        errorMessage: String,
        durationMs: Long,
    )

    /**
     * Delete all sync states.
     * Used for testing and cleanup.
     */
    @Query("DELETE FROM sync_state")
    suspend fun deleteAllSyncStates()
}
