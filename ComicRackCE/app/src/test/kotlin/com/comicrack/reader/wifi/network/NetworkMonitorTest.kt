package com.comicrack.wifi.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for NetworkMonitor - verifies WiFi state detection logic.
 *
 * Note: These tests focus on the pure logic components that can be tested
 * without Android framework dependencies. The actual ConnectivityManager
 * and WifiManager interactions are tested via instrumentation tests.
 */
class NetworkMonitorTest {
    // ==========================================================================
    // SSID normalization tests
    // ==========================================================================

    @Test
    fun `normalizeSSID removes surrounding quotes`() {
        assertEquals("MyNetwork", NetworkMonitorUtils.normalizeSSID("\"MyNetwork\""))
    }

    @Test
    fun `normalizeSSID handles already unquoted SSID`() {
        assertEquals("MyNetwork", NetworkMonitorUtils.normalizeSSID("MyNetwork"))
    }

    @Test
    fun `normalizeSSID handles empty string`() {
        assertEquals("", NetworkMonitorUtils.normalizeSSID(""))
    }

    @Test
    fun `normalizeSSID handles single quotes only`() {
        assertEquals("", NetworkMonitorUtils.normalizeSSID("\"\""))
    }

    @Test
    fun `normalizeSSID handles partial quotes`() {
        // Only leading quote
        assertEquals("\"Network", NetworkMonitorUtils.normalizeSSID("\"Network"))
        // Only trailing quote
        assertEquals("Network\"", NetworkMonitorUtils.normalizeSSID("Network\""))
    }

    @Test
    fun `normalizeSSID trims whitespace`() {
        assertEquals("MyNetwork", NetworkMonitorUtils.normalizeSSID("  MyNetwork  "))
    }

    @Test
    fun `normalizeSSID handles unknown SSID marker`() {
        assertEquals("", NetworkMonitorUtils.normalizeSSID("<unknown ssid>"))
    }

    // ==========================================================================
    // SSID matching tests
    // ==========================================================================

    @Test
    fun `isHomeNetwork returns true for exact match`() {
        assertTrue(NetworkMonitorUtils.isHomeNetwork("MyHomeWiFi", "MyHomeWiFi"))
    }

    @Test
    fun `isHomeNetwork returns true for case-insensitive match`() {
        assertTrue(NetworkMonitorUtils.isHomeNetwork("myhomewifi", "MyHomeWiFi"))
    }

    @Test
    fun `isHomeNetwork returns false for non-matching SSID`() {
        assertFalse(NetworkMonitorUtils.isHomeNetwork("OtherNetwork", "MyHomeWiFi"))
    }

    @Test
    fun `isHomeNetwork returns false when home SSID is empty`() {
        assertFalse(NetworkMonitorUtils.isHomeNetwork("MyNetwork", ""))
    }

    @Test
    fun `isHomeNetwork returns false when current SSID is empty`() {
        assertFalse(NetworkMonitorUtils.isHomeNetwork("", "MyHomeWiFi"))
    }

    @Test
    fun `isHomeNetwork handles quoted SSID comparison`() {
        assertTrue(NetworkMonitorUtils.isHomeNetwork("\"MyHomeWiFi\"", "MyHomeWiFi"))
    }

    @Test
    fun `isHomeNetwork handles both SSIDs quoted`() {
        assertTrue(NetworkMonitorUtils.isHomeNetwork("\"MyHomeWiFi\"", "\"MyHomeWiFi\""))
    }

    // ==========================================================================
    // WiFi state tests
    // ==========================================================================

    @Test
    fun `WiFiState disconnected has correct properties`() {
        val state = WiFiState.Disconnected
        assertFalse(state.isConnected)
        assertFalse(state.isOnHomeNetwork)
        assertEquals("", state.ssid)
    }

    @Test
    fun `WiFiState connected to home network has correct properties`() {
        val state = WiFiState.Connected("MyHomeWiFi", isOnHomeNetwork = true)
        assertTrue(state.isConnected)
        assertTrue(state.isOnHomeNetwork)
        assertEquals("MyHomeWiFi", state.ssid)
    }

    @Test
    fun `WiFiState connected to other network has correct properties`() {
        val state = WiFiState.Connected("OtherNetwork", isOnHomeNetwork = false)
        assertTrue(state.isConnected)
        assertFalse(state.isOnHomeNetwork)
        assertEquals("OtherNetwork", state.ssid)
    }

    // ==========================================================================
    // Network capability checks
    // ==========================================================================

    @Test
    fun `isUnmeteredNetwork returns true for WiFi`() {
        // WiFi is typically unmetered
        assertTrue(NetworkMonitorUtils.isUnmeteredTransport(NetworkTransport.WIFI))
    }

    @Test
    fun `isUnmeteredNetwork returns false for cellular`() {
        // Cellular is typically metered
        assertFalse(NetworkMonitorUtils.isUnmeteredTransport(NetworkTransport.CELLULAR))
    }

    @Test
    fun `isUnmeteredNetwork returns true for ethernet`() {
        // Ethernet is typically unmetered
        assertTrue(NetworkMonitorUtils.isUnmeteredTransport(NetworkTransport.ETHERNET))
    }

    @Test
    fun `isUnmeteredNetwork returns false for unknown`() {
        assertFalse(NetworkMonitorUtils.isUnmeteredTransport(NetworkTransport.UNKNOWN))
    }
}
