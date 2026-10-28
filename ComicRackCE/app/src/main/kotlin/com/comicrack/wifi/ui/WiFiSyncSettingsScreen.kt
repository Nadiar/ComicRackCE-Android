package com.comicrack.wifi.ui

// DOCFIX: doc-only pass - 2026-01-15 (docfix/pilot-comments)

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Main settings screen for WiFi sync configuration.
 *
 * This Composable provides the UI for:
 * - Enabling/disabling WiFi sync
 * - Configuring home network SSID
 * - Selecting sync folder
 * - Setting device name
 * - Viewing sync status and progress
 * - Initiating manual sync
 *
 * Operational notes:
 * - Reading SSID requires `ACCESS_FINE_LOCATION` on modern Android; the UI prompts
 *   for permission and displays a helpful message when denied.
 * - The Sync Folder is selected via SAF (`OpenDocumentTree`); persist and validate
 *   the picked Uri before using it in background sync flows.
 * - The 'Sync Now' action starts the foreground `WiFiSyncService`; ensure the
 *   service presents a notification promptly to satisfy modern foreground requirements.
 *
 * @param state Current UI state from [WiFiSyncViewModel]
 * @param onWifiSyncEnabledChanged Callback when sync toggle changes
 * @param onHomeSSIDChanged Callback when home SSID is configured
 * @param onSyncFolderSelected Callback when sync folder is picked
 * @param onUseCurrentNetwork Callback to use current network as home
 * @param onSyncNow Callback to start manual sync
 * @param onDeviceNameChanged Callback when device name is changed
 * @param modifier Modifier for the root layout
 *
 * @see WiFiSyncViewModel
 * @see WiFiSyncSettingsState
 */
@Composable
fun WiFiSyncSettingsScreen(
    state: WiFiSyncSettingsState,
    onWifiSyncEnabledChanged: (Boolean) -> Unit,
    onHomeSSIDChanged: (String) -> Unit,
    onSyncFolderSelected: (Uri) -> Unit,
    onUseCurrentNetwork: () -> Unit,
    onSyncNow: () -> Unit,
    onDeviceNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var showSSIDDialog by remember { mutableStateOf(false) }
    var showDeviceNameDialog by remember { mutableStateOf(false) }

    // SAF folder picker launcher
    val folderPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let { onSyncFolderSelected(it) }
        }

    // Location permission state (required for SSID on Android 8+)
    var hasLocationPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasLocationPermission = isGranted
        }

    // Check permission on first composition
    androidx.compose.runtime.LaunchedEffect(Unit) {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    Column(
        modifier =
            modifier
                .verticalScroll(scrollState)
                .padding(16.dp),
    ) {
        com.comicrack.settings.composables
            .SettingsHeader(text = "WIFI SYNC")

        com.comicrack.settings.composables.SettingsGroupContainer {
            // Main enable/disable toggle
            com.comicrack.settings.composables.ToggleListItemWithSubtitle(
                title = "WiFi Sync",
                subtitle = "Sync with ComicRack CE desktop over WiFi",
                checked = state.wifiSyncEnabled,
                onCheckedChange = onWifiSyncEnabledChanged,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Network status card
        StatusCard(
            state = state,
            isPermissionGranted = hasLocationPermission,
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Configuration section (shown when enabled)
        if (state.wifiSyncEnabled) {
            com.comicrack.settings.composables
                .SettingsHeader(text = "CONFIGURATION")

            com.comicrack.settings.composables.SettingsGroupContainer {
                // Home Network SSID
                com.comicrack.settings.composables.SettingsListItem(
                    title = "Home Network",
                    subtitle = state.homeSSID.ifEmpty { "Not configured" },
                    onClick = { showSSIDDialog = true },
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Device Name
                com.comicrack.settings.composables.SettingsListItem(
                    title = "Device Name",
                    subtitle = state.deviceName,
                    onClick = { showDeviceNameDialog = true },
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Sync Folder
                com.comicrack.settings.composables.SettingsListItem(
                    title = "Sync Folder",
                    subtitle = state.syncFolderDisplayName.ifEmpty { "Not selected" },
                    onClick = { folderPickerLauncher.launch(null) },
                )
            }

            // Quick action to use current network
            if (state.currentSSID.isNotEmpty() && state.currentSSID != state.homeSSID) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onUseCurrentNetwork,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.End),
                ) {
                    Text("Use current network (${state.currentSSID})")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sync Now button
            Button(
                onClick = onSyncNow,
                enabled = WiFiSyncSettingsLogic.isSyncReady(state),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sync Now")
            }

            // Progress indicator (shown during sync)
            if (state.syncProgress.totalBooks > 0 || state.syncProgress.percent > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { state.syncProgress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        if (state.syncProgress.isComplete) {
                            "Sync Complete!"
                        } else {
                            val pct = state.syncProgress.percent
                            val processed = state.syncProgress.booksProcessed
                            val total = state.syncProgress.totalBooks
                            "Syncing: $pct% ($processed/$total)"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.syncProgress.currentBook.isNotEmpty()) {
                    Text(
                        text = state.syncProgress.currentBook,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Status/error messages
            val messages = WiFiSyncSettingsLogic.getStatusMessages(state)
            if (messages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                messages.forEach { message ->
                    Text(
                        text = "• $message",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }

    // SSID input dialog
    if (showSSIDDialog) {
        SSIDInputDialog(
            currentSSID = state.homeSSID,
            onDismiss = { showSSIDDialog = false },
            onConfirm = { ssid ->
                onHomeSSIDChanged(ssid)
                showSSIDDialog = false
            },
            discoveredSSID =
                if (state.currentSSID.isNotEmpty() && state.currentSSID != "<unknown ssid>") {
                    state.currentSSID.removeSurrounding("\"")
                } else {
                    null
                },
        )
    }

    // Device name input dialog
    if (showDeviceNameDialog) {
        DeviceNameInputDialog(
            currentName = state.deviceName,
            onDismiss = { showDeviceNameDialog = false },
            onConfirm = { name ->
                onDeviceNameChanged(name)
                showDeviceNameDialog = false
            },
        )
    }
}

// =============================================================================
// Private Composables
// =============================================================================

/**
 * Card showing current network connection status.
 */
@Composable
private fun StatusCard(
    state: WiFiSyncSettingsState,
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (state.isOnHomeNetwork && state.wifiSyncEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Current Network",
                style = MaterialTheme.typography.labelMedium,
            )

            if (state.currentSSID.isNotEmpty() && state.currentSSID != "<unknown ssid>") {
                Text(
                    text = state.currentSSID,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                if (!isPermissionGranted) {
                    Text(
                        text = "Unknown (Permission required)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestPermission) {
                        Text("Grant Permission")
                    }
                } else {
                    Text(
                        text = "Not connected or unknown",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (state.isOnHomeNetwork) {
                Text(
                    text = "✓ On home network",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Dialog for entering home network SSID.
 */
@Composable
private fun SSIDInputDialog(
    currentSSID: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    discoveredSSID: String? = null,
) {
    var ssid by remember { mutableStateOf(currentSSID) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Home Network SSID") },
        text = {
            Column {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Network Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (discoveredSSID != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { ssid = discoveredSSID }) {
                            Text("Use current: $discoveredSSID")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(ssid) },
                enabled = ssid.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Dialog for entering device name.
 */
@Composable
private fun DeviceNameInputDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device Name") },
        text = {
            Column {
                Text(
                    text = "This name appears on the desktop when searching for devices.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
