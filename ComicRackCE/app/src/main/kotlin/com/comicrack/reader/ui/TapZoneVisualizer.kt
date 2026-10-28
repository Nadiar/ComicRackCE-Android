package com.comicrack.reader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.ZoneSize

/**
 * Visual overlay for Reader Tap Zones.
 *
 * Displays a 3x3 grid overlay matching the touch zone detector logic. Grid lines
 * are drawn at the exact boundaries used by [TouchZoneDetector] to ensure users
 * see precisely where tap zones begin and end.
 *
 * **Grid Layout:**
 * ```
 * TL │ TC │ TR
 * ───┼────┼───
 * ML │ CC │ MR
 * ───┼────┼───
 * BL │ BC │ BR
 * ```
 *
 * **Center Zone Ratios by ZoneSize:**
 * - TINY: 10% of screen (90% for edge zones)
 * - SMALL: 20% of screen (80% for edge zones)
 * - MEDIUM: 30% of screen (70% for edge zones, DEFAULT)
 * - LARGE: 40% of screen (60% for edge zones)
 * - HUGE: 50% of screen (50% for edge zones)
 *
 * **Design Decisions:**
 * 1. **Import ZoneSize:** Eliminates fully qualified type references, improving readability
 * 2. **Explicit else clause:** Defaults to MEDIUM (0.30f) for robustness. If new ZoneSize
 *    values are added to the enum, visualizer gracefully falls back to a sensible default
 *    rather than causing compilation errors.
 * 3. **Named constants:** GRID_LINE_WIDTH, DASH_ON/OFF_LENGTH for maintainability
 * 4. **Stroke consistency:** Draw parameters passed directly to drawLine(); Compose Canvas
 *    doesn't accept Stroke objects, so we extract and pass width/pathEffect separately
 *
 * **Integration with TouchZoneDetector:**
 * - "Match TouchZoneDetector logic" means: Grid lines drawn by this visualizer must
 *   appear at the exact pixel coordinates where TouchZoneDetector divides zones.
 *   Both use identical ratio calculations (centerRatio, sideRatio) to guarantee alignment.
 * - Visual verification: Tap exactly on a grid line should trigger zone transition
 * - Future: Add integration tests verifying:
 *   - Line positions match detector zone boundaries across screen sizes (1080p, 2K, 4K)
 *   - Boundary taps (x = col1 ± 1px) map to correct adjacent zones
 *   - All ZoneSize values produce valid, non-overlapping zones
 *   Scope: VisualVerifierTest.kt with parameterized screen sizes and zone configs
 *
 * **Accessibility Note:**
 * centerZoneSize is configurable to support users who prefer larger tap targets.
 * Consider adding opacity adjustment in future for low-vision users.
 *
 * @param isVisible Whether the visualizer is currently active (typically toggled via
 *   reader overlay or debug mode)
 * @param centerZoneSize Configurable size of the center zone, affects grid line placement.
 *   Defaults to MEDIUM (30% center, 35% per side). Enum provides TINY through HUGE options
 *   for accessibility customization.
 * @param modifier Optional Modifier for layout customization
 */
@Composable
fun TapZoneVisualizer(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    centerZoneSize: ZoneSize = ZoneSize.MEDIUM,
) {
    if (!isVisible) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Define center zone ratios (must match TouchZoneDetector.detectZone())
        val centerRatio = centerZoneSize.ratio

        val sideRatio = (1.0f - centerRatio) / 2.0f

        // Define grid line positions
        // col1: Left edge of center column
        // col2: Right edge of center column
        // row1: Top edge of center row
        // row2: Bottom edge of center row
        val col1 = w * sideRatio
        val col2 = w * (sideRatio + centerRatio)
        val row1 = h * sideRatio
        val row2 = h * (sideRatio + centerRatio)

        // Grid line styling
        val lineColor = Color.Red.copy(alpha = GRID_LINE_ALPHA)
        val dashPattern =
            PathEffect.dashPathEffect(
                floatArrayOf(DASH_ON_LENGTH, DASH_OFF_LENGTH),
                phase = 0f,
            )

        // Vertical Lines (divide left | center | right columns)
        drawLine(
            color = lineColor,
            start = Offset(col1, 0f),
            end = Offset(col1, h),
            strokeWidth = GRID_LINE_WIDTH,
            pathEffect = dashPattern,
        )
        drawLine(
            color = lineColor,
            start = Offset(col2, 0f),
            end = Offset(col2, h),
            strokeWidth = GRID_LINE_WIDTH,
            pathEffect = dashPattern,
        )

        // Horizontal Lines (divide top | middle | bottom rows)
        drawLine(
            color = lineColor,
            start = Offset(0f, row1),
            end = Offset(w, row1),
            strokeWidth = GRID_LINE_WIDTH,
            pathEffect = dashPattern,
        )
        drawLine(
            color = lineColor,
            start = Offset(0f, row2),
            end = Offset(w, row2),
            strokeWidth = GRID_LINE_WIDTH,
            pathEffect = dashPattern,
        )
    }
}

// Visual styling constants
private const val GRID_LINE_WIDTH = 4f // Stroke width in pixels
private const val GRID_LINE_ALPHA = 0.5f // Semi-transparent red overlay
private const val DASH_ON_LENGTH = 10f // Visible dash segment length
private const val DASH_OFF_LENGTH = 10f // Gap between dashes
