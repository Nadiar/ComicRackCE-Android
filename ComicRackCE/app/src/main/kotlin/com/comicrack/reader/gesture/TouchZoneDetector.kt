package com.comicrack.reader.gesture

// DOCFIX: doc-only pass â€" 2026-01-15 (docfix/pilot-comments)

import androidx.compose.ui.unit.IntSize
import com.comicrack.data.preferences.ZoneSize

private const val ZONE_RATIO_DIVISOR = 2.0f

/**
 * TouchZone enum representing the 9 zones in a 3x3 grid.
 *
 * Grid layout:
 * ```
 * TL    TC    TR
 * ML    CC    MR
 * BL    BC    BR
 * ```
 *
 * Default action mappings:
 * - CENTER: Toggle overlays (menu)
 * - LEFT_SIDES (ML, TL, BL): Previous page
 * - RIGHT_SIDES (MR, TR, BR): Next page
 * - TOP_CORNERS (TL, TR): Open previous/next comic (mock for now)
 */
enum class TouchZone {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    MIDDLE_LEFT,
    CENTER,
    MIDDLE_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
}

/**
 * TouchZoneDetector - Maps screen coordinates to 3x3 grid zones.
 *
 * Production-ready gesture architecture that divides the screen into 9 configurable zones.
 * Each zone can be remapped to different actions (page turn, open menu, etc.).
 *
 * Notes:
 * - Consider making the grid configurable (e.g., different aspect ratios) for devices
 *   with unusual aspect ratios or to support accessibility preferences.
 * - Add unit tests that assert correct zone detection across representative screen sizes
 *   and for boundary coordinates to avoid off-by-one errors.
 *
 * Example:
 * ```kotlin
 * val detector = TouchZoneDetector()
 * val zone = detector.detectZone(x = 100f, y = 200f, screenSize = IntSize(1080, 1920))
 * when (zone) {
 *     TouchZone.CENTER -> toggleOverlays()
 *     TouchZone.MIDDLE_LEFT -> previousPage()
 *     TouchZone.MIDDLE_RIGHT -> nextPage()
 *     else -> {}
 * }
 * ```
 */
object TouchZoneDetector {
    /**
     * Detects which zone a tap occurred in based on screen coordinates.
     *
     * @param x X coordinate of the tap (0 to screenSize.width)
     * @param y Y coordinate of the tap (0 to screenSize.height)
     * @param screenSize Screen dimensions (width, height)
     * @param centerZoneSize The configurable size of the center zone
     * @return The TouchZone where the tap occurred
     */
    fun detectZone(
        x: Float,
        y: Float,
        screenSize: IntSize,
        centerZoneSize: com.comicrack.data.preferences.ZoneSize = com.comicrack.data.preferences.ZoneSize.MEDIUM,
    ): TouchZone {
        val width = screenSize.width.toFloat()
        val height = screenSize.height.toFloat()

        // Define center zone ratios
        val centerRatio = centerZoneSize.ratio

        // Side ratio (remaining space split in two)
        val sideRatio = (1.0f - centerRatio) / ZONE_RATIO_DIVISOR

        // Determine column (0 = left, 1 = center, 2 = right)
        val column =
            when {
                x < width * sideRatio -> 0
                x < width * (sideRatio + centerRatio) -> 1
                else -> 2
            }

        // Determine row (0 = top, 1 = middle, 2 = bottom)
        val row =
            when {
                y < height * sideRatio -> 0
                y < height * (sideRatio + centerRatio) -> 1
                else -> 2
            }

        // Map (row, column) to zone
        return when (Pair(row, column)) {
            Pair(0, 0) -> TouchZone.TOP_LEFT
            Pair(0, 1) -> TouchZone.TOP_CENTER
            Pair(0, 2) -> TouchZone.TOP_RIGHT
            Pair(1, 0) -> TouchZone.MIDDLE_LEFT
            Pair(1, 1) -> TouchZone.CENTER
            Pair(1, 2) -> TouchZone.MIDDLE_RIGHT
            Pair(2, 0) -> TouchZone.BOTTOM_LEFT
            Pair(2, 1) -> TouchZone.BOTTOM_CENTER
            Pair(2, 2) -> TouchZone.BOTTOM_RIGHT
            else -> TouchZone.CENTER // Fallback
        }
    }

    /**
     * Gets the bounds of a specific zone for UI rendering (e.g., in settings).
     *
     * @param zone The zone to get bounds for
     * @param screenSize Screen dimensions
     * @param centerZoneSize The configurable size of the center zone
     * @return A Rect with (left, top, right, bottom) coordinates
     */
    fun getZoneBounds(
        zone: TouchZone,
        screenSize: IntSize,
        centerZoneSize: com.comicrack.data.preferences.ZoneSize = com.comicrack.data.preferences.ZoneSize.MEDIUM,
    ): ZoneBounds {
        val width = screenSize.width.toFloat()
        val height = screenSize.height.toFloat()

        val centerRatio = centerZoneSize.ratio
        val sideRatio = (1.0f - centerRatio) / ZONE_RATIO_DIVISOR

        val (row, column) =
            when (zone) {
                TouchZone.TOP_LEFT -> Pair(0, 0)
                TouchZone.TOP_CENTER -> Pair(0, 1)
                TouchZone.TOP_RIGHT -> Pair(0, 2)
                TouchZone.MIDDLE_LEFT -> Pair(1, 0)
                TouchZone.CENTER -> Pair(1, 1)
                TouchZone.MIDDLE_RIGHT -> Pair(1, 2)
                TouchZone.BOTTOM_LEFT -> Pair(2, 0)
                TouchZone.BOTTOM_CENTER -> Pair(2, 1)
                TouchZone.BOTTOM_RIGHT -> Pair(2, 2)
            }

        val left =
            when (column) {
                0 -> 0f
                1 -> width * sideRatio
                else -> width * (sideRatio + centerRatio)
            }

        val right =
            when (column) {
                0 -> width * sideRatio
                1 -> width * (sideRatio + centerRatio)
                else -> width
            }

        val top =
            when (row) {
                0 -> 0f
                1 -> height * sideRatio
                else -> height * (sideRatio + centerRatio)
            }

        val bottom =
            when (row) {
                0 -> height * sideRatio
                1 -> height * (sideRatio + centerRatio)
                else -> height
            }

        return ZoneBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }
}

/**
 * Represents the bounds of a touch zone in pixels.
 */
data class ZoneBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}
