package com.comicrack.settings

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.AppTheme
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.settings.catalog.SettingCategory
import com.comicrack.settings.screens.ControlsSettingsScreen
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.comicrack.settings.screens.GeneralSettingsScreen
import com.comicrack.settings.screens.ReadingSettingsScreen
import com.comicrack.settings.screens.RootSettingsScreen
import com.comicrack.settings.screens.SearchScreen
import com.comicrack.settings.screens.WallpaperSettingsScreen
import com.comicrack.settings.screens.TapZoneSettingsScreen
import com.comicrack.settings.screens.VolumeButtonsSettingsScreen
import com.comicrack.settings.screens.DPadSettingsScreen
import com.comicrack.settings.screens.GamepadSettingsScreen
import com.comicrack.wifi.settings.WiFiSyncViewModel
import com.comicrack.wifi.ui.WiFiSyncSettingsScreen

// import com.comicrack.theme.ComicRackTheme // Legacy
import com.comicrack.ui.theme.ComicRackMaterial3Theme

/**
 * Settings Activity for Project Epsilon: Preferences Modernization
 *
 * Manages the settings UI with Jetpack Navigation Compose.
 * Observe app theme preference to prevent flashing/inconsistency.
 *
 * Implementation & testing notes:
 * - Keep navigation routes stable; add UI tests that assert deep-linking and
 *   navigation stack restoration when users jump between screens.
 * - Observe theme preference at Activity level to avoid theme flash on startup.
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    private val pendingRoute = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingRoute.value = intent?.getStringExtra(EXTRA_TARGET_ROUTE)

        setContent {
            // Inject ViewModel at Activity level to observe Theme
            val viewModel: PreferencesViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsState()

            // Determine effective theme
            val isDark =
                when (state.appTheme) {
                    AppTheme.DARK -> true
                    AppTheme.LIGHT -> false
                    AppTheme.SYSTEM -> isSystemInDarkTheme()
                }

            // Fixed Composable invocation and parameter name
            ComicRackMaterial3Theme(
                darkTheme = isDark,
                dynamicColor = state.dynamicColorEnabled,
            ) {
                SettingsApp(viewModel, pendingRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_TARGET_ROUTE)
    }

    companion object {
        const val EXTRA_TARGET_ROUTE = "target_route"

        val VALID_ROUTES = setOf(
            "root", "search", "general", "reading", "controls",
            "volume", "keyboard", "gamepad", "tap_zones",
            "wifi_sync", "wallpaper", "about",
        )
    }
}

@Composable
private fun SettingsApp(
    viewModel: PreferencesViewModel,
    pendingRoute: androidx.compose.runtime.MutableState<String?> = androidx.compose.runtime.mutableStateOf(null),
) {
    val navController = rememberNavController()

    // Navigate to target route when pending (supports both onCreate and onNewIntent)
    val targetRoute by pendingRoute
    val validTarget = targetRoute?.takeIf { it in SettingsActivity.VALID_ROUTES && it != "root" }
    androidx.compose.runtime.LaunchedEffect(validTarget) {
        validTarget?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
            pendingRoute.value = null
        }
    }

    NavHost(
        navController = navController,
        startDestination = "root",
    ) {
        composable("root") {
            val context = LocalContext.current
            RootSettingsScreen(
                onBack = { (context as? android.app.Activity)?.finish() },
                onSearchClick = {
                    navController.navigate("search") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onGeneralClick = {
                    navController.navigate("general") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onReadingClick = {
                    navController.navigate("reading") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onControlsClick = {
                    navController.navigate("controls") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onWifiSyncClick = {
                    navController.navigate("wifi_sync") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onWallpaperClick = {
                    navController.navigate("wallpaper") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAboutClick = {
                    navController.navigate("about") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        // Custom Layout for Search (No TopAppBar wrapper)
        composable("search") {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onSettingNavigate = { setting ->
                    // Navigate to appropriate screen based on setting category
                    val route =
                        when (setting.category) {
                            SettingCategory.GENERAL -> "general"
                            SettingCategory.READING -> "reading"
                            SettingCategory.CONTROLS -> "controls"
                            SettingCategory.WALLPAPER -> "wallpaper"
                        }
                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        composable("general") {
            SettingsScreenWithBack(
                title = "ComicRack Settings",
                onBackClick = { navController.popBackStack() },
            ) {
                GeneralSettingsScreen()
            }
        }

        composable("reading") {
            SettingsScreenWithBack(
                title = "Reading & Display",
                onBackClick = { navController.popBackStack() },
            ) {
                ReadingSettingsScreen()
            }
        }

        composable("controls") {
            SettingsScreenWithBack(
                title = "Controls",
                onBackClick = { navController.popBackStack() },
            ) {
                ControlsSettingsScreen(
                    onTapZonesClick = {
                        navController.navigate("tap_zones") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onVolumeClick = {
                        navController.navigate("volume") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onKeyboardClick = {
                        navController.navigate("keyboard") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onGamepadClick = {
                        navController.navigate("gamepad") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }

        composable("volume") {
            SettingsScreenWithBack(
                title = "Volume Buttons",
                onBackClick = { navController.popBackStack() },
            ) {
                VolumeButtonsSettingsScreen()
            }
        }

        composable("keyboard") {
            SettingsScreenWithBack(
                title = "Keyboard & D-Pad",
                onBackClick = { navController.popBackStack() },
            ) {
                DPadSettingsScreen()
            }
        }

        composable("gamepad") {
            SettingsScreenWithBack(
                title = "Gamepad Controls",
                onBackClick = { navController.popBackStack() },
            ) {
                GamepadSettingsScreen()
            }
        }

        composable("tap_zones") {
            SettingsScreenWithBack(
                title = "Tap Zones",
                onBackClick = { navController.popBackStack() },
            ) {
                TapZoneSettingsScreen()
            }
        }

        composable("wifi_sync") {
            SettingsScreenWithBack(
                title = "WiFi Sync",
                onBackClick = { navController.popBackStack() },
            ) {
                val viewModel: WiFiSyncViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsState()

                val context = LocalContext.current

                WiFiSyncSettingsScreen(
                    state = state,
                    onWifiSyncEnabledChanged = viewModel::setWifiSyncEnabled,
                    onHomeSSIDChanged = viewModel::setHomeSSID,
                    onSyncFolderSelected = { uri ->
                        // Persist permissions
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        try {
                            context.contentResolver.takePersistableUriPermission(uri, flags)
                        } catch (e: SecurityException) {
                            // Log or handle denial if needed, but usually this works if opened via SAF
                        }

                        // Simplified: using URI path as display name for now.
                        // Ideal: Resolve generic document ID to display name.
                        viewModel.setSyncFolder(uri.toString(), uri.path?.substringAfterLast(":") ?: "Selected Folder")
                    },
                    onUseCurrentNetwork = viewModel::useCurrentNetwork,
                    onSyncNow = viewModel::syncNow,
                    onDeviceNameChanged = viewModel::setDeviceName,
                )
            }
        }

        composable("wallpaper") {
            SettingsScreenWithBack(
                title = "Live Wallpaper",
                onBackClick = { navController.popBackStack() },
            ) {
                WallpaperSettingsScreen()
            }
        }

        composable("about") {
            SettingsScreenWithBack(
                title = "About",
                onBackClick = { navController.popBackStack() },
            ) {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier
                        .padding(16.dp),
                ) {
                    Text(
                        "ComicRack Community Edition",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    )
                    Text("Version 1.80", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                    Text(
                        "Modernized with Material 3",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Wrapper composable for settings screens with back button navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenWithBack(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                    androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding),
        ) {
            content()
        }
    }
}
