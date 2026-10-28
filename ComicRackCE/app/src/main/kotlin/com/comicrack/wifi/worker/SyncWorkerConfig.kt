package com.comicrack.wifi.worker

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import java.util.concurrent.TimeUnit

/**
 * Configuration constants for the WiFi sync worker.
 */
object SyncWorkerConfig {
    /**
     * Tag for the periodic work request.
     */
    const val WORKER_TAG = "wifi_sync_check"

    /**
     * Unique work name for the periodic sync check.
     */
    const val UNIQUE_WORK_NAME = "wifi_sync_periodic"

    /**
     * Repeat interval in minutes.
     * WorkManager minimum is 15 minutes.
     */
    const val REPEAT_INTERVAL_MINUTES = 15L

    /**
     * Flex interval in minutes.
     * Work can run within this window before the end of the repeat interval.
     */
    const val FLEX_INTERVAL_MINUTES = 5L

    /**
     * Get repeat interval in milliseconds.
     */
    fun getRepeatIntervalMillis(): Long = TimeUnit.MINUTES.toMillis(REPEAT_INTERVAL_MINUTES)

    /**
     * Get flex interval in milliseconds.
     */
    fun getFlexIntervalMillis(): Long = TimeUnit.MINUTES.toMillis(FLEX_INTERVAL_MINUTES)
}

/**
 * Logic helpers for sync worker decisions.
 *
 * These are pure functions that can be unit tested.
 */
object SyncWorkerLogic {
    /**
     * Determine if sync should run based on current conditions.
     *
     * @param wifiSyncEnabled Whether WiFi sync is enabled in settings
     * @param isOnWifi Whether device is connected to WiFi
     * @param isOnHomeNetwork Whether device is on configured home network
     * @param hasSyncFolder Whether a sync folder is configured
     * @return true if all conditions are met for sync
     */
    fun shouldRunSync(
        wifiSyncEnabled: Boolean,
        isOnWifi: Boolean,
        isOnHomeNetwork: Boolean,
        hasSyncFolder: Boolean,
    ): Boolean = wifiSyncEnabled && isOnWifi && isOnHomeNetwork && hasSyncFolder
}

/**
 * Builder for WorkManager work requests.
 *
 * Contains constraint configuration that can be unit tested.
 */
object WorkRequestBuilder {
    /**
     * Build constraints for the sync worker.
     *
     * Requires unmetered network (WiFi) but not charging or battery.
     */
    fun buildConstraints(): WorkConstraints =
        WorkConstraints(
            requiresUnmeteredNetwork = true,
            requiresCharging = false,
            requiresBatteryNotLow = false,
        )
}

/**
 * Data class representing work constraints.
 *
 * Used for testing without Android dependencies.
 * Actual WorkManager Constraints are built in the worker.
 */
data class WorkConstraints(
    val requiresUnmeteredNetwork: Boolean,
    val requiresCharging: Boolean,
    val requiresBatteryNotLow: Boolean,
)
