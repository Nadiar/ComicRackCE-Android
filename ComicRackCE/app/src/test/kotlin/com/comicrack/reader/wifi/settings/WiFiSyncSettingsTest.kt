package com.comicrack.wifi.settings

import com.comicrack.wifi.network.WiFiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for WiFi Sync settings logic.
 */
class WiFiSyncSettingsTest {
    // ==========================================================================
    // Settings state tests
    // ==========================================================================

    @Test
    fun `default settings state has sync disabled`() {
        val state = WiFiSyncSettingsState()
        assertFalse(state.wifiSyncEnabled)
    }

    @Test
    fun `default settings state has empty home SSID`() {
        val state = WiFiSyncSettingsState()
        assertEquals("", state.homeSSID)
    }

    @Test
    fun `default settings state has no sync folder`() {
        val state = WiFiSyncSettingsState()
        assertNull(state.syncFolderUri)
    }

    @Test
    fun `settings state accepts custom values`() {
        val state =
            WiFiSyncSettingsState(
                wifiSyncEnabled = true,
                homeSSID = "MyNetwork",
                syncFolderUri = "content://folder",
                currentSSID = "MyNetwork",
                isOnHomeNetwork = true,
            )
        assertTrue(state.wifiSyncEnabled)
        assertEquals("MyNetwork", state.homeSSID)
        assertEquals("content://folder", state.syncFolderUri)
        assertTrue(state.isOnHomeNetwork)
    }

    // ==========================================================================
    // WiFi state display tests
    // ==========================================================================

    @Test
    fun `getWiFiStatusText returns disconnected when not on WiFi`() {
        val text = WiFiSyncSettingsLogic.getWiFiStatusText(WiFiState.Disconnected)
        assertEquals("Not connected to WiFi", text)
    }

    @Test
    fun `getWiFiStatusText returns SSID when connected`() {
        val text =
            WiFiSyncSettingsLogic.getWiFiStatusText(
                WiFiState.Connected("MyNetwork", isOnHomeNetwork = false),
            )
        assertEquals("Connected to: MyNetwork", text)
    }

    @Test
    fun `getWiFiStatusText indicates home network`() {
        val text =
            WiFiSyncSettingsLogic.getWiFiStatusText(
                WiFiState.Connected("MyNetwork", isOnHomeNetwork = true),
            )
        assertTrue(text.contains("home network"))
    }

    // ==========================================================================
    // Sync readiness tests
    // ==========================================================================

    @Test
    fun `isSyncReady returns false when sync disabled`() {
        val state =
            WiFiSyncSettingsState(
                wifiSyncEnabled = false,
                homeSSID = "Network",
                syncFolderUri = "content://folder",
                isOnHomeNetwork = true,
            )
        assertFalse(WiFiSyncSettingsLogic.isSyncReady(state))
    }

    @Test
    fun `isSyncReady returns false when no home SSID`() {
        val state =
            WiFiSyncSettingsState(
                wifiSyncEnabled = true,
                homeSSID = "",
                syncFolderUri = "content://folder",
                isOnHomeNetwork = true,
            )
        assertFalse(WiFiSyncSettingsLogic.isSyncReady(state))
    }

    @Test
    fun `isSyncReady returns false when no sync folder`() {
        val state =
            WiFiSyncSettingsState(
                wifiSyncEnabled = true,
                homeSSID = "Network",
                syncFolderUri = null,
                isOnHomeNetwork = true,
            )
        assertFalse(WiFiSyncSettingsLogic.isSyncReady(state))
    }

    @Test
    fun `isSyncReady returns false when not on home network`() {
        val state =
            WiFiSyncSettingsState(
                wifiSyncEnabled = true,
                homeSSID = "HomeNetwork",
                syncFolderUri = "content://folder",
                isOnHomeNetwork = false,
            )
        assertFalse(WiFiSyncSettingsLogic.isSyncReady(state))
    }

    @Test
    fun `isSyncReady returns true when all conditions met`() {
        val state =
            WiFiSyncSettingsState(
                wifiSyncEnabled = true,
                homeSSID = "HomeNetwork",
                syncFolderUri = "content://folder",
                isOnHomeNetwork = true,
            )
        assertTrue(WiFiSyncSettingsLogic.isSyncReady(state))
    }

    // ==========================================================================
    // Validation tests
    // ==========================================================================

    @Test
    fun `getStatusMessages lists missing requirements`() {
        val state =
            WiFiSyncSettingsState(
                wifiSyncEnabled = true,
                homeSSID = "",
                syncFolderUri = null,
                isOnHomeNetwork = false,
            )
        val messages = WiFiSyncSettingsLogic.getStatusMessages(state)
        assertTrue(messages.any { it.contains("home network") })
        assertTrue(messages.any { it.contains("sync folder") })
    }

    @Test
    fun `getStatusMessages returns empty when ready`() {
        val state =
            WiFiSyncSettingsState(
                wifiSyncEnabled = true,
                homeSSID = "Network",
                syncFolderUri = "content://folder",
                isOnHomeNetwork = true,
            )
        val messages = WiFiSyncSettingsLogic.getStatusMessages(state)
        assertTrue(messages.isEmpty())
    }
}
