package com.comicrack.reader.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ReaderPageNumberDisplay - Show current page number in a semi-transparent overlay.
 *
 * Features:
 * - Position: Bottom-right corner (LTR) or Bottom-left corner (RTL)
 * - Format: "Current / Total" (e.g., "42 / 185")
 * - Semi-transparent background for readability over page content
 * - Subtle styling to not interfere with reading
 * - Automatic layout reversal in RTL mode
 *
 * Accessibility & testing:
 * - Ensure text is accessible to screen readers and color-contrast remains sufficient
 *   over a variety of page content (add image overlay tests).
 * - Consider animating visibility changes with low-motion accessibility in mind.
 *
 * Phase 9.1: Wire to readerShowPageNumbers preference
 * Phase 9.2: RTL layout support
 *
 * @param currentPage Zero-based current page index
 * @param totalPages Total number of pages in the comic
 * @param isVisible Whether the overlay should be visible
 * @param isRtl When true, display order is reversed and position flips to left
 */
@Composable
fun ReaderPageNumberDisplay(
    currentPage: Int,
    totalPages: Int,
    isVisible: Boolean = true,
    isRtl: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!isVisible || totalPages == 0) {
        return
    }

    Box(
        modifier =
            modifier
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    if (isRtl) {
                        "$totalPages / ${currentPage + 1}" // Reverse display in RTL
                    } else {
                        "${currentPage + 1} / $totalPages"
                    },
                color = Color.White,
                fontSize = 14.sp,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
