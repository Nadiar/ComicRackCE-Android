package com.comicrack.wifi.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.BackgroundSyncMode
import com.comicrack.wifi.progress.SyncProgressTracker
import com.comicrack.wifi.service.WiFiSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the WiFi sync settings screen.
 *
 * Manages UI state by combining:
 * - User preferences (enabled, home SSID, sync folder)
 * - Network state (current SSID, connection status)
 * - Sync progress (from [SyncProgressTracker])
 *
 * Provides actions for:
 * - Enabling/disabling WiFi sync
 * - Configuring home network and sync folder
 * - Initiating manual sync
 *
 * Operational notes:
 * - Reading WiFi SSID requires `ACCESS_FINE_LOCATION` on modern Android versions.
 *   The ViewModel logs a warning and exposes an empty SSID when permission is absent.
 * - Network callbacks must be unregistered (handled in `awaitClose`) to avoid leaks.
 * - Background sync starts a foreground service (`WiFiSyncService`); ensure the
 *   service provides a proper notification and handles modern foreground execution
 *   requirements (foreground service permission on recent Android versions).
 * - WorkManager-based periodic checks are used to decide when to start sync; tests
 *   should verify interplay between preferences, network state, and worker triggers.
 *
 * @see WiFiSyncSettingsScreen
 * @see WiFiSyncSettingsState
 */
@HiltViewModel
class WiFiSyncViewModel
    @Inject
    constructor(
        private val repository: AppPreferencesRepository,
        @param:ApplicationContext private val context: Context,
        private val progressTracker: SyncProgressTracker,
    ) : ViewModel() {
        companion object {
            private const val TAG = "WiFiSyncViewModel"
        }

        /**
         * Flow that monitors the current WiFi SSID.
         *
         * Requires ACCESS_FINE_LOCATION permission to read SSID on Android 8+.
         * Uses FLAG_INCLUDE_LOCATION_INFO for network callback accuracy.
         */
        private val currentSSIDFlow: StateFlow<String> =
            callbackFlow {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (connectivityManager == null) {
                    trySend("")
                    close()
                    return@callbackFlow
                }

                // ACCESS_FINE_LOCATION is required to read WiFi SSID on all modern Android versions
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    Timber.tag(TAG).w("WiFi SSID: ACCESS_FINE_LOCATION permission not granted.")
                    trySend("")
                    close()
                    return@callbackFlow
                }

                fun updateSsid(networkCapabilities: NetworkCapabilities?) {
                    val transportInfo = networkCapabilities?.transportInfo
                    val ssid =
                        if (transportInfo is WifiInfo) {
                            transportInfo.ssid
                        } else {
                            null
                        }

                    val result =
                        if (ssid != null && ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
                            val cleaned = ssid.removeSurrounding("\"")
                            Timber.tag(TAG).i("WiFi SSID detected: '$cleaned'")
                            cleaned
                        } else {
                            Timber.tag(TAG).i("WiFi SSID: empty or unknown: $ssid")
                            ""
                        }
                    trySend(result)
                }

                val request =
                    NetworkRequest
                        .Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build()

                // FLAG_INCLUDE_LOCATION_INFO is required to receive SSID in callbacks
                val networkCallback =
                    object : ConnectivityManager.NetworkCallback(
                        FLAG_INCLUDE_LOCATION_INFO,
                    ) {
                        override fun onAvailable(network: Network) {
                            updateSsid(connectivityManager.getNetworkCapabilities(network))
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            updateSsid(networkCapabilities)
                        }

                        override fun onLost(network: Network) {
                            trySend("")
                        }
                    }

                try {
                    connectivityManager.registerNetworkCallback(request, networkCallback)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "WiFi SSID: Failed to register network callback")
                    close()
                }

                awaitClose {
                    try {
                        connectivityManager.unregisterNetworkCallback(networkCallback)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "WiFi SSID: Failed to unregister network callback")
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = "",
            )

        /**
         * Combined UI state for the settings screen.
         *
         * Merges preferences, network state, and sync progress into a single
         * state object that the Compose UI observes.
         */
        val uiState: StateFlow<WiFiSyncSettingsState> =
            combine(
                combine(
                    repository.wifiSyncEnabled,
                    repository.wifiSyncHomeSSID,
                    repository.wifiSyncFolderUri,
                    repository.wifiSyncFolderName,
                    repository.wifiSyncDeviceName,
                    ::PrefState,
                ),
                combine(
                    repository.wifiSyncIsPaired,
                    repository.wifiSyncLastPairedDesktop,
                    repository.wifiSyncLastSyncTimestamp,
                    repository.wifiSyncBackgroundMode,
                    ::PairingState,
                ),
                currentSSIDFlow,
                progressTracker.progress,
            ) { prefs, pairing, currentSSID, progress ->
                WiFiSyncSettingsState(
                    wifiSyncEnabled = prefs.enabled,
                    homeSSID = prefs.homeSSID,
                    syncFolderUri = prefs.folderUri,
                    syncFolderDisplayName = prefs.folderName,
                    currentSSID = currentSSID,
                    isOnHomeNetwork = currentSSID.isNotEmpty() && currentSSID == prefs.homeSSID,
                    syncProgress = progress,
                    deviceName = prefs.deviceName,
                    isPaired = pairing.isPaired,
                    lastPairedDesktop = pairing.lastDesktop,
                    lastSyncTimestamp = pairing.lastSyncTimestamp,
                    backgroundMode = pairing.backgroundMode,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = WiFiSyncSettingsState(),
            )

        /** Internal data class to combine preference flows */
        private data class PrefState(
            val enabled: Boolean,
            val homeSSID: String,
            val folderUri: String?,
            val folderName: String,
            val deviceName: String,
        )

        /** Internal data class to combine pairing state flows */
        private data class PairingState(
            val isPaired: Boolean,
            val lastDesktop: String,
            val lastSyncTimestamp: Long,
            val backgroundMode: BackgroundSyncMode,
        )

        // =========================================================================
        // User Actions
        // =========================================================================

        /**
         * Enable or disable WiFi sync feature.
         *
         * When disabled, stops any running sync service.
         */
        fun setWifiSyncEnabled(enabled: Boolean) {
            viewModelScope.launch {
                repository.setWifiSyncEnabled(enabled)
                if (!enabled) {
                    val intent =
                        Intent(context, WiFiSyncService::class.java).apply {
                            action = WiFiSyncService.ACTION_STOP_SYNC
                        }
                    context.startService(intent)
                }
            }
        }

        /**
         * Update the device name shown to desktop.
         */
        fun setDeviceName(name: String) {
            viewModelScope.launch {
                repository.setWifiSyncDeviceName(name)
            }
        }

        /**
         * Set the home network SSID.
         *
         * Strips surrounding quotes that Android sometimes adds.
         */
        fun setHomeSSID(ssid: String) {
            viewModelScope.launch {
                repository.setWifiSyncHomeSSID(ssid.removeSurrounding("\""))
            }
        }

        /**
         * Set the sync folder from SAF picker result.
         *
         * @param uri SAF tree URI for the folder
         * @param displayName User-friendly folder name
         */
        fun setSyncFolder(
            uri: String,
            displayName: String,
        ) {
            viewModelScope.launch {
                repository.setWifiSyncFolderUri(uri)
                repository.setWifiSyncFolderName(displayName)
            }
        }

        /**
         * Convenience action to set home SSID to current network.
         */
        fun useCurrentNetwork() {
            val current = uiState.value.currentSSID
            if (current.isNotEmpty() && current != "<unknown ssid>") {
                setHomeSSID(current)
            }
        }

        /**
         * Set the background sync mode preference.
         */
        fun setBackgroundMode(mode: BackgroundSyncMode) {
            viewModelScope.launch {
                repository.setWifiSyncBackgroundMode(mode)
            }
        }

        /**
         * Start the sync service in standby mode.
         *
         * The service will broadcast presence and listen for desktop connections.
         * Sync is initiated by the desktop, not the Android device.
         */
        fun startService() {
            val state = uiState.value
            if (!WiFiSyncSettingsLogic.shouldServiceRun(state)) {
                Timber.tag(TAG).d("Service should not run - requirements not met")
                return
            }

            val intent =
                Intent(context, WiFiSyncService::class.java).apply {
                    action = WiFiSyncService.ACTION_START_SYNC
                    data = Uri.parse(state.syncFolderUri)
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the sync service.
         */
        fun stopService() {
            val intent =
                Intent(context, WiFiSyncService::class.java).apply {
                    action = WiFiSyncService.ACTION_STOP_SYNC
                }
            context.startService(intent)
        }

        /**
         * Trigger a sync request by sending the `:Sync` beacon to the Desktop.
         *
         * This sends a "ComicRackAndroid:<DeviceID>:Sync" multicast beacon that
         * Desktop's WirelessSyncProvider listens for. The Desktop will then initiate
         * a sync session with this device.
         *
         * If the WiFi sync service isn't already running, this starts it first.
         */
        fun syncNow() {
            val intent =
                Intent(context, WiFiSyncService::class.java).apply {
                    action = WiFiSyncService.ACTION_SEND_SYNC_REQUEST
                }
            context.startService(intent)
        }
    }
