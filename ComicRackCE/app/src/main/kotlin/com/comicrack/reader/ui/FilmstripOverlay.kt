package com.comicrack.reader.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * FilmstripOverlay - Bottom overlay showing page thumbnails and navigation.
 *
 * Features:
 * - Horizontal scrollable list of page thumbnails (placeholders in Phase 2)
 * - Navigation arrows for quick access
 * - Current page indicator
 * - Close button
 * - Smooth animations
 *
 * Phase 2: Uses placeholder rectangles
 * Phase 4: Will integrate with thumbnail generation
 *
 * Testing & performance:
 * - Thumbnail generation should be cached and done off the UI thread; add tests
 *   to assert that long libraries do not block the UI while thumbnails are prepared.
 * - Add integration tests for scrolling and page selection to ensure smooth animation.
 *
 * @param isVisible Controls whether the filmstrip is displayed
 * @param totalPages Number of pages available in the current comic
 * @param currentPageIndex Current selected page index (zero-based)
 * @param onPageSelected Callback invoked when a thumbnail is tapped
 */
@Composable
fun FilmstripOverlay(
    isVisible: Boolean,
    totalPages: Int,
    currentPageIndex: Int,
    onPageSelected: (Int) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            modifier =
                modifier
                    .fillMaxWidth()
                    .fillMaxWidth(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onBackground,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
            ) {
                // Header with close button
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Pages: ${currentPageIndex + 1} / $totalPages",
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                shadow =
                                    Shadow(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        offset = Offset(2f, 2f),
                                        blurRadius = 4f,
                                    ),
                            ),
                        color = Color.White,
                        modifier =
                            Modifier
                                .background(Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Filmstrip",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                // Thumbnail strip with navigation
                FilmstripRow(
                    totalPages = totalPages,
                    currentPageIndex = currentPageIndex,
                    onPageSelected = onPageSelected,
                )
            }
        }
    }
}

/**
 * FilmstripRow - Horizontal scrollable list of page thumbnails.
 */
@Composable
private fun FilmstripRow(
    totalPages: Int,
    currentPageIndex: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, currentPageIndex - 2))
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(120.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Left navigation arrow
        IconButton(
            onClick = {
                coroutineScope.launch {
                    listState.animateScrollToItem(
                        maxOf(0, listState.firstVisibleItemIndex - 3),
                    )
                }
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Scroll Left",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Thumbnail list
        LazyRow(
            state = listState,
            modifier =
                Modifier
                    .weight(1f)
                    .height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(totalPages) { pageIndex ->
                ThumbnailFrame(
                    pageIndex = pageIndex,
                    isSelected = pageIndex == currentPageIndex,
                    onClick = { onPageSelected(pageIndex) },
                )
            }
        }

        // Right navigation arrow
        IconButton(
            onClick = {
                coroutineScope.launch {
                    listState.animateScrollToItem(
                        minOf(totalPages - 1, listState.firstVisibleItemIndex + 3),
                    )
                }
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Scroll Right",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * ThumbnailFrame - Individual page thumbnail (placeholder in Phase 2).
 *
 * Phase 2: Displays a colored rectangle with page number
 * Phase 4: Will display actual page bitmap thumbnail
 */
@Composable
private fun ThumbnailFrame(
    pageIndex: Int,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .size(width = 70.dp, height = 100.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { onClick() },
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (pageIndex + 1).toString(),
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
