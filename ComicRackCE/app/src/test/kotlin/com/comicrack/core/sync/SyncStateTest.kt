package com.comicrack.core.sync

import com.comicrack.data.database.entities.SyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SyncState] entity helper methods.
 */
class SyncStateTest {

    @Test
    fun `isSuccessful returns true for SUCCESS status`() {
        val state = SyncState(libraryPathId = 1, lastSyncStatus = SyncState.STATUS_SUCCESS)
        assertTrue(state.isSuccessful())
    }

    @Test
    fun `isSuccessful returns false for FAILED status`() {
        val state = SyncState(libraryPathId = 1, lastSyncStatus = SyncState.STATUS_FAILED)
        assertFalse(state.isSuccessful())
    }

    @Test
    fun `isSyncing returns true for SYNCING status`() {
        val state = SyncState(libraryPathId = 1, lastSyncStatus = SyncState.STATUS_SYNCING)
        assertTrue(state.isSyncing())
    }

    @Test
    fun `isSyncing returns false for PENDING status`() {
        val state = SyncState(libraryPathId = 1, lastSyncStatus = SyncState.STATUS_PENDING)
        assertFalse(state.isSyncing())
    }

    @Test
    fun `hasFailed returns true for FAILED status`() {
        val state = SyncState(libraryPathId = 1, lastSyncStatus = SyncState.STATUS_FAILED)
        assertTrue(state.hasFailed())
    }

    @Test
    fun `neverSynced returns true when lastSyncAt is 0`() {
        val state = SyncState(libraryPathId = 1, lastSyncAt = 0L)
        assertTrue(state.neverSynced())
    }

    @Test
    fun `neverSynced returns false when lastSyncAt is non-zero`() {
        val state = SyncState(libraryPathId = 1, lastSyncAt = System.currentTimeMillis())
        assertFalse(state.neverSynced())
    }

    @Test
    fun `shouldRetry returns true for failed state with error message`() {
        val state = SyncState(
            libraryPathId = 1,
            lastSyncStatus = SyncState.STATUS_FAILED,
            lastErrorMessage = "Network error",
        )
        assertTrue(state.shouldRetry())
    }

    @Test
    fun `shouldRetry returns false for failed state without error message`() {
        val state = SyncState(
            libraryPathId = 1,
            lastSyncStatus = SyncState.STATUS_FAILED,
            lastErrorMessage = null,
        )
        assertFalse(state.shouldRetry())
    }

    @Test
    fun `shouldRetry returns false for success state`() {
        val state = SyncState(
            libraryPathId = 1,
            lastSyncStatus = SyncState.STATUS_SUCCESS,
            lastErrorMessage = "old error",
        )
        assertFalse(state.shouldRetry())
    }

    @Test
    fun `status constants have correct values`() {
        assertTrue(SyncState.STATUS_PENDING == "PENDING")
        assertTrue(SyncState.STATUS_SYNCING == "SYNCING")
        assertTrue(SyncState.STATUS_SUCCESS == "SUCCESS")
        assertTrue(SyncState.STATUS_FAILED == "FAILED")
    }
}
