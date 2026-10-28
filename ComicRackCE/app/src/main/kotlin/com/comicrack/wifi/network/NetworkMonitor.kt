package com.comicrack.wifi.network

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

/**
 * Monitors WiFi network state and detects when device is on configured home network.
 *
 * Uses ConnectivityManager's NetworkCallback with FLAG_INCLUDE_LOCATION_INFO for reactive updates.
 * This flag is required to receive SSID in callbacks on Android 12+.
 *
 * Permission model:
 * - Android 13+ (API 33): NEARBY_WIFI_DEVICES with neverForLocation=true
 * - Android 12 and below: ACCESS_COARSE_LOCATION (maxSdkVersion=32)
 */
class NetworkMonitor(
    private val context: Context,
    private val homeSSIDProvider: () -> String,
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Check if location permission is granted (required for WiFi SSID).
     */
    private fun hasWiFiPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Flow of WiFi state changes.
     *
     * Emits current state immediately, then updates on network changes.
     * Requires NEARBY_WIFI_DEVICES (API 33+) or ACCESS_COARSE_LOCATION (API 32-) permission.
     */
    val wifiState: Flow<WiFiState> =
        callbackFlow {
            if (!hasWiFiPermission()) {
                Timber.tag(TAG).w("WiFi permission not granted, cannot monitor network state")
                trySend(WiFiState.Disconnected)
                close()
                return@callbackFlow
            }

            // FLAG_INCLUDE_LOCATION_INFO is required to receive SSID in callbacks
            val callback =
                object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onAvailable(network: Network) {
                        Timber.tag(TAG).d("Network available: $network")
                        // Get capabilities with SSID from the callback's network
                        val caps = connectivityManager.getNetworkCapabilities(network)
                        trySend(getWiFiStateFromCapabilities(caps))
                    }

                    override fun onLost(network: Network) {
                        Timber.tag(TAG).d("Network lost: $network")
                        trySend(WiFiState.Disconnected)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        Timber.tag(TAG).d("Network capabilities changed")
                        trySend(getWiFiStateFromCapabilities(networkCapabilities))
                    }
                }

            // Request WiFi network updates
            val request =
                NetworkRequest
                    .Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()

            try {
                connectivityManager.registerNetworkCallback(request, callback)
            } catch (e: IllegalArgumentException) {
                Timber.tag(TAG).e(e, "Failed to register network callback: invalid request")
                trySend(WiFiState.Disconnected)
            }

            awaitClose {
                Timber.tag(TAG).d("Unregistering network callback")
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (e: IllegalArgumentException) {
                    Timber.tag(TAG).e(e, "Failed to unregister network callback: invalid callback")
                }
            }
        }.distinctUntilChanged()

    /**
     * Get current WiFi state synchronously.
     * Note: For reactive updates, prefer using wifiState Flow which uses FLAG_INCLUDE_LOCATION_INFO.
     */
    fun getCurrentWiFiState(): WiFiState {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        return getWiFiStateFromCapabilities(capabilities)
    }

    /**
     * Get current network transport type.
     */
    fun getCurrentTransport(): NetworkTransport {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities =
            activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
                ?: return NetworkTransport.UNKNOWN

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.UNKNOWN
        }
    }

    /**
     * Check if currently connected to configured home network.
     */
    fun isOnHomeNetwork(): Boolean {
        val state = getCurrentWiFiState()
        return state is WiFiState.Connected && state.isOnHomeNetwork
    }

    /**
     * Check if WiFi is currently enabled.
     */
    fun isWiFiEnabled(): Boolean = wifiManager.isWifiEnabled

    /**
     * Extract WiFi state from NetworkCapabilities using modern API.
     * Uses transportInfo to get WifiInfo which contains SSID.
     */
    private fun getWiFiStateFromCapabilities(capabilities: NetworkCapabilities?): WiFiState {
        if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return WiFiState.Disconnected
        }

        val wifiInfo = capabilities.transportInfo as? WifiInfo
        val rawSsid = wifiInfo?.ssid
        val ssid = NetworkMonitorUtils.normalizeSSID(rawSsid ?: "")

        if (ssid.isEmpty() || ssid == "<unknown ssid>") {
            Timber.tag(TAG).w("Connected to WiFi but SSID unavailable: $rawSsid")
            return WiFiState.Connected(ssid = "", isOnHomeNetwork = false)
        }

        val homeSSID = homeSSIDProvider()
        val isHome = NetworkMonitorUtils.isHomeNetwork(ssid, homeSSID)

        Timber.tag(TAG).d("WiFi state: SSID=$ssid, homeSSID=$homeSSID, isHome=$isHome")
        return WiFiState.Connected(ssid = ssid, isOnHomeNetwork = isHome)
    }
}
