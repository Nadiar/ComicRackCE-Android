package com.comicrack.settings.screens

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.data.preferences.UserPreferences
import com.comicrack.settings.catalog.Setting
import com.comicrack.settings.catalog.SettingCategory
import com.comicrack.settings.ui.components.RenderSettingWithRegistry

/**
 * Phase 2.3: Helper composable for rendering all catalog settings by category
 *
 * Simplifies page refactoring by centralizing catalog rendering.
 * Demonstrates the pattern for integrating SettingsCatalog into existing screens.
 *
 * NOTE: RenderSettingWithRegistry is used by `RenderCatalogSetting` to provide interactive
 * rendering today. Future improvements (Phase 3):
 * - Interactive previews and contextual help for complex settings
 * - Search/filtering of settings with tags
 * - Performance: lazy rendering for large catalogs and grouping optimizations
 */
@Composable
fun RenderCatalogSettingsByCategory(
    category: SettingCategory,
    viewModel: PreferencesViewModel,
    modifier: Modifier = Modifier,
) {
    // Get all settings for this category
    val catalog = viewModel.catalog
    val settingsInCategory = catalog.getSettingsByCategory(category)
    val preferences by viewModel.uiState.collectAsStateWithLifecycle()

    if (settingsInCategory.isEmpty()) {
        return
    }

    Column(modifier = modifier) {
        settingsInCategory.forEachIndexed { index, setting ->
            val currentValue = viewModel.getValueForSetting(setting, preferences)

            RenderSettingWithRegistry(
                setting = setting,
                value = currentValue,
                onValueChange = { newValue ->
                    viewModel.updateSetting(setting.id, newValue)
                },
            )
            if (index < settingsInCategory.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/**
 * Renders an individual catalog setting by resolving the current value from the ViewModel.
 */
@Composable
fun RenderCatalogSetting(
    setting: Setting,
    viewModel: PreferencesViewModel,
    modifier: Modifier = Modifier,
) {
    val preferences by viewModel.uiState.collectAsStateWithLifecycle()
    val currentValue = viewModel.getValueForSetting(setting, preferences)

    RenderSettingWithRegistry(
        setting = setting,
        value = currentValue,
        onValueChange = { newValue ->
            viewModel.updateSetting(setting.id, newValue)
        },
        modifier = modifier,
    )
}

/**
 * Render a single catalog setting
 *
 * Phase 3.0: FULLY INTERACTIVE UI using `RenderSettingWithRegistry`.
 *
 * This composable bridges the search/catalog UI with the rendering pipeline.
 * It fetches the current value from `PreferencesViewModel.uiState` and renders the
 * interactive component (Toggle, Slider, Dropdown, or Custom) with full interactivity.
 *
 * Implementation notes:
 * - Keep `RenderSettingWithRegistry` free of heavy side-effects â€” use the ViewModel
 *   to persist changes (as done by `viewModel.updateSetting`).
 * - Keep the ID -> state mapping in `getSettingValueFromState` synchronized with the
 *   `SettingsCatalog` definitions. Prefer generating this mapping from the catalog
 *   metadata if possible to avoid drift.
 *
 * @param setting The catalog `Setting` to render. It must include type hints and any UI metadata.
 * @param viewModel Preferences ViewModel providing current values and update methods.
 * @param modifier Optional UI modifier applied to the setting renderer.
 */

/**
 * Helper: Extract setting value from UserPreferences state by setting ID
 *
 * Maps setting catalog IDs to their corresponding properties in the preferences state.
 * Used by RenderCatalogSetting to fetch current values for interactive rendering.
 */
fun getSettingValueFromState(
    state: UserPreferences,
    settingId: String,
): Any? =
    when (settingId) {
        // General Settings
        "app_theme" -> state.appTheme
        "library_view_mode" -> state.libraryViewMode
        "library_sort_order" -> state.librarySortOrder
        "auto_scan_library" -> state.autoScanLibrary
        "recursive_scan" -> state.recursiveScan
        "remember_reading_position" -> state.rememberReadingPosition

        // Reading Settings
        "reader_mode" -> state.readerMode
        "page_fit_mode" -> state.pageFitMode
        "reading_direction" -> state.readingDirection
        "keep_screen_on" -> state.readerKeepScreenOn
        "brightness" -> state.readerBrightness
        "page_transitions" -> state.pageTransitionsEnabled
        "reader_saturation" -> state.readerSaturation * 100f
        "reader_sharpness" -> state.readerSharpness * 100f
        "reader_open_with_menu" -> state.readerOpenWithMenu
        "reader_show_tap_toasts" -> state.readerShowTapToasts

        // Controls Settings
        "haptic_feedback" -> state.hapticFeedback
        "tap_navigation" -> state.tapNavigation
        "double_tap_enabled" -> state.doubleTapEnabled
        "pinch_zoom_sensitivity" -> state.pinchZoomSensitivity
        "show_tap_areas" -> state.showTapAreas
        "rtl_navigation_enabled" -> state.rtlNavigationEnabled
        "horizontal_swipe_enabled" -> state.horizontalSwipeEnabled

        // Tap Zones
        "tap_zone_top_left" -> state.tapZoneTopLeft
        "tap_zone_top_center" -> state.tapZoneTopCenter
        "tap_zone_top_right" -> state.tapZoneTopRight
        "tap_zone_middle_left" -> state.tapZoneMiddleLeft
        "tap_zone_center" -> state.tapZoneCenter
        "tap_zone_middle_right" -> state.tapZoneMiddleRight
        "tap_zone_bottom_left" -> state.tapZoneBottomLeft
        "tap_zone_bottom_center" -> state.tapZoneBottomCenter
        "tap_zone_bottom_right" -> state.tapZoneBottomRight

        // Long Tap Zones
        "long_tap_zone_top_left" -> state.longTapZoneTopLeft
        "long_tap_zone_top_center" -> state.longTapZoneTopCenter
        "long_tap_zone_top_right" -> state.longTapZoneTopRight
        "long_tap_zone_middle_left" -> state.longTapZoneMiddleLeft
        "long_tap_zone_center" -> state.longTapZoneCenter
        "long_tap_zone_middle_right" -> state.longTapZoneMiddleRight
        "long_tap_zone_bottom_left" -> state.longTapZoneBottomLeft
        "long_tap_zone_bottom_center" -> state.longTapZoneBottomCenter
        "long_tap_zone_bottom_right" -> state.longTapZoneBottomRight

        // Wallpaper Settings
        "animation_speed" -> state.animationSpeed
        "parallax_effect" -> state.parallaxEffectEnabled
        "wallpaper_color_theme" -> state.wallpaperColorTheme
        "low_power_mode" -> state.lowPowerMode

        else -> null
    }
