package com.comicrack.reader.ui

// Phase 2.4: Composable Decomposition - State Grouping Data Classes
// Extracts large state collections into reusable data classes

import androidx.compose.runtime.State
import com.comicrack.data.preferences.ReaderTapAction
import com.comicrack.data.preferences.ZoneSize

/**
 * Grouped tap zone states for reader screen.
 *
 * Extracts 27 individual tap zone flows into organized data class
 * for cleaner state collection and parameter passing to helper composables.
 *
 * **Pattern Benefits:**
 * - Reduces single-tap state collection from ~100 lines to ~5
 * - Improves Composable parameter clarity
 * - Enables reusable zone visualization helpers
 * - Single point of change for zone state management
 *
 * **Usage in Composables:**
 * ```kotlin
 * @Composable
 * fun ReaderScreen(viewModel: ComicViewerViewModel) {
 *     val tapZones = collectTapZoneStates(viewModel)
 *     ReaderOverlay(tapZones)
 *     TapZoneVisualizer(tapZones)
 * }
 * ```
 */
data class TapZoneStates(
    // Single tap zones
    val tapZoneTopLeft: State<ReaderTapAction>,
    val tapZoneTopCenter: State<ReaderTapAction>,
    val tapZoneTopRight: State<ReaderTapAction>,
    val tapZoneMiddleLeft: State<ReaderTapAction>,
    val tapZoneCenter: State<ReaderTapAction>,
    val tapZoneMiddleRight: State<ReaderTapAction>,
    val tapZoneBottomLeft: State<ReaderTapAction>,
    val tapZoneBottomCenter: State<ReaderTapAction>,
    val tapZoneBottomRight: State<ReaderTapAction>,

    // Long tap zones
    val longTapZoneTopLeft: State<ReaderTapAction>,
    val longTapZoneTopCenter: State<ReaderTapAction>,
    val longTapZoneTopRight: State<ReaderTapAction>,
    val longTapZoneMiddleLeft: State<ReaderTapAction>,
    val longTapZoneCenter: State<ReaderTapAction>,
    val longTapZoneMiddleRight: State<ReaderTapAction>,
    val longTapZoneBottomLeft: State<ReaderTapAction>,
    val longTapZoneBottomCenter: State<ReaderTapAction>,
    val longTapZoneBottomRight: State<ReaderTapAction>,

    // Double tap zones
    val doubleTapZoneTopLeft: State<ReaderTapAction>,
    val doubleTapZoneTopCenter: State<ReaderTapAction>,
    val doubleTapZoneTopRight: State<ReaderTapAction>,
    val doubleTapZoneMiddleLeft: State<ReaderTapAction>,
    val doubleTapZoneCenter: State<ReaderTapAction>,
    val doubleTapZoneMiddleRight: State<ReaderTapAction>,
    val doubleTapZoneBottomLeft: State<ReaderTapAction>,
    val doubleTapZoneBottomCenter: State<ReaderTapAction>,
    val doubleTapZoneBottomRight: State<ReaderTapAction>,

    // Center zone size
    val centerZoneSize: State<ZoneSize>,
) {
    /**
     * Get single tap action for a zone index (0-8)
     */
    fun getSingleTapAction(zoneIndex: Int): State<ReaderTapAction> = when (zoneIndex) {
        0 -> tapZoneTopLeft
        1 -> tapZoneTopCenter
        2 -> tapZoneTopRight
        3 -> tapZoneMiddleLeft
        4 -> tapZoneCenter
        5 -> tapZoneMiddleRight
        6 -> tapZoneBottomLeft
        7 -> tapZoneBottomCenter
        8 -> tapZoneBottomRight
        else -> tapZoneCenter
    }

    /**
     * Get long tap action for a zone index (0-8)
     */
    fun getLongTapAction(zoneIndex: Int): State<ReaderTapAction> = when (zoneIndex) {
        0 -> longTapZoneTopLeft
        1 -> longTapZoneTopCenter
        2 -> longTapZoneTopRight
        3 -> longTapZoneMiddleLeft
        4 -> longTapZoneCenter
        5 -> longTapZoneMiddleRight
        6 -> longTapZoneBottomLeft
        7 -> longTapZoneBottomCenter
        8 -> longTapZoneBottomRight
        else -> longTapZoneCenter
    }

    /**
     * Get double tap action for a zone index (0-8)
     */
    fun getDoubleTapAction(zoneIndex: Int): State<ReaderTapAction> = when (zoneIndex) {
        0 -> doubleTapZoneTopLeft
        1 -> doubleTapZoneTopCenter
        2 -> doubleTapZoneTopRight
        3 -> doubleTapZoneMiddleLeft
        4 -> doubleTapZoneCenter
        5 -> doubleTapZoneMiddleRight
        6 -> doubleTapZoneBottomLeft
        7 -> doubleTapZoneBottomCenter
        8 -> doubleTapZoneBottomRight
        else -> doubleTapZoneCenter
    }
}

/**
 * Grouped navigation panel state.
 *
 * Extracts navigation-related preferences and states into focused data class.
 *
 * **Pattern Benefits:**
 * - Reduces NavigationPanel Composable parameters
 * - Cleaner state management
 * - Improves readability
 */
data class NavigationPanelState(
    val tapForward: State<ReaderTapAction>,
    val tapBack: State<ReaderTapAction>,
    val tapNextBook: State<ReaderTapAction>,
    val tapPrevBook: State<ReaderTapAction>,
    val longTapThumbnail: State<ReaderTapAction>,
    val longTapForward: State<ReaderTapAction>,
    val longTapBack: State<ReaderTapAction>,
)

/**
 * Grouped reader display and visual state.
 *
 * Extracts brightness, fit mode, page state, and visual adjustments.
 *
 * **Pattern Benefits:**
 * - Cleaner PageDisplay Composable
 * - Single parameter for display configuration
 * - Improved state update efficiency
 */
data class ReaderDisplayState(
    val currentPageNumber: State<Int>,
    val totalPages: State<Int>,
    val fitMode: State<com.comicrack.data.preferences.FitMode>,
    val brightness: State<Float>,
    val brightnessEnabled: State<Boolean>,
    val saturation: State<Float>,
    val contrast: State<Float>,
    val sharpness: State<Float>,
    val dynamicBackground: State<Boolean>,
    val pageTransitionsEnabled: State<Boolean>,
    val readingDirection: State<com.comicrack.data.preferences.ReadingDirection>,
    val showPageNumbers: State<Boolean>,
)

/**
 * Grouped reader gesture and control state.
 *
 * Extracts all gesture-related preferences into single data class.
 *
 * **Pattern Benefits:**
 * - Centralizes gesture configuration
 * - Single point of change for touch handling
 * - Improves gesture detector integration
 */
data class ReaderGestureState(
    val tapNavigation: State<Boolean>,
    val doubleTapEnabled: State<Boolean>,
    val pinchZoomSensitivity: State<Float>,
    val hapticFeedback: State<Boolean>,
    val centerZoneSize: State<ZoneSize>,
    val horizontalSwipeEnabled: State<Boolean>,
    val rtlNavigationEnabled: State<Boolean>,
)

/**
 * Complete reader screen state bundle.
 *
 * Groups all sub-states for cleaner composable parameter passing.
 * Reduces main Composable parameter count from 50+ to 4.
 *
 * **Pattern Benefits:**
 * - Significantly reduces LongMethod violations
 * - Improves Composable function signature clarity
 * - Enables cleaner state collection helpers
 * - Single parameter for all reader states
 */
data class ComicReaderScreenState(
    val tapZones: TapZoneStates,
    val navigationPanel: NavigationPanelState,
    val display: ReaderDisplayState,
    val gestures: ReaderGestureState,
    val isMenuVisible: State<Boolean>,
    val showTapAreas: State<Boolean>,
    val currentComicTitle: State<String>,
    val isLoading: State<Boolean>,
)
