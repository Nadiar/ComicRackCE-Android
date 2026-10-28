package com.comicrack.settings.screens

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comicrack.settings.ui.components.SettingsCategoryCard

/**
 * Root Settings Screen - Main entry point for preferences
 *
 * Material3 system settings-style interface with category cards
 *
 * Lists all available settings pages:
 * - Search: Global settings search with debounce and interactive results
 * - ComicRack (general): Application and library settings
 * - Reading & Display: Comic viewing and layout preferences
 * - Controls: Gesture and interaction settings
 * - Live Wallpaper: Dynamic wallpaper configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootSettingsScreen(
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onGeneralClick: () -> Unit,
    onReadingClick: () -> Unit,
    onControlsClick: () -> Unit,
    onWifiSyncClick: () -> Unit,
    onWallpaperClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            // Material 3 Search Bar as Top App Bar
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = "",
                        onQueryChange = {},
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = { if (it) onSearchClick() },
                        placeholder = { Text("Search settings") },
                        leadingIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                },
                expanded = false,
                onExpandedChange = { if (it) onSearchClick() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                colors =
                    SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                content = {},
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ComicRack General Settings Card
            SettingsCategoryCard(
                title = "General",
                description = "Application and library preferences",
                icon = Icons.Default.Settings,
                onClick = onGeneralClick,
            )

            // Reading & Display Settings Card
            SettingsCategoryCard(
                title = "Reading & Display",
                description = "Comic viewing and layout options",
                icon = Icons.Default.Tune,
                onClick = onReadingClick,
            )

            // Controls Settings Card
            SettingsCategoryCard(
                title = "Controls",
                description = "Gesture and interaction settings",
                icon = Icons.Default.Gamepad,
                onClick = onControlsClick,
            )

            // WiFi Sync Settings Card
            SettingsCategoryCard(
                title = "WiFi Sync",
                description = "Sync library with desktop app",
                icon = Icons.Default.Sync,
                onClick = onWifiSyncClick,
            )

            // Live Wallpaper Settings Card
            SettingsCategoryCard(
                title = "Live Wallpaper",
                description = "Dynamic wallpaper configuration",
                icon = Icons.Default.Wallpaper,
                onClick = onWallpaperClick,
            )

            // About Settings Card
            SettingsCategoryCard(
                title = "About",
                description = "Version info and credits",
                icon = Icons.Default.Info,
                onClick = onAboutClick,
            )

            // Footer section
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(
                    text = "ComicRack CE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Version 1.0 • Android 12+",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
