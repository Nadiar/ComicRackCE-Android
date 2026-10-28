package com.comicrack.data.preferences

// DOCFIX: doc-only pass â€" 2026-01-15 (docfix/pilot-comments)

import androidx.compose.runtime.Immutable
import com.comicrack.data.preferences.AppTheme
import com.comicrack.data.preferences.ColorTheme
import com.comicrack.data.preferences.FitMode
import com.comicrack.data.preferences.LibraryViewMode
import com.comicrack.data.preferences.ReadingDirection
import com.comicrack.data.preferences.ReaderMode
import com.comicrack.data.preferences.ReaderTapAction
import com.comicrack.data.preferences.SortOrder
import com.comicrack.data.preferences.ZoneSize

/**
 * PreferencesUiState â€” Immutable snapshot of preferences for UI consumption.
 *
 * Phase 2: Domain layer representation of all user preferences exposed to the UI.
 * Typically provided by [PreferencesViewModel] as a [kotlinx.coroutines.flow.StateFlow].
 *
 * @property libraryViewMode The current library view mode (e.g. GRID, LIST).
 * @property librarySortOrder Sort order used for library entries.
 * @property autoScanLibrary Whether library auto-scan is enabled.
 * @property recursiveScan Whether library scans are performed recursively.
 * @property rememberReadingPosition Persist and restore last reading position.
 * @property useDirectFileAccess Use direct file access when supported.
 * @property readerMode Default reader mode (page / panel / etc.).
 * @property readerFlashCover Show cover flash when opening a comic.
 * @property readerAskRating Whether to prompt for rating after reading.
 * @property readerShowPageNumbers Show page numbers in reader UI.
 * @property readerKeepScreenOn Keep the screen on while reading.
 * @property readerBrightness Reader brightness value in range 0.0..1.0.
 * @property pageFitMode How pages are fit to the viewport.
 * @property pageTransitionsEnabled Enable page transition animations.
 * @property readingDirection Reading direction (LTR/RTL).
 * @property doublePageDisplay Display pages two-up when appropriate.
 * @property hapticFeedback Whether haptic feedback is enabled.
 * @property tapNavigation Whether tap-to-navigate is enabled.
 * @property doubleTapEnabled Whether double-tap is enabled in reader.
 * @property pinchZoomSensitivity Sensitivity multiplier for pinch-zoom.
 * @property tapZoneTopLeft..tapZoneBottomRight Actions for single tap zones.
 * @property longTapZoneTopLeft..longTapZoneBottomRight Actions for long-tap zones.
 * @property doubleTapZoneTopLeft..doubleTapZoneBottomRight Actions for double-tap zones.
 * @property navPanelTapForward..navPanelLongPrevBook Navigation panel tap settings.
 * @property keyboardUp..keyboardVolumeDown Hardware keyboard action mappings.
 * @property volumeButtonsEnabled Whether volume buttons are handled.
 * @property dPadEnabled Whether D-Pad navigation is enabled.
 * @property arrowKeysEnabled Whether arrow key navigation is enabled.
 * @property gamepadButtonsEnabled Whether gamepad buttons are enabled.
 * @property horizontalSwipeEnabled Horizontal swipe gesture enabled.
 * @property rtlNavigationEnabled RTL navigation mapping enabled.
 * @property showTapAreas Whether tap areas are shown for debugging.
 * @property animationSpeed Wallpaper animation speed multiplier.
 * @property parallaxEffectEnabled Wallpaper parallax effect enabled.
 * @property wallpaperColorTheme Wallpaper color theme setting.
 * @property lowPowerMode Low power mode for wallpapers and animations.
 * @property appTheme Application theme selection.
 * @property isLoading Whether preferences are currently loading.
 * @property error Optional error message describing a loading failure.
 */
@Immutable
data class PreferencesUiState(
    // Library Settings
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
    val librarySortOrder: SortOrder = SortOrder.TITLE,

    // General Settings
    val autoScanLibrary: Boolean = true,
    val recursiveScan: Boolean = true,
    val rememberReadingPosition: Boolean = true,
    val useDirectFileAccess: Boolean = false,

    // Reader Settings
    val readerMode: ReaderMode = ReaderMode.PAGE,
    val readerFlashCover: Boolean = true,
    val readerAskRating: Boolean = true,
    val readerShowPageNumbers: Boolean = true,
    val readerKeepScreenOn: Boolean = true,
    val readerBrightness: Float = 0.5f,

    // Reader Display Settings
    val pageFitMode: FitMode = FitMode.FIT_PAGE,
    val pageTransitionsEnabled: Boolean = true,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val doublePageDisplay: Boolean = false,

    // Controls Settings
    val hapticFeedback: Boolean = true,
    val tapNavigation: Boolean = true,
    val doubleTapEnabled: Boolean = true,
    val pinchZoomSensitivity: Float = 1.0f,

    // Tap Zone Controls (9 zones Ã— 3: single + long + double)
    val tapZoneTopLeft: ReaderTapAction = ReaderTapAction.PREVIOUS_PAGE,
    val tapZoneTopCenter: ReaderTapAction = ReaderTapAction.NONE,
    val tapZoneTopRight: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val tapZoneMiddleLeft: ReaderTapAction = ReaderTapAction.PREVIOUS_PAGE,
    val tapZoneCenter: ReaderTapAction = ReaderTapAction.MENU,
    val tapZoneMiddleRight: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val tapZoneBottomLeft: ReaderTapAction = ReaderTapAction.PREVIOUS_PAGE,
    val tapZoneBottomCenter: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val tapZoneBottomRight: ReaderTapAction = ReaderTapAction.NEXT_PAGE,

    val longTapZoneTopLeft: ReaderTapAction = ReaderTapAction.NONE,
    val longTapZoneTopCenter: ReaderTapAction = ReaderTapAction.GOTO_PAGE,
    val longTapZoneTopRight: ReaderTapAction = ReaderTapAction.NONE,
    val longTapZoneMiddleLeft: ReaderTapAction = ReaderTapAction.NONE,
    val longTapZoneCenter: ReaderTapAction = ReaderTapAction.SHOW_ZOOM,
    val longTapZoneMiddleRight: ReaderTapAction = ReaderTapAction.NONE,
    val longTapZoneBottomLeft: ReaderTapAction = ReaderTapAction.NONE,
    val longTapZoneBottomCenter: ReaderTapAction = ReaderTapAction.BOOKMARKS,
    val longTapZoneBottomRight: ReaderTapAction = ReaderTapAction.NONE,
    
    // Double Tap Zones
    val doubleTapZoneTopLeft: ReaderTapAction = ReaderTapAction.NONE,
    val doubleTapZoneTopCenter: ReaderTapAction = ReaderTapAction.NONE,
    val doubleTapZoneTopRight: ReaderTapAction = ReaderTapAction.NONE,
    val doubleTapZoneMiddleLeft: ReaderTapAction = ReaderTapAction.NONE,
    val doubleTapZoneCenter: ReaderTapAction = ReaderTapAction.NONE,
    val doubleTapZoneMiddleRight: ReaderTapAction = ReaderTapAction.NONE,
    val doubleTapZoneBottomLeft: ReaderTapAction = ReaderTapAction.NONE,
    val doubleTapZoneBottomCenter: ReaderTapAction = ReaderTapAction.NONE,
    val doubleTapZoneBottomRight: ReaderTapAction = ReaderTapAction.NONE,


    // Navigation Panel Controls (5 areas Ã— 2: single + long)
    val navPanelTapForward: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val navPanelTapBack: ReaderTapAction = ReaderTapAction.PREVIOUS_PAGE,
    val navPanelTapNextBook: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val navPanelTapPrevBook: ReaderTapAction = ReaderTapAction.PREVIOUS_PAGE,
    val navPanelLongThumbnail: ReaderTapAction = ReaderTapAction.CHANGE_PAGE_TYPE,
    val navPanelLongForward: ReaderTapAction = ReaderTapAction.GOTO_PAGE,
    val navPanelLongBack: ReaderTapAction = ReaderTapAction.FIRST_PAGE,
    val navPanelLongNextBook: ReaderTapAction = ReaderTapAction.NONE,
    val navPanelLongPrevBook: ReaderTapAction = ReaderTapAction.NONE,

    // Hardware Keyboard Controls (6 keys)
    val keyboardUp: ReaderTapAction = ReaderTapAction.PREVIOUS_PAGE,
    val keyboardDown: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val keyboardLeft: ReaderTapAction = ReaderTapAction.PREVIOUS_PAGE,
    val keyboardRight: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val keyboardVolumeUp: ReaderTapAction = ReaderTapAction.NONE,
    val keyboardVolumeDown: ReaderTapAction = ReaderTapAction.NONE,

    // Hardware Button Enabled/Disabled State
    val volumeButtonsEnabled: Boolean = true,
    val dPadEnabled: Boolean = true,
    val arrowKeysEnabled: Boolean = true,
    val gamepadButtonsEnabled: Boolean = true,

    // Helper Control Settings (3 booleans)
    val horizontalSwipeEnabled: Boolean = true,
    val rtlNavigationEnabled: Boolean = true,
    val showTapAreas: Boolean = false,

    // Wallpaper Settings
    val animationSpeed: Float = 1.0f,
    val parallaxEffectEnabled: Boolean = true,
    val wallpaperColorTheme: ColorTheme = ColorTheme.AUTO,
    val lowPowerMode: Boolean = false,

    // Theme Settings
    val appTheme: AppTheme = AppTheme.SYSTEM,

    // Loading/Error State
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * Whether the UI is in a valid, non-loading state.
     *
     * @return `true` if the state is ready (not loading and no error), `false` otherwise.
     */
    val isReady: Boolean
        get() = !isLoading && error == null

    /**
     * Phase 2.4++: Get a setting value by ID from the state.
     * 
     * Used by RenderCatalogSetting and SearchScreen to fetch current values
     * for dynamic rendering. Maps setting IDs to their property values.
     * 
     * @param settingId The setting ID from SettingsCatalog (e.g., "rtl_mode")
     * @return The current value, or null if setting ID is unknown
     */
    fun getSettingValue(settingId: String): Any? = when (settingId) {
        // General Settings
        "library_view_mode" -> libraryViewMode
        "library_sort_order" -> librarySortOrder
        "auto_scan_library" -> autoScanLibrary
        "recursive_scan" -> recursiveScan
        "remember_reading_position" -> rememberReadingPosition

        // Reading Settings
        "reader_mode" -> readerMode
        "reader_flash_cover" -> readerFlashCover
        "reader_ask_rating" -> readerAskRating
        "reader_show_page_numbers" -> readerShowPageNumbers
        "reader_keep_screen_on" -> readerKeepScreenOn
        "reader_brightness" -> readerBrightness
        "page_fit_mode" -> pageFitMode
        "page_transitions_enabled" -> pageTransitionsEnabled
        "reading_direction" -> readingDirection
        "double_page_display" -> doublePageDisplay

        // Controls Settings
        "haptic_feedback" -> hapticFeedback
        "tap_navigation" -> tapNavigation
        "double_tap_enabled" -> doubleTapEnabled
        "pinch_zoom_sensitivity" -> pinchZoomSensitivity
        "tap_zone_top_left" -> tapZoneTopLeft
        "tap_zone_top_center" -> tapZoneTopCenter
        "tap_zone_top_right" -> tapZoneTopRight
        "tap_zone_middle_left" -> tapZoneMiddleLeft
        "tap_zone_center" -> tapZoneCenter
        "tap_zone_middle_right" -> tapZoneMiddleRight
        "tap_zone_bottom_left" -> tapZoneBottomLeft
        "tap_zone_bottom_center" -> tapZoneBottomCenter
        "tap_zone_bottom_right" -> tapZoneBottomRight
        "long_tap_zone_top_left" -> longTapZoneTopLeft
        "long_tap_zone_top_center" -> longTapZoneTopCenter
        "long_tap_zone_top_right" -> longTapZoneTopRight
        "long_tap_zone_middle_left" -> longTapZoneMiddleLeft
        "long_tap_zone_center" -> longTapZoneCenter
        "long_tap_zone_middle_right" -> longTapZoneMiddleRight
        "long_tap_zone_bottom_left" -> longTapZoneBottomLeft
        "long_tap_zone_bottom_center" -> longTapZoneBottomCenter
        "long_tap_zone_bottom_right" -> longTapZoneBottomRight
        "double_tap_zone_top_left" -> doubleTapZoneTopLeft
        "double_tap_zone_top_center" -> doubleTapZoneTopCenter
        "double_tap_zone_top_right" -> doubleTapZoneTopRight
        "double_tap_zone_middle_left" -> doubleTapZoneMiddleLeft
        "double_tap_zone_center" -> doubleTapZoneCenter
        "double_tap_zone_middle_right" -> doubleTapZoneMiddleRight
        "double_tap_zone_bottom_left" -> doubleTapZoneBottomLeft
        "double_tap_zone_bottom_center" -> doubleTapZoneBottomCenter
        "double_tap_zone_bottom_right" -> doubleTapZoneBottomRight

        "nav_panel_tap_forward" -> navPanelTapForward
        "nav_panel_tap_back" -> navPanelTapBack
        "nav_panel_tap_next_book" -> navPanelTapNextBook
        "nav_panel_tap_prev_book" -> navPanelTapPrevBook
        "nav_panel_long_thumbnail" -> navPanelLongThumbnail
        "nav_panel_long_forward" -> navPanelLongForward
        "nav_panel_long_back" -> navPanelLongBack
        "nav_panel_long_next_book" -> navPanelLongNextBook
        "nav_panel_long_prev_book" -> navPanelLongPrevBook
        "keyboard_up" -> keyboardUp
        "keyboard_down" -> keyboardDown
        "keyboard_left" -> keyboardLeft
        "keyboard_right" -> keyboardRight
        "keyboard_volume_up" -> keyboardVolumeUp
        "keyboard_volume_down" -> keyboardVolumeDown
        "horizontal_swipe_enabled" -> horizontalSwipeEnabled
        "rtl_navigation_enabled" -> rtlNavigationEnabled
        "show_tap_areas" -> showTapAreas

        // Wallpaper Settings
        "animation_speed" -> animationSpeed
        "parallax_effect_enabled" -> parallaxEffectEnabled
        "wallpaper_color_theme" -> wallpaperColorTheme
        "low_power_mode" -> lowPowerMode

        // Theme Settings
        "app_theme" -> appTheme

        // Unknown setting
        else -> null
    }
}
