package com.comicrack.wifi.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for SyncCheckWorker - verifies WorkManager scheduling logic.
 *
 * Note: Actual WorkManager operations require Android instrumentation tests.
 * These unit tests focus on scheduling configuration and constraints.
 */
class SyncCheckWorkerTest {
    // ==========================================================================
    // Worker configuration tests
    // ==========================================================================

    @Test
    fun `default repeat interval is 15 minutes`() {
        assertEquals(15, SyncWorkerConfig.REPEAT_INTERVAL_MINUTES)
    }

    @Test
    fun `default flex interval is 5 minutes`() {
        assertEquals(5, SyncWorkerConfig.FLEX_INTERVAL_MINUTES)
    }

    @Test
    fun `worker tag is correct`() {
        assertEquals("wifi_sync_check", SyncWorkerConfig.WORKER_TAG)
    }

    @Test
    fun `unique work name is correct`() {
        assertEquals("wifi_sync_periodic", SyncWorkerConfig.UNIQUE_WORK_NAME)
    }

    // ==========================================================================
    // Constraint checks
    // ==========================================================================

    @Test
    fun `shouldRunSync returns true when all conditions met`() {
        val result =
            SyncWorkerLogic.shouldRunSync(
                wifiSyncEnabled = true,
                isOnWifi = true,
                isOnHomeNetwork = true,
                hasSyncFolder = true,
            )
        assertTrue(result)
    }

    @Test
    fun `shouldRunSync returns false when sync disabled`() {
        val result =
            SyncWorkerLogic.shouldRunSync(
                wifiSyncEnabled = false,
                isOnWifi = true,
                isOnHomeNetwork = true,
                hasSyncFolder = true,
            )
        assertFalse(result)
    }

    @Test
    fun `shouldRunSync returns false when not on wifi`() {
        val result =
            SyncWorkerLogic.shouldRunSync(
                wifiSyncEnabled = true,
                isOnWifi = false,
                isOnHomeNetwork = false,
                hasSyncFolder = true,
            )
        assertFalse(result)
    }

    @Test
    fun `shouldRunSync returns false when not on home network`() {
        val result =
            SyncWorkerLogic.shouldRunSync(
                wifiSyncEnabled = true,
                isOnWifi = true,
                isOnHomeNetwork = false,
                hasSyncFolder = true,
            )
        assertFalse(result)
    }

    @Test
    fun `shouldRunSync returns false when no sync folder`() {
        val result =
            SyncWorkerLogic.shouldRunSync(
                wifiSyncEnabled = true,
                isOnWifi = true,
                isOnHomeNetwork = true,
                hasSyncFolder = false,
            )
        assertFalse(result)
    }

    // ==========================================================================
    // Time calculation tests
    // ==========================================================================

    @Test
    fun `getRepeatIntervalMillis returns correct value`() {
        val expected = TimeUnit.MINUTES.toMillis(15)
        assertEquals(expected, SyncWorkerConfig.getRepeatIntervalMillis())
    }

    @Test
    fun `getFlexIntervalMillis returns correct value`() {
        val expected = TimeUnit.MINUTES.toMillis(5)
        assertEquals(expected, SyncWorkerConfig.getFlexIntervalMillis())
    }

    // ==========================================================================
    // Work request building
    // ==========================================================================

    @Test
    fun `WorkRequestBuilder creates valid constraints`() {
        val constraints = WorkRequestBuilder.buildConstraints()
        assertTrue(constraints.requiresUnmeteredNetwork)
        assertFalse(constraints.requiresCharging)
        assertFalse(constraints.requiresBatteryNotLow)
    }
}
