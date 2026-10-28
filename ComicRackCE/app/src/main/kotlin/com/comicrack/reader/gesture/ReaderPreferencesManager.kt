package com.comicrack.reader.gesture

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.FitMode
import com.comicrack.data.preferences.ReaderMode
import com.comicrack.data.preferences.ReadingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Reader Preferences Manager â€” Centralizes observation and provision of all reader preferences
 *
 * Manages:
 * - Reading Settings (14 keys): Mode, fit, brightness, direction, page display, etc.
 * - Control Settings (4 keys): Haptic, tap navigation, zoom, swipe behavior
 * - Tap Zone Mappings (36 zones): Single tap + long tap actions for 18 zones
 * - Hardware Key Mappings (6 keys): D-Pad and Volume key actions
 *
 * Usage in Composable:
 * ```kotlin
 * val readerPrefs = ReaderPreferencesManager(AppPreferencesRepository)
 * val readingSettings = readerPrefs.readingSettingsComposable()
 * val controlSettings = readerPrefs.controlSettingsComposable()
 * ```
 *
 * Notes:
 * - Preference IDs and mapping should be treated as a stable schema; when adding
 *   new preferences, provide a migration plan and default values to avoid breaking
 *   existing user configurations.
 * - Add unit tests that verify the combined flows and composable helpers produce
 *   expected values when mock preferences are provided.
 */
class ReaderPreferencesManager(
    private val preferencesRepository: AppPreferencesRepository,
) {
    /**
     * Combined flow of all reading preferences
     * Returns a data class with all 14 reading setting values
     */
    fun readingSettingsFlow(): Flow<ReadingSettings> =
        combine<Any, ReadingSettings>(
            preferencesRepository.readerMode,
            preferencesRepository.pageFitMode,
            preferencesRepository.readerBrightness,
            preferencesRepository.readingDirection,
            preferencesRepository.readerShowPageNumbers,
            preferencesRepository.pageTransitionsEnabled,
            preferencesRepository.readerFlashCover,
            preferencesRepository.readerAskRating,
            preferencesRepository.readerKeepScreenOn,
            preferencesRepository.rememberReadingPosition,
            preferencesRepository.readerBrightnessEnabled,
        ) { values ->
            val readerMode = values[0] as ReaderMode
            val pageFit = values[1] as FitMode
            val brightness = values[2] as Float
            val direction = values[3] as ReadingDirection
            val showPages = values[4] as Boolean
            val pageTransitions = values[5] as Boolean
            val flashCover = values[6] as Boolean
            val askRating = values[7] as Boolean
            val keepOn = values[8] as Boolean
            val rememberPos = values[9] as Boolean
            val doublePage = false
            val brightnessEnabled = values[10] as Boolean

            ReadingSettings(
                readerMode = readerMode,
                pageFitMode = pageFit,
                brightness = brightness,
                brightnessEnabled = brightnessEnabled,
                readingDirection = direction,
                showPageNumbers = showPages,
                pageTransitionsEnabled = pageTransitions,
                flashCover = flashCover,
                askRating = askRating,
                keepScreenOn = keepOn,
                rememberReadingPosition = rememberPos,
                doublePageDisplay = doublePage,
            )
        }

    /**
     * Combined flow of all control preferences
     * Returns a data class with all 4 control setting values
     */
    fun controlSettingsFlow(): Flow<ControlSettings> =
        combine<Any, ControlSettings>(
            preferencesRepository.hapticFeedback,
            preferencesRepository.tapNavigation,
            preferencesRepository.doubleTapEnabled,
            preferencesRepository.pinchZoomSensitivity,
            preferencesRepository.showTapAreas,
            preferencesRepository.rtlNavigationEnabled,
            preferencesRepository.horizontalSwipeEnabled,
        ) { values ->
            val haptic = values[0] as Boolean
            val tapNav = values[1] as Boolean
            val doubleTap = values[2] as Boolean
            val zoomSens = values[3] as Float
            val showTaps = values[4] as Boolean
            val reverseTaps = values[5] as Boolean
            val horizSwipe = values[6] as Boolean

            ControlSettings(
                hapticFeedback = haptic,
                tapNavigation = tapNav,
                doubleTapEnabled = doubleTap,
                pinchZoomSensitivity = zoomSens,
                showTapAreas = showTaps,
                rtlNavigationEnabled = reverseTaps,
                horizontalSwipeEnabled = horizSwipe,
            )
        }

    /**
     * Get individual reading preference flows (for granular updates)
     */
    fun readerModeFlow() = preferencesRepository.readerMode

    fun pageFitModeFlow() = preferencesRepository.pageFitMode

    fun readerBrightnessFlow() = preferencesRepository.readerBrightness

    fun readingDirectionFlow() = preferencesRepository.readingDirection

    fun showPageNumbersFlow() = preferencesRepository.readerShowPageNumbers

    fun pageTransitionsEnabledFlow() = preferencesRepository.pageTransitionsEnabled

    fun readerFlashCoverFlow() = preferencesRepository.readerFlashCover

    fun readerAskRatingFlow() = preferencesRepository.readerAskRating

    fun readerKeepScreenOnFlow() = preferencesRepository.readerKeepScreenOn

    fun rememberReadingPositionFlow() = preferencesRepository.rememberReadingPosition

    fun doublePageDisplayFlow() = kotlinx.coroutines.flow.flowOf(false)

    /**
     * Get individual control preference flows
     */
    fun hapticFeedbackFlow() = preferencesRepository.hapticFeedback

    fun tapNavigationFlow() = preferencesRepository.tapNavigation

    fun doubleTapEnabledFlow() = preferencesRepository.doubleTapEnabled

    fun pinchZoomSensitivityFlow() = preferencesRepository.pinchZoomSensitivity

    fun showTapAreasFlow() = preferencesRepository.showTapAreas

    fun rtlNavigationEnabledFlow() = preferencesRepository.rtlNavigationEnabled

    fun horizontalSwipeEnabledFlow() = preferencesRepository.horizontalSwipeEnabled

    /**
     * Get tap zone action flows
     * Returns the ReaderTapAction for the given zone and type (single/long tap)
     */
    fun getTapZoneActionFlow(
        zoneIndex: Int,
        isLongTap: Boolean,
    ) = if (isLongTap) {
        when (zoneIndex) {
            0 -> preferencesRepository.longTapZoneTopLeft
            1 -> preferencesRepository.longTapZoneTopCenter
            2 -> preferencesRepository.longTapZoneTopRight
            3 -> preferencesRepository.longTapZoneMiddleLeft
            4 -> preferencesRepository.longTapZoneCenter
            5 -> preferencesRepository.longTapZoneMiddleRight
            6 -> preferencesRepository.longTapZoneBottomLeft
            7 -> preferencesRepository.longTapZoneBottomCenter
            8 -> preferencesRepository.longTapZoneBottomRight
            else -> preferencesRepository.longTapZoneTopLeft // default
        }
    } else {
        when (zoneIndex) {
            0 -> preferencesRepository.tapZoneTopLeft
            1 -> preferencesRepository.tapZoneTopCenter
            2 -> preferencesRepository.tapZoneTopRight
            3 -> preferencesRepository.tapZoneMiddleLeft
            4 -> preferencesRepository.tapZoneCenter
            5 -> preferencesRepository.tapZoneMiddleRight
            6 -> preferencesRepository.tapZoneBottomLeft
            7 -> preferencesRepository.tapZoneBottomCenter
            8 -> preferencesRepository.tapZoneBottomRight
            else -> preferencesRepository.tapZoneTopLeft // default
        }
    }

    /**
     * Composable helpers for collecting state at composition time
     */
    @Composable
    fun readingSettingsComposable(): ReadingSettings {
        val settings by readingSettingsFlow().collectAsState(
            initial = ReadingSettings(),
        )
        return settings
    }

    @Composable
    fun controlSettingsComposable(): ControlSettings {
        val settings by controlSettingsFlow().collectAsState(
            initial = ControlSettings(),
        )
        return settings
    }

    @Composable
    fun readerModeComposable() =
        preferencesRepository.readerMode
            .collectAsState(
                initial = ReaderMode.PAGE,
            ).value

    @Composable
    fun pageFitModeComposable() =
        preferencesRepository.pageFitMode
            .collectAsState(
                initial = FitMode.FIT_WIDTH,
            ).value

    @Composable
    fun readerBrightnessComposable() =
        preferencesRepository.readerBrightness
            .collectAsState(
                initial = 1.0f,
            ).value

    @Composable
    fun readingDirectionComposable() =
        preferencesRepository.readingDirection
            .collectAsState(
                initial = ReadingDirection.LTR,
            ).value

    @Composable
    fun showPageNumbersComposable() =
        preferencesRepository.readerShowPageNumbers
            .collectAsState(
                initial = true,
            ).value

    @Composable
    fun tapNavigationComposable() =
        preferencesRepository.tapNavigation
            .collectAsState(
                initial = true,
            ).value

    @Composable
    fun hapticFeedbackComposable() =
        preferencesRepository.hapticFeedback
            .collectAsState(
                initial = true,
            ).value

    @Composable
    fun rtlNavigationEnabledComposable() =
        preferencesRepository.rtlNavigationEnabled
            .collectAsState(
                initial = false,
            ).value
}

/**
 * Data class for reading preferences (14 settings)
 */
data class ReadingSettings(
    val readerMode: ReaderMode = ReaderMode.PAGE,
    val pageFitMode: FitMode = FitMode.FIT_WIDTH,
    val brightness: Float = 1.0f,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val showPageNumbers: Boolean = true,
    val pageTransitionsEnabled: Boolean = true,
    val flashCover: Boolean = false,
    val askRating: Boolean = false,
    val keepScreenOn: Boolean = false,
    val rememberReadingPosition: Boolean = true,
    val doublePageDisplay: Boolean = false,
    val brightnessEnabled: Boolean = true,
)

/**
 * Data class for control preferences (7 settings)
 */
data class ControlSettings(
    val hapticFeedback: Boolean = true,
    val tapNavigation: Boolean = true,
    val doubleTapEnabled: Boolean = true,
    val pinchZoomSensitivity: Float = 1.0f,
    val showTapAreas: Boolean = false,
    val rtlNavigationEnabled: Boolean = false,
    val horizontalSwipeEnabled: Boolean = true,
)
