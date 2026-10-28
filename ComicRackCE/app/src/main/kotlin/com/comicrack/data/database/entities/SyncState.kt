package com.comicrack.data.database.entities

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for tracking synchronization state of remote library paths.
 *
 * Stores the last synchronization timestamp, status, error messages, and summary
 * statistics for each Google Drive library path. Used to track sync history and
 * determine when the next sync should occur.
 *
 * Phase 1: Authentication & API Foundation
 * Remote Library module - Sync state tracking
 *
 * @property libraryPathId Primary key - Foreign key to LibraryPath being synced
 * @property lastSyncAt Timestamp of last sync attempt (0 if never synced)
 * @property lastSyncStatus Enum status (PENDING, SYNCING, SUCCESS, FAILED)
 * @property lastErrorMessage Error message if last sync failed (null if successful)
 * @property totalFilesFound Total files discovered in Drive folder
 * @property totalComicsFound Total comic files identified (filtered by MIME type)
 * @property durationMs How long the sync took in milliseconds
 */
@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey
    val libraryPathId: Long,
    @ColumnInfo(defaultValue = "0")
    val lastSyncAt: Long = 0L,
    @ColumnInfo(defaultValue = "'PENDING'")
    val lastSyncStatus: String = "PENDING", // PENDING, SYNCING, SUCCESS, FAILED
    val lastErrorMessage: String? = null,
    @ColumnInfo(defaultValue = "0")
    val totalFilesFound: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val totalComicsFound: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val durationMs: Long = 0L,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCING = "SYNCING"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
    }

    /**
     * Check if the last sync was successful.
     */
    fun isSuccessful(): Boolean = lastSyncStatus == STATUS_SUCCESS

    /**
     * Check if a sync is currently in progress.
     */
    fun isSyncing(): Boolean = lastSyncStatus == STATUS_SYNCING

    /**
     * Check if the last sync failed.
     */
    fun hasFailed(): Boolean = lastSyncStatus == STATUS_FAILED

    /**
     * Check if this path has never been synced.
     */
    fun neverSynced(): Boolean = lastSyncAt == 0L

    /**
     * Check if sync should be retried (failed status and has an error).
     */
    fun shouldRetry(): Boolean = hasFailed() && lastErrorMessage != null

    override fun toString(): String =
        "SyncState(libraryPathId=$libraryPathId, status=$lastSyncStatus, " +
            "comicsFound=$totalComicsFound, durationMs=$durationMs)"
}
