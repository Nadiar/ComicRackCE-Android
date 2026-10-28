package com.comicrack.reader.gesture

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.ReaderTapAction

/**
 * Reader Tap Action Handler â€” high-level executor for reader tap actions.
 *
 * This class delegates actual action implementations to [ReaderActionProcessor]
 * and adds orchestration concerns such as optional haptic feedback. It intentionally
 * keeps higher-level decisions (which action to execute) separate from the implementation
 * so unit tests can verify mapping logic independently.
 *
 * Provides:
 * - Tap action execution for all ReaderTapAction types
 * - Optional haptic feedback orchestration
 * - Thin integration point for analytics/debugging
 *
 * Testing:
 * - Unit tests should assert mapping from `ReaderTapAction` to `ReaderActionProcessor`
 *   calls and verify haptic/enabled flags without requiring UI instrumentation.
 *
 * Usage:
 * ```kotlin
 * val handler = ReaderTapActionHandler(processor)
 * handler.execute(action, currentPageIndex, totalPages, hapticEnabled = true)
 * ```
 */
class ReaderTapActionHandler(
    private val processor: com.comicrack.reader.action.ReaderActionProcessor,
) {
    /**
     * Execute a tap action with optional haptic feedback.
     * The method forwards to ReaderActionProcessor, which performs the concrete change
     * (e.g., page navigation, menu toggle).
     *
     * @param action The ReaderTapAction to execute
     * @param currentPageIndex Current page (0-based) - kept for compatibility / logging
     * @param totalPages Total pages in comic - kept for compatibility
     * @param hapticEnabled Whether haptic feedback should be requested
     */
    fun execute(
        action: ReaderTapAction,
        _currentPageIndex: Int,
        _totalPages: Int,
        hapticEnabled: Boolean = false,
    ) {
        processor.processAction(action, hapticEnabled)
    }
}

class TapZoneActionMapper(
    private val preferencesRepository: AppPreferencesRepository,
) {
    /**
     * Get tap action for safe usage (returns default for CENTER which never varies)
     * For configurable zones, this is best used in a Composable via getTapActionComposable
     *
     * Note: CENTER is intentionally hard-coded to MENU to ensure consistent UX regardless
     * of user preferences; other zones are configurable via DataStore preferences.
     */
    fun getTapActionDefault(
        zone: TouchZone,
        _isLongTap: Boolean,
    ): ReaderTapAction =
        when (zone) {
            TouchZone.CENTER -> {
                // Center always toggles menu, not customizable via preferences
                ReaderTapAction.MENU
            }
            // For other zones, return a sensible default (they should be overridden in Composable)
            else -> ReaderTapAction.NONE
        }

    /**
     * Get composable version of tap action for safe state collection in Compose
     */
    @Composable
    fun getTapActionComposable(
        zone: TouchZone,
        isLongTap: Boolean,
    ): ReaderTapAction {
        val action =
            when (zone) {
                TouchZone.TOP_LEFT -> {
                    if (isLongTap) {
                        preferencesRepository.longTapZoneTopLeft
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    } else {
                        preferencesRepository.tapZoneTopLeft
                            .collectAsState(
                                ReaderTapAction.NEXT_PAGE,
                            ).value
                    }
                }
                TouchZone.TOP_CENTER -> {
                    if (isLongTap) {
                        preferencesRepository.longTapZoneTopCenter
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    } else {
                        preferencesRepository.tapZoneTopCenter
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    }
                }
                TouchZone.TOP_RIGHT -> {
                    if (isLongTap) {
                        preferencesRepository.longTapZoneTopRight
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    } else {
                        preferencesRepository.tapZoneTopRight
                            .collectAsState(
                                ReaderTapAction.NEXT_PAGE,
                            ).value
                    }
                }
                TouchZone.MIDDLE_LEFT -> {
                    if (isLongTap) {
                        preferencesRepository.longTapZoneMiddleLeft
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    } else {
                        preferencesRepository.tapZoneMiddleLeft
                            .collectAsState(
                                ReaderTapAction.PREVIOUS_PAGE,
                            ).value
                    }
                }
                TouchZone.CENTER -> {
                    // Center always toggles menu, not customizable via preferences
                    ReaderTapAction.MENU
                }
                TouchZone.MIDDLE_RIGHT -> {
                    if (isLongTap) {
                        preferencesRepository.longTapZoneMiddleRight
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    } else {
                        preferencesRepository.tapZoneMiddleRight
                            .collectAsState(
                                ReaderTapAction.NEXT_PAGE,
                            ).value
                    }
                }
                TouchZone.BOTTOM_LEFT -> {
                    if (isLongTap) {
                        preferencesRepository.longTapZoneBottomLeft
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    } else {
                        preferencesRepository.tapZoneBottomLeft
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    }
                }
                TouchZone.BOTTOM_CENTER -> {
                    if (isLongTap) {
                        preferencesRepository.longTapZoneBottomCenter
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    } else {
                        preferencesRepository.tapZoneBottomCenter
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    }
                }
                TouchZone.BOTTOM_RIGHT -> {
                    if (isLongTap) {
                        preferencesRepository.longTapZoneBottomRight
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    } else {
                        preferencesRepository.tapZoneBottomRight
                            .collectAsState(
                                ReaderTapAction.NONE,
                            ).value
                    }
                }
            }
        return action
    }
}
