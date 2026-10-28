package com.comicrack.wifi.ui

// DOCFIX: doc-only pass â€" 2026-01-15 (docfix/pilot-comments)

import com.comicrack.data.preferences.BackgroundSyncMode
import com.comicrack.wifi.network.WiFiState
import com.comicrack.wifi.progress.SyncProgress

/**
 * UI state for the WiFi sync settings screen.
 *
 * This data class holds all the state needed to render the settings UI.
 * It's derived from various sources (preferences, network state, sync progress)
 * and combined in [WiFiSyncViewModel].
 *
 * @property wifiSyncEnabled Whether WiFi sync feature is enabled
 * @property homeSSID The configured home network SSID for auto-sync
 * @property syncFolderUri URI string of the selected sync folder (SAF tree URI)
 * @property syncFolderDisplayName User-friendly name of the sync folder
 * @property currentSSID SSID of the currently connected WiFi network
 * @property isOnHomeNetwork True if currently connected to the configured home network
 * @property syncProgress Current sync progress (if sync is running)
 * @property deviceName User-configurable device name shown to desktop
 * @property isPaired Whether this device has ever successfully paired with a desktop
 * @property lastPairedDesktop Hostname/IP of the last desktop we synced with
 * @property lastSyncTimestamp Unix timestamp of last successful sync
 * @property backgroundMode When to run sync in background
 *
 * @see WiFiSyncViewModel
 * @see WiFiSyncSettingsScreen
 */
data class WiFiSyncSettingsState(
    val wifiSyncEnabled: Boolean = false,
    val homeSSID: String = "",
    val syncFolderUri: String? = null,
    val syncFolderDisplayName: String = "",
    val currentSSID: String = "",
    val isOnHomeNetwork: Boolean = false,
    val syncProgress: SyncProgress = SyncProgress(),
    val deviceName: String = "",
    // Pairing state
    val isPaired: Boolean = false,
    val lastPairedDesktop: String = "",
    val lastSyncTimestamp: Long = 0L,
    val backgroundMode: BackgroundSyncMode = BackgroundSyncMode.CHARGING,
) {
    /**
     * Human-readable description of when the last sync occurred.
     */
    val lastSyncText: String
        get() =
            when {
                lastSyncTimestamp == 0L -> "Never"
                else -> {
                    val formatter = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                    formatter.format(java.util.Date(lastSyncTimestamp))
                }
            }

    /**
     * Current sync phase for UI display.
     */
    val syncPhase: SyncPhase
        get() =
            when {
                !wifiSyncEnabled -> SyncPhase.DISABLED
                syncFolderUri == null -> SyncPhase.NEEDS_SETUP
                homeSSID.isEmpty() -> SyncPhase.NEEDS_SETUP
                !isOnHomeNetwork -> SyncPhase.AWAY_FROM_HOME
                !isPaired -> SyncPhase.WAITING_FOR_PAIRING
                syncProgress.isActive -> SyncPhase.SYNCING
                else -> SyncPhase.READY
            }
}

/**
 * Sync phases for UI state machine.
 */
enum class SyncPhase {
    /** Feature is disabled */
    DISABLED,

    /** Missing required configuration (folder, home SSID) */
    NEEDS_SETUP,

    /** Not on home network */
    AWAY_FROM_HOME,

    /** On home network, broadcasting, waiting for desktop to initiate pairing */
    WAITING_FOR_PAIRING,

    /** Actively syncing */
    SYNCING,

    /** Paired and ready - desktop can initiate sync anytime */
    READY,
}

/**
 * Pure logic helpers for WiFi sync settings.
 *
 * These functions contain no side effects and can be easily unit tested.
 * They determine UI behavior based on [WiFiSyncSettingsState].
 */
object WiFiSyncSettingsLogic {
    /**
     * Get display text describing current WiFi connection state.
     *
     * @param wifiState Current WiFi connection state
     * @return Human-readable status string
     */
    fun getWiFiStatusText(wifiState: WiFiState): String =
        when (wifiState) {
            is WiFiState.Disconnected -> "Not connected to WiFi"
            is WiFiState.Connected -> {
                if (wifiState.isOnHomeNetwork) {
                    "Connected to: ${wifiState.ssid} (home network)"
                } else {
                    "Connected to: ${wifiState.ssid}"
                }
            }
        }

    /**
     * Check if all requirements are met to start a sync.
     *
     * All conditions must be satisfied:
     * - WiFi sync is enabled in settings
     * - Home SSID is configured
     * - Sync folder has been selected
     * - Device is currently on the home network
     *
     * @param state Current settings state
     * @return true if sync can be started
     */
    fun isSyncReady(state: WiFiSyncSettingsState): Boolean =
        state.wifiSyncEnabled &&
            state.homeSSID.isNotEmpty() &&
            state.syncFolderUri != null &&
            state.isOnHomeNetwork

    /**
     * Get list of missing requirements preventing sync.
     *
     * Used to display helpful messages to the user about what
     * needs to be configured before sync can run.
     *
     * @param state Current settings state
     * @return List of requirement messages (empty if all requirements met)
     */
    fun getStatusMessages(state: WiFiSyncSettingsState): List<String> {
        if (!state.wifiSyncEnabled) {
            return emptyList() // Sync disabled, no messages needed
        }

        val messages = mutableListOf<String>()

        if (state.homeSSID.isEmpty()) {
            messages.add("Configure home network SSID")
        }

        if (state.syncFolderUri == null) {
            messages.add("Select sync folder")
        }

        if (!state.isOnHomeNetwork) {
            messages.add("Not on home network")
        }

        return messages
    }

    /**
     * Get the primary status message based on current sync phase.
     *
     * @param state Current settings state
     * @return Primary status message for UI display
     */
    fun getPrimaryStatus(state: WiFiSyncSettingsState): String =
        when (state.syncPhase) {
            SyncPhase.DISABLED -> "WiFi Sync is disabled"
            SyncPhase.NEEDS_SETUP -> "Complete setup to enable sync"
            SyncPhase.AWAY_FROM_HOME -> "Not on home network (${state.currentSSID})"
            SyncPhase.WAITING_FOR_PAIRING -> "Broadcasting... Open ComicRack on your desktop to pair"
            SyncPhase.SYNCING -> "Syncing..."
            SyncPhase.READY ->
                if (state.lastPairedDesktop.isNotEmpty()) {
                    "Ready â€¢ Last synced: ${state.lastSyncText}"
                } else {
                    "Ready â€¢ Waiting for desktop"
                }
        }

    /**
     * Whether the service should be running based on current state.
     *
     * Service runs when:
     * - WiFi sync is enabled AND configured
     * - Either on home network OR in foreground (for pairing)
     */
    fun shouldServiceRun(state: WiFiSyncSettingsState): Boolean {
        if (!state.wifiSyncEnabled) return false
        if (state.syncFolderUri == null) return false
        if (state.homeSSID.isEmpty()) return false
        // Service should run when on home network
        return state.isOnHomeNetwork
    }
}
