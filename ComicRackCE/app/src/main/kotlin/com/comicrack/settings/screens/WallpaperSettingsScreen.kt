package com.comicrack.settings.screens

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.ColorTheme
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.settings.composables.SettingsGroupContainer
import com.comicrack.settings.composables.SettingsHeader
import com.comicrack.settings.composables.SettingsSubLabel
import com.comicrack.settings.composables.ToggleListItemWithSubtitle
import com.comicrack.settings.ui.components.SegmentedControl

/**
 * Wallpaper Settings Screen - Live wallpaper configuration
 *
 * Features:
 * - Animation speed control
 * - Parallax effect toggle
 * - Color theme selection (Segmented Control)
 * - Low power mode configuration
 *
 * Implementation notes:
 * - `animationSpeed` is bounded to 0.25x..2.0x; add tests that assert UI clamping
 *   and that the `WallpaperEngine` respects these values under low-power mode.
 * - Low power mode should reduce update frequency and visual complexity; verify
 *   behavior on low-end devices to avoid jank and OOMs.
 * - ColorTheme.AUTO delegates to system night mode; include tests for older API
 *   levels that don't provide automatic dark mode.
 */
@Composable
fun WallpaperSettingsScreen() {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        // --- Animation ---
        SettingsHeader("ANIMATION & EFFECTS")

        SettingsGroupContainer {
            SettingsSubLabel("Animation Speed")

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Slider(
                    value = state.animationSpeed,
                    onValueChange = { viewModel.setAnimationSpeed(it) },
                    valueRange = 0.25f..2.0f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.2fx".format(state.animationSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(48.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ToggleListItemWithSubtitle(
                title = "Parallax Effect",
                subtitle = "Enable depth motion as wallpaper scrolls",
                checked = state.parallaxEffectEnabled,
                onCheckedChange = { viewModel.setParallaxEffectEnabled(it) },
            )
        }

        // --- Color Theme ---
        SettingsHeader("COLOR THEME")

        SettingsGroupContainer {
            SettingsSubLabel("Choose wallpaper color scheme")

            SegmentedControl(
                items = ColorTheme.entries,
                selectedItem = state.wallpaperColorTheme,
                onItemSelection = { viewModel.setWallpaperColorTheme(it) },
                labelProvider = { theme ->
                    when (theme) {
                        ColorTheme.AUTO -> "Auto"
                        ColorTheme.LIGHT -> "Light"
                        ColorTheme.DARK -> "Dark"
                    }
                },
            )
        }

        // --- Performance ---
        SettingsHeader("PERFORMANCE")

        SettingsGroupContainer {
            ToggleListItemWithSubtitle(
                title = "Low Power Mode",
                subtitle = "Reduce animations and effects to save battery",
                checked = state.lowPowerMode,
                onCheckedChange = { viewModel.setLowPowerMode(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
