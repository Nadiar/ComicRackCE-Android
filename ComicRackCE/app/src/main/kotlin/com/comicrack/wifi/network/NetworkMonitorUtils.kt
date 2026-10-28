package com.comicrack.wifi.network

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Utility functions for network monitoring.
 *
 * These are pure functions that can be unit tested without
 * Android framework dependencies.
 */
object NetworkMonitorUtils {
    private const val UNKNOWN_SSID_MARKER = "<unknown ssid>"

    /**
     * Normalize an SSID by removing surrounding quotes and trimming whitespace.
     *
     * Android may return SSIDs wrapped in quotes ("NetworkName") or as
     * "<unknown ssid>" when location permission is not granted.
     *
     * @param ssid The raw SSID string
     * @return Normalized SSID without quotes
     */
    fun normalizeSSID(ssid: String): String {
        val trimmed = ssid.trim()

        // Handle unknown SSID marker
        if (trimmed.equals(UNKNOWN_SSID_MARKER, ignoreCase = true)) {
            return ""
        }

        // Remove surrounding quotes if present
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    /**
     * Check if the current network matches the configured home network.
     *
     * Comparison is case-insensitive and handles quoted SSIDs.
     *
     * @param currentSSID The current network SSID (may be quoted)
     * @param homeSSID The configured home network SSID
     * @return true if the networks match
     */
    fun isHomeNetwork(
        currentSSID: String,
        homeSSID: String,
    ): Boolean {
        val normalizedCurrent = normalizeSSID(currentSSID)
        val normalizedHome = normalizeSSID(homeSSID)

        if (normalizedCurrent.isEmpty() || normalizedHome.isEmpty()) {
            return false
        }

        return normalizedCurrent.equals(normalizedHome, ignoreCase = true)
    }

    /**
     * Check if a network transport is typically unmetered.
     *
     * WiFi and Ethernet are typically unmetered, while cellular is metered.
     *
     * @param transport The network transport type
     * @return true if the transport is typically unmetered
     */
    fun isUnmeteredTransport(transport: NetworkTransport): Boolean =
        when (transport) {
            NetworkTransport.WIFI -> true
            NetworkTransport.ETHERNET -> true
            NetworkTransport.CELLULAR -> false
            NetworkTransport.UNKNOWN -> false
        }
}
