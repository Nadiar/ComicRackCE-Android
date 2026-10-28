@file:Suppress("unused")

package com.comicrack.wifi.settings

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * @deprecated Use [com.comicrack.wifi.ui.WiFiSyncSettingsScreen] instead.
 *
 * This wrapper exists for backwards compatibility during migration and simply
 * forwards parameters to the new `ui` package. It will be removed in a future release.
 *
 * @param state Current settings state used to render the screen UI
 * @param onWifiSyncEnabledChanged Callback when WiFi sync enabled toggle changes
 * @param onHomeSSIDChanged Callback when the home SSID is edited
 * @param onSyncFolderSelected Callback invoked with SAF URI when user picks a sync folder
 * @param onUseCurrentNetwork Callback to auto-fill the SSID from the current network
 * @param onSyncNow Callback to start a manual sync
 * @param onDeviceNameChanged Callback when the device name is changed
 * @param modifier Optional Compose [Modifier]
 */
@Deprecated(
    message = "Moved to ui package",
    replaceWith =
        ReplaceWith(
            "WiFiSyncSettingsScreen",
            "com.comicrack.viewer.wifi.ui.WiFiSyncSettingsScreen",
        ),
)
@Composable
fun WiFiSyncSettingsScreen(
    state: com.comicrack.wifi.ui.WiFiSyncSettingsState,
    onWifiSyncEnabledChanged: (Boolean) -> Unit,
    onHomeSSIDChanged: (String) -> Unit,
    onSyncFolderSelected: (Uri) -> Unit,
    onUseCurrentNetwork: () -> Unit,
    onSyncNow: () -> Unit,
    onDeviceNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.comicrack.wifi.ui.WiFiSyncSettingsScreen(
        state = state,
        onWifiSyncEnabledChanged = onWifiSyncEnabledChanged,
        onHomeSSIDChanged = onHomeSSIDChanged,
        onSyncFolderSelected = onSyncFolderSelected,
        onUseCurrentNetwork = onUseCurrentNetwork,
        onSyncNow = onSyncNow,
        onDeviceNameChanged = onDeviceNameChanged,
        modifier = modifier,
    )
}
