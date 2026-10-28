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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.FitMode
import com.comicrack.data.preferences.ReaderMode
import com.comicrack.data.preferences.ReadingDirection
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.settings.composables.SettingsGroupContainer
import com.comicrack.settings.composables.SettingsHeader
import com.comicrack.settings.composables.SettingsSubLabel
import com.comicrack.settings.composables.ToggleListItemWithSubtitle
import com.comicrack.settings.ui.components.SegmentedControl

/**
 * Reading Settings Screen
 *
 * Features:
 * - Segmented Buttons (Divided) for Enum selections
 * - Sliders for continuous values
 */
@Composable
fun ReadingSettingsScreen() {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        // --- Reading Mode ---
        SettingsHeader("READING MODE")

        SettingsGroupContainer {
            SettingsSubLabel("Select how pages are displayed")

            SegmentedControl(
                items = ReaderMode.entries,
                selectedItem = state.readerMode,
                onItemSelection = { viewModel.setReaderMode(it) },
                labelProvider = { mode ->
                    when (mode) {
                        ReaderMode.PAGE -> "Page"
                        ReaderMode.SCROLL -> "Scroll"
                    }
                },
            )
        }

        // --- Page Fit ---
        Text(
            text = "PAGE FIT MODE",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        SettingsGroupContainer {
            Text(
                text = "How to fit page to screen",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Using Segmented Control with shorter labels for 4 items
            SegmentedControl(
                items = FitMode.entries,
                selectedItem = state.pageFitMode,
                onItemSelection = { viewModel.setPageFitMode(it) },
                labelProvider = { mode ->
                    when (mode) {
                        FitMode.FIT_WIDTH -> "Width"
                        FitMode.FIT_HEIGHT -> "Height"
                        FitMode.FIT_PAGE -> "Page"
                        FitMode.ORIGINAL_SIZE -> "Original"
                    }
                },
            )
        }

        // --- Reading Direction ---
        SettingsHeader("READING DIRECTION")

        SettingsGroupContainer {
            SettingsSubLabel("Page navigation direction")

            SegmentedControl(
                items = ReadingDirection.entries,
                selectedItem = state.readingDirection,
                onItemSelection = { viewModel.setReadingDirection(it) },
                labelProvider = { direction ->
                    when (direction) {
                        ReadingDirection.LTR -> "Left to Right"
                        ReadingDirection.RTL -> "Right to Left"
                    }
                },
            )
        }

        SettingsHeader("DISPLAY & BEHAVIOR")

        SettingsGroupContainer {
            // Brightness Control
            SettingsSubLabel("Brightness")

            ToggleListItemWithSubtitle(
                title = "Override System Brightness",
                subtitle = "Use app brightness in the Reader",
                checked = state.readerBrightnessEnabled,
                onCheckedChange = { viewModel.setReaderBrightnessEnabled(it) },
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Slider(
                    value = state.readerBrightness,
                    onValueChange = { viewModel.setReaderBrightness(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(state.readerBrightness * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Page Numbers
            ToggleListItemWithSubtitle(
                title = "Show Page Numbers",
                subtitle = "Overlay page number",
                checked = state.readerShowPageNumbers,
                onCheckedChange = { viewModel.setReaderShowPageNumbers(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Dynamic Background (New S3-03 Refinement)
            ToggleListItemWithSubtitle(
                title = "Dynamic Page Background",
                subtitle = "Adapt background color to page content",
                checked = state.readerDynamicBackground,
                onCheckedChange = { viewModel.setReaderDynamicBackground(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Page Transitions
            ToggleListItemWithSubtitle(
                title = "Page Transitions",
                subtitle = "Animate page turns",
                checked = state.pageTransitionsEnabled,
                onCheckedChange = { viewModel.setPageTransitionsEnabled(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Flash Cover
            ToggleListItemWithSubtitle(
                title = "Flash Cover",
                subtitle = "Show cover on open",
                checked = state.readerFlashCover,
                onCheckedChange = { viewModel.setReaderFlashCover(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Ask Rating
            ToggleListItemWithSubtitle(
                title = "Ask Rating",
                subtitle = "Dialog after finishing",
                checked = state.readerAskRating,
                onCheckedChange = { viewModel.setReaderAskRating(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Tap Feedback (Relocated from Controls for better context)
            ToggleListItemWithSubtitle(
                title = "Show Tap Feedbacks",
                subtitle = "Brief confirmation when tapping pages",
                checked = state.readerShowTapToasts,
                onCheckedChange = { viewModel.setReaderShowTapToasts(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ToggleListItemWithSubtitle(
                title = "Keep Screen On",
                subtitle = "Prevent timeout",
                checked = state.readerKeepScreenOn,
                onCheckedChange = { viewModel.setReaderKeepScreenOn(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
