package com.comicrack.reader.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * RTLNavigationButtons - Previous/Next buttons that respect reading direction.
 *
 * Features:
 * - Swaps button positions in RTL mode
 * - Maintains semantic meaning (previous goes backward, next goes forward)
 * - Icons automatically mirror in RTL (using automirrored icons)
 * - Semi-transparent background for overlay effect
 *
 * Phase 9.2: Wire to readingDirection preference
 *
 * Accessibility:
 * - Ensure buttons include `contentDescription` and have large touch targets. Add
 *   tests that validate correct semantic labels when swapped for RTL.
 *
 * Usage:
 * ```kotlin
 * RTLNavigationButtons(
 *     isRtl = readingDirection == ReadingDirection.RTL,
 *     onPreviousClick = { viewModel.previousPage() },
 *     onNextClick = { viewModel.nextPage() },
 *     modifier = Modifier.align(Alignment.BottomCenter)
 * )
 * ```
 */
@Composable
fun RTLNavigationButtons(
    isRtl: Boolean = false,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // In RTL, swap the button order visually but keep semantics correct
    val (leftAction, rightAction, leftLabel, rightLabel) =
        if (isRtl) {
            // RTL: Left button = Next (forward), Right button = Previous (backward)
            Quadruple(onNextClick, onPreviousClick, "Next", "Previous")
        } else {
            // LTR: Left button = Previous (backward), Right button = Next (forward)
            Quadruple(onPreviousClick, onNextClick, "Previous", "Next")
        }

    Row(
        modifier =
            modifier
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                ).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left button
        NavigationButton(
            label = leftLabel,
            isRtl = isRtl,
            pointsRight = if (isRtl) true else false,
            onClick = leftAction,
            modifier = Modifier.size(44.dp),
        )

        // Right button
        NavigationButton(
            label = rightLabel,
            isRtl = isRtl,
            pointsRight = if (isRtl) false else true,
            onClick = rightAction,
            modifier = Modifier.size(44.dp),
        )
    }
}

/**
 * Single navigation button with automatic icon direction.
 *
 * @param label Button label (for accessibility)
 * @param isRtl Whether layout is RTL
 * @param pointsRight Whether arrow should point right (vs left)
 * @param onClick Callback when button is clicked
 */
@Composable
fun NavigationButton(
    label: String,
    isRtl: Boolean = false,
    pointsRight: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector =
                if (pointsRight) {
                    Icons.AutoMirrored.Filled.ArrowForward
                } else {
                    Icons.AutoMirrored.Filled.ArrowBack
                },
            contentDescription = label,
            tint = Color.White,
            modifier =
                Modifier
                    .size(24.dp)
                    // Mirror icon horizontally if RTL and icon should point left
                    .graphicsLayer(
                        scaleX = if (isRtl && !pointsRight) -1f else 1f,
                    ),
        )
    }
}

/**
 * Simple data class for quadruple return value
 */
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
