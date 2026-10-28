package com.comicrack.wifi.network

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Represents the current WiFi connection state.
 */
sealed class WiFiState {
    /**
     * Not connected to any WiFi network.
     */
    data object Disconnected : WiFiState() {
        override val isConnected: Boolean = false
        override val isOnHomeNetwork: Boolean = false
        override val ssid: String = ""
    }

    /**
     * Connected to a WiFi network.
     *
     * @property ssid The SSID of the connected network
     * @property isHomeNetwork Whether this is the configured home network
     */
    data class Connected(
        override val ssid: String,
        override val isOnHomeNetwork: Boolean,
    ) : WiFiState() {
        override val isConnected: Boolean = true
    }

    abstract val isConnected: Boolean
    abstract val isOnHomeNetwork: Boolean
    abstract val ssid: String
}

/**
 * Network transport types.
 */
enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    UNKNOWN,
}
