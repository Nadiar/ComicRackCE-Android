package com.comicrack.reader.gesture

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.ReaderTapAction
import kotlin.math.abs

/**
 * SwipeHandler - Detects and processes swipe gestures with optional RTL support.
 *
 * Features:
 * - Horizontal swipe detection (left/right)
 * - Vertical swipe detection (up/down)
 * - RTL swipe reversal (swaps left/right direction when reading right-to-left)
 * - Minimum distance threshold for swipe validation
 *
 * Notes:
 * - The `isRtl` parameter should be wired to the user's reading-direction
 *   preference (see `AppPreferencesRepository`) so that horizontal swipes
 *   map intuitively for RTL languages.
 * - Thresholds (distance/velocity) are conservative defaults and can be
 *   exposed if device-specific tuning is required.
 *
 * Testing:
 * - Add unit tests that validate detectSwipe returns expected directions for
 *   a range of delta values and that RTL flips left/right behavior correctly.
 *
 * Phase 9.2: Wire to readingDirection preference
 */
class SwipeHandler(
    private val minSwipeDistance: Float = 100f, // pixels
) {
    /**
     * Detect swipe direction from start and end positions.
     *
     * @param startX Swipe start X coordinate
     * @param startY Swipe start Y coordinate
     * @param endX Swipe end X coordinate
     * @param endY Swipe end Y coordinate
     * @param isRtl Whether reading direction is RTL
     * @return SwipeDirection or NONE if not a valid swipe
     */
    /**
     * Detect swipe direction from start and end positions.
     *
     * @param startX Swipe start X coordinate
     * @param startY Swipe start Y coordinate
     * @param endX Swipe end X coordinate
     * @param endY Swipe end Y coordinate
     * @param isRtl When true, flips left/right interpretation for RTL reading direction.
     * @return [SwipeDirection] or [SwipeDirection.NONE] if motion does not meet swipe thresholds.
     */
    fun detectSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        isRtl: Boolean = false,
    ): SwipeDirection {
        val deltaX = endX - startX
        val deltaY = endY - startY

        val absDeltaX = abs(deltaX)
        val absDeltaY = abs(deltaY)

        // Check if movement is large enough to be a swipe
        if (absDeltaX < minSwipeDistance && absDeltaY < minSwipeDistance) {
            return SwipeDirection.NONE
        }

        // Determine swipe direction based on dominant axis
        return if (absDeltaX > absDeltaY) {
            // Horizontal swipe
            if (deltaX > 0) {
                if (isRtl) SwipeDirection.LEFT else SwipeDirection.RIGHT
            } else {
                if (isRtl) SwipeDirection.RIGHT else SwipeDirection.LEFT
            }
        } else {
            // Vertical swipe
            if (deltaY > 0) SwipeDirection.DOWN else SwipeDirection.UP
        }
    }

    /**
     * Map swipe direction to reader action.
     *
     * Typical mapping:
     * - LEFT/RIGHT: Page navigation (direction depends on RTL)
     * - UP/DOWN: Menu toggles
     */
    fun swipeToAction(direction: SwipeDirection): ReaderTapAction =
        when (direction) {
            SwipeDirection.LEFT -> ReaderTapAction.PREVIOUS_PAGE
            SwipeDirection.RIGHT -> ReaderTapAction.NEXT_PAGE
            SwipeDirection.UP -> ReaderTapAction.MENU
            SwipeDirection.DOWN -> ReaderTapAction.MENU
            SwipeDirection.NONE -> ReaderTapAction.NONE
        }
}

/**
 * SwipeDirection - Detected swipe directions
 */
enum class SwipeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    NONE,
}
