
package com.comicrack.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.AppTheme
import com.comicrack.data.preferences.LibraryViewMode
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.data.preferences.SortOrder
import com.comicrack.settings.composables.SettingsHeader
import com.comicrack.settings.composables.SettingsSubLabel
import com.comicrack.settings.composables.ToggleListItem
import com.comicrack.settings.composables.ToggleListItemWithSubtitle
import com.comicrack.settings.ui.components.SegmentedControl

/**
 * General Settings Screen
 *
 * Features:
 * - Theme Selector (Segmented Button)
 * - Library Management (Main + Additional Folders)
 * - View & Sort Options (Segmented Buttons)
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Composable
fun GeneralSettingsScreen() {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        // --- Theme Section ---
        SettingsHeader("APPEARANCE")

        com.comicrack.settings.composables.SettingsGroupContainer {
            SettingsSubLabel("Application Theme")

            // Segmented Button for Theme
            SegmentedControl(
                items = AppTheme.entries,
                selectedItem = state.appTheme,
                onItemSelection = { viewModel.setAppTheme(it) },
                labelProvider = { theme ->
                    when (theme) {
                        AppTheme.SYSTEM -> "System"
                        AppTheme.LIGHT -> "Light"
                        AppTheme.DARK -> "Dark"
                    }
                },
            )

            ToggleListItemWithSubtitle(
                title = "Use System Colors",
                subtitle = "Match device wallpaper",
                checked = state.dynamicColorEnabled,
                onCheckedChange = { viewModel.setDynamicColorEnabled(it) },
            )
        }

        // --- Library Section ---
        SettingsHeader("LIBRARY")

        com.comicrack.settings.composables.SettingsGroupContainer {
            // Main Library Folder
            LibraryFolderPickerWithSplitButton(
                currentPath = state.libraryPath,
                isScanning = state.isScanning,
                useDirectFileAccess = state.useDirectFileAccess,
                onFolderSelected = { viewModel.setLibraryPath(it.toString()) },
                onScanRequested = { viewModel.initiateLibraryScan() },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // Additional Folders
            AdditionalFoldersSection(
                folders = state.libraryAdditionalFolders,
                isScanning = state.isScanning,
                useDirectFileAccess = state.useDirectFileAccess,
                onAddFolder = { uri ->
                    val newSet = state.libraryAdditionalFolders + uri.toString()
                    viewModel.setLibraryAdditionalFolders(newSet)
                },
                onRemoveFolder = { uriString ->
                    val newSet = state.libraryAdditionalFolders - uriString
                    viewModel.setLibraryAdditionalFolders(newSet)
                },
                onScanFolder = { uriString ->
                    viewModel.initiateTargetedLibraryScan(uriString)
                },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // View Mode (Segmented)
            Text(
                text = "View Mode",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SegmentedControl(
                items = LibraryViewMode.values().toList(),
                selectedItem = state.libraryViewMode,
                onItemSelection = { viewModel.setLibraryViewMode(it) },
                labelProvider = { mode ->
                    when (mode) {
                        LibraryViewMode.GRID -> "Grid"
                        LibraryViewMode.LIST -> "List"
                    }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // Sort Order (Segmented)
            Text(
                text = "Sort Order",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SegmentedControl(
                items = SortOrder.values().toList(),
                selectedItem = state.librarySortOrder,
                onItemSelection = { viewModel.setLibrarySortOrder(it) },
                labelProvider = { order ->
                    when (order) {
                        SortOrder.TITLE -> "Title"
                        SortOrder.DATE_ADDED -> "Date"
                        SortOrder.LAST_READ -> "Read"
                    }
                },
            )
        }

        // --- Scanning Section ---
        Text(
            text = "SCANNING",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        com.comicrack.settings.composables.SettingsGroupContainer {
            ToggleListItem(
                title = "Auto-scan Library",
                checked = state.autoScanLibrary,
                onCheckedChange = { viewModel.setAutoScanLibrary(it) },
            )

            ToggleListItem(
                title = "Recursive Scan",
                checked = state.recursiveScan,
                onCheckedChange = { viewModel.setRecursiveScan(it) },
            )

            ToggleListItem(
                title = "Remember Position",
                checked = state.rememberReadingPosition,
                onCheckedChange = { viewModel.setRememberReadingPosition(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            ToggleListItemWithSubtitle(
                title = "Use Direct File Access",
                subtitle = "Requires All Files Access permission (faster, legacy picker)",
                checked = state.useDirectFileAccess,
                onCheckedChange = { viewModel.setUseDirectFileAccess(it) },
            )
        }
    }
}

// Components moved to LibraryFolderPicker.kt and AdditionalFoldersSection.kt
