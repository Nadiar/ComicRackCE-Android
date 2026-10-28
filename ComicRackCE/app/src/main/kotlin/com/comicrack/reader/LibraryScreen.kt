package com.comicrack.reader

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.library.ui.ComicCoverCard
import com.comicrack.library.ui.ComicDetailCard
import com.comicrack.library.ui.ComicStackCard
import timber.log.Timber
import kotlin.math.abs

/**
 * LibraryScreen â€” Compose library browser UI.
 *
 * Top-level screen composable that renders the library in multiple view modes
 * ("cover", "stack", "detail"), manages adaptive layout/zoom, and exposes
 * callbacks for user actions such as opening a comic or toggling read status.
 *
 * Important notes & guidance:
 * - Pinch-to-zoom updates the `zoomLevel` (range 0.5f..2.0f). The UI computes
 *   the effective column count by dividing `layoutColumns` by `zoomLevel` and
 *   clamping the result between 1 and 5; this provides a simple "zoom to change
 *   density" behavior (not a pixel-scale zoom).
 * - Gesture conflicts: current background `pointerInput` based transform detection
 *   may intercept taps intended for child cards. Prefer an `awaitPointerEventScope`
 *   based approach where the initial pointer target is inspected (background vs
 *   child). If the gesture begins on the background or with 2+ pointers, treat it
 *   as a transform; otherwise forward taps to children. See actionable TODOs below.
 * - Testing: add instrumentation tests that simulate multi-pointer transforms,
 *   assert scrolling is disabled during active pinch gestures, and validate that
 *   single taps on child cards are still handled.
 * - RTL: Ensure column/stack ordering and tap zones are tested under RTL layouts.
 *
 * @param comics List of `ComicBook` items to display. Use `filePath` or stable id
 *   as an identifier for operations such as toggling read status.
 * @param onComicClick Callback invoked with the selected `ComicBook` when opened.
 * @param viewMode Legacy view-mode parameter; prefer `currentViewMode` for authoritative state.
 * @param onViewModeChange Kept for compatibility; not used by this implementation.
 * @param filterMode Legacy filter-mode parameter; prefer `currentFilterMode`.
 * @param onFilterModeChange Kept for compatibility; not used by this implementation.
 * @param currentViewMode Active view mode: "cover"|"stack"|"detail".
 * @param onToggleViewMode Invoked when the view-mode toggle button is pressed.
 * @param currentFilterMode Currently applied filter mode string.
 * @param onToggleFilterMode Invoked when the filter-mode toggle button is pressed.
 * @param onToggleReadStatus Toggles read state for a comic identified by its filePath.
 * @param readComics Set of filePath strings considered read (used for visuals).
 * @param layoutColumns Preferred base column count (used with zoom to compute final columns).
 * @param gridSpacing Spacing between items (float, converted to dp where used).
 * @param cardElevation Card elevation in dp.
 * @param showTitles Whether to display titles on cards.
 * @param animateCards Whether to animate card appearance.
 *
 * Implementation notes:
 * - Keep renderers (e.g., `ComicCoverCard`) small and side-effect free. Move
 *   business logic to ViewModels or `Setting` validation where possible.
 * - When changing gesture handling, add integration tests for both
 *   single-pointer and multi-pointer interactions to prevent regressions.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    comics: List<ComicBook>,
    onComicClick: (ComicBook) -> Unit,
    _viewMode: String = "cover",
    _onViewModeChange: (String) -> Unit = {},
    _filterMode: String = "all",
    _onFilterModeChange: (String) -> Unit = {},
    currentViewMode: String = "cover",
    onToggleViewMode: () -> Unit = {},
    currentFilterMode: String = "all",
    onToggleFilterMode: () -> Unit = {},
    onToggleReadStatus: (String) -> Unit = {},
    readComics: Set<String> = emptySet(),
    // ===== Pull to Refresh =====
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    // ===== Phase 8 Task 4: Layout Preferences =====
    layoutColumns: Int = 2,
    gridSpacing: Float = 8.0f,
    cardElevation: Float = 4.0f,
    showTitles: Boolean = true,
    animateCards: Boolean = true,
) {
    // Zoom state for grid size (1.0 = 100%, range 0.5 to 2.0)
    val zoomLevel = remember { mutableStateOf(1.0f) }

    // Calculate grid columns based on layout preference and zoom level
    // Using layoutColumns preference as base, adjusted by zoom
    BoxWithConstraints {
        val screenWidth = maxWidth
        val spacingDp = gridSpacing.dp
        val padding = spacingDp
        // Calculate columns based on zoom level:
        // e.g., if layoutColumns=2: zoom 1.0 = 2 cols, zoom 0.5 = 3 cols (zoomed out), zoom 2.0 = 1 col (zoomed in)
        val columns = (layoutColumns / zoomLevel.value).toInt().coerceIn(1, 5)
        val itemWidth = (screenWidth - (padding * (columns + 1))) / columns

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
            ) {
            // Filter section
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = getFilterTitle(currentFilterMode),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "${comics.size} ${getFilterLabel(currentFilterMode).lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }

                    // Toggle buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Filter toggle button
                        Button(
                            onClick = onToggleFilterMode,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            modifier = Modifier.padding(0.dp),
                        ) {
                            Text(getFilterLabel(currentFilterMode), style = MaterialTheme.typography.labelSmall)
                        }

                        // View mode toggle button
                        Button(
                            onClick = onToggleViewMode,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                ),
                            modifier = Modifier.padding(0.dp),
                        ) {
                            Text(getViewModeLabel(currentViewMode), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Comics grid with zoom gesture
            if (comics.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "No Comics Found",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Add library paths to scan for comics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            } else {
                when (currentViewMode) {
                    "stack" ->
                        ComicStackView(
                            comics,
                            onComicClick,
                            columns,
                            itemWidth,
                            onToggleReadStatus,
                            readComics,
                            zoomLevel,
                            gridSpacing,
                            cardElevation,
                            showTitles,
                            animateCards,
                        )
                    "detail" ->
                        ComicDetailView(
                            comics,
                            onComicClick,
                            columns,
                            itemWidth,
                            onToggleReadStatus,
                            readComics,
                            zoomLevel,
                            gridSpacing,
                            cardElevation,
                            showTitles,
                            animateCards,
                        )
                    else ->
                        ComicCoverGridView(
                            comics,
                            onComicClick,
                            columns,
                            itemWidth,
                            onToggleReadStatus,
                            readComics,
                            zoomLevel,
                            gridSpacing,
                            cardElevation,
                            showTitles,
                            animateCards,
                        )
                }
            }
        }
    }
}
}

/**
 * ComicCoverGridView â€” Standard cover grid layout.
 *
 * Renders comic covers in a fixed-column grid. Handles pinch-to-zoom via the
 * provided `zoomLevel` state; scrolling is disabled while a pinch gesture is active.
 *
 * @param comics list of comics to render.
 * @param onComicClick callback invoked when a comic is clicked.
 * @param columns number of grid columns supplied by the parent.
 * @param itemWidth width provided to each card (parent-calculated).
 * @param onToggleReadStatus callback to toggle read state for a comic filePath.
 * @param readComics set of filePaths that are considered read.
 * @param zoomLevel mutable state containing current zoom factor (0.5..2.0).
 * @param gridSpacing spacing between items in dp (float value converted to dp in caller).
 * @param cardElevation elevation applied to each card.
 * @param showTitles whether to render title overlays on cards.
 * @param animateCards enable animated card appearance or layout changes.
 */
@Composable
fun ComicCoverGridView(
    comics: List<ComicBook>,
    onComicClick: (ComicBook) -> Unit,
    columns: Int = 3,
    itemWidth: Dp,
    onToggleReadStatus: (String) -> Unit = {},
    readComics: Set<String> = emptySet(),
    zoomLevel: MutableState<Float>,
    gridSpacing: Float = 8.0f,
    cardElevation: Float = 4.0f,
    showTitles: Boolean = true,
    animateCards: Boolean = true,
) {
    // Track if user is currently pinching (to block scrolling during pinch)
    val isPinching = remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        // TODO (cleanup phase - actionable): Gesture handling currently conflates
                        // child-card taps and parent grid transforms. To fix:
                        // 1) Use awaitPointerEventScope to detect pointer-count and source of initial
                        //    pointer down; only treat as a transform if the gesture begins on the
                        //    grid background or with two+ pointers.
                        // 2) When a transform starts, set `isPinching` and disable scrolling until
                        //    all pointers are lifted.
                        // If zoom changed significantly, we're pinching
                        if (abs(zoom - 1.0f) > 0.01f) {
                            isPinching.value = true
                        }
                        val newZoom = (zoomLevel.value * zoom).coerceIn(0.5f, 2.0f)
                        if (newZoom != zoomLevel.value) {
                            zoomLevel.value = newZoom
                        }
                    }
                },
        contentPadding = PaddingValues(gridSpacing.dp),
        horizontalArrangement = Arrangement.spacedBy(gridSpacing.dp),
        verticalArrangement = Arrangement.spacedBy(gridSpacing.dp),
        userScrollEnabled = !isPinching.value,
    ) {
        items(comics) { comic ->
            ComicCoverCard(
                comic = comic,
                onClick = {
                    Timber.tag("LibraryScreen").d("Comic clicked: ${comic.title}")
                    onComicClick(comic)
                },
                onLongPress = {
                    Timber.tag("LibraryScreen").d("Comic long pressed: ${comic.title}")
                    onToggleReadStatus(comic.filePath)
                },
                isRead = readComics.contains(comic.filePath),
                modifier = Modifier.width(itemWidth),
                elevation = cardElevation,
                _showTitle = showTitles,
                _animateCard = animateCards,
            )
        }
    }
}

/**
 * ComicStackView â€” Stack all issues of same series/volume.
 *
 * Groups comics by series (falling back to title when absent) and renders a
 * `ComicStackCard` per series showing a representative issue and the issue
 * count. Pinch-to-zoom is supported via `zoomLevel`; scrolling is disabled while
 * a pinch gesture is active to prevent accidental jumps.
 *
 * @param comics list of `ComicBook` items to group and display.
 * @param onComicClick invoked with a representative `ComicBook` when a series is opened.
 * @param columns base column count provided by the parent layout.
 * @param itemWidth width to supply to each stack card.
 * @param onToggleReadStatus toggles read state for a comic identified by its `filePath`.
 * @param readComics set of `filePath` strings that are considered read.
 * @param zoomLevel mutable state containing the current zoom factor (0.5..2.0).
 * @param gridSpacing spacing between grid items in dp (float value converted to dp by the caller).
 * @param cardElevation elevation applied to the stack cards in dp.
 * @param showTitles whether to render titles on the stack cards.
 * @param animateCards whether to animate card appearance and layout changes.
 */
@Composable
fun ComicStackView(
    comics: List<ComicBook>,
    onComicClick: (ComicBook) -> Unit,
    columns: Int = 3,
    itemWidth: Dp,
    onToggleReadStatus: (String) -> Unit = {},
    readComics: Set<String> = emptySet(),
    zoomLevel: MutableState<Float>,
    _gridSpacing: Float = 8.0f,
    _cardElevation: Float = 4.0f,
    _showTitles: Boolean = true,
    _animateCards: Boolean = true,
) {
    // Track if user is currently pinching (to block scrolling during pinch)
    val isPinching = remember { mutableStateOf(false) }

    // Group comics by series so stacks present multiple issues of the same series together.
    val groupedComics = comics.groupBy { it.series.ifEmpty { it.title } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        // TODO (cleanup phase - actionable): Gesture handling currently conflates
                        // child-card taps and parent grid transforms. To fix:
                        // 1) Use awaitPointerEventScope to detect pointer-count and source of initial
                        //    pointer down; only treat as a transform if the gesture begins on the
                        //    grid background or with two+ pointers.
                        // 2) When a transform starts, set `isPinching` and disable scrolling until
                        //    all pointers are lifted.
                        // If zoom changed significantly, we're pinching
                        if (abs(zoom - 1.0f) > 0.01f) {
                            isPinching.value = true
                        }
                        val newZoom = (zoomLevel.value * zoom).coerceIn(0.5f, 2.0f)
                        if (newZoom != zoomLevel.value) {
                            zoomLevel.value = newZoom
                        }
                    }
                },
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = !isPinching.value,
    ) {
        items(groupedComics.size) { index ->
            val (series, seriesComics) = groupedComics.entries.toList()[index]
            // Check if all comics in series are read
            val allRead = seriesComics.all { readComics.contains(it.filePath) }

            ComicStackCard(
                seriesName = series,
                comicCount = seriesComics.size,
                seriesComics = seriesComics,
                onClick = {
                    Timber.tag("LibraryScreen").d("Stack clicked: $series (${seriesComics.size} issues)")
                    onComicClick(seriesComics.first())
                },
                onLongPress = {
                    Timber.tag("LibraryScreen").d("Stack long pressed: $series")
                    // Toggle read status for first comic as series representative
                    onToggleReadStatus(seriesComics.first().filePath)
                },
                isRead = allRead,
                modifier = Modifier.width(itemWidth),
            )
        }
    }
}

/**
 * ComicDetailView â€” Detailed list view with metadata.
 *
 * Renders one comic per row with a cover thumbnail and metadata. The `columns`
 * parameter is preserved for API symmetry but is not used for linear detail rows.
 * Pinch-to-zoom adjusts an effective item size by mutating `zoomLevel` and scrolling
 * is disabled while a pinch is active to prevent accidental page jumps.
 *
 * @param comics list of comics to render.
 * @param onComicClick called when a row is clicked.
 * @param columns intended column count (unused for linear detail rows but kept
 *        for consistent API across view components).
 * @param itemWidth width guidance from parent; may be ignored for detail rows.
 * @param onToggleReadStatus callback to toggle read state for a comic filePath.
 * @param readComics set of filePath that are considered read.
 * @param zoomLevel mutable float state containing the current zoom factor.
 * @param gridSpacing spacing used between rows.
 * @param cardElevation elevation passed to the detail cards.
 * @param showTitles whether to display title text when applicable.
 * @param animateCards whether to animate card transitions.
 */
@Composable
fun ComicDetailView(
    comics: List<ComicBook>,
    onComicClick: (ComicBook) -> Unit,
    _columns: Int = 3,
    _itemWidth: Dp,
    onToggleReadStatus: (String) -> Unit = {},
    readComics: Set<String> = emptySet(),
    zoomLevel: MutableState<Float>,
    _gridSpacing: Float = 8.0f,
    _cardElevation: Float = 4.0f,
    _showTitles: Boolean = true,
    _animateCards: Boolean = true,
) {
    // Track if user is currently pinching (to block scrolling during pinch)
    val isPinching = remember { mutableStateOf(false) }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        // TODO (cleanup phase - actionable): Gesture handling currently conflates
                        // child-card taps and parent list transforms. Actionable plan:
                        // 1) Replace with `awaitPointerEventScope` and perform initial hit testing
                        //    to detect whether pointers start on a child row or on the list background.
                        // 2) Only treat as a transform when it originates off-card or with 2+ pointers.
                        // 3) When transform starts, set `isPinching.value = true` and disable
                        //    scrolling; clear the flag when all pointers are lifted.
                        // 4) Add tests that assert single taps still propagate to child rows and
                        //    that scrolling is disabled during active pinches.
                        if (abs(zoom - 1.0f) > 0.01f) {
                            isPinching.value = true
                        }
                        val newZoom = (zoomLevel.value * zoom).coerceIn(0.5f, 2.0f)
                        if (newZoom != zoomLevel.value) {
                            zoomLevel.value = newZoom
                        }
                    }
                },
        userScrollEnabled = !isPinching.value,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(comics) { comic ->
            ComicDetailCard(
                comic = comic,
                onClick = {
                    Timber.tag("LibraryScreen").d("Detail clicked: ${comic.title}")
                    onComicClick(comic)
                },
                onLongPress = {
                    Timber.tag("LibraryScreen").d("Detail long pressed: ${comic.title}")
                    onToggleReadStatus(comic.filePath)
                },
                isRead = readComics.contains(comic.filePath),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Returns the display title for a given filter mode.
 *
 * Accepts standard filter modes: "all", "unread", "read", "reading". Unknown
 * modes fall back to "All comics".
 *
 * @param filterMode filter mode identifier.
 * @return human-readable title string for the filter header.
 */
private fun getFilterTitle(filterMode: String): String =
    when (filterMode) {
        "all" -> "All comics"
        "unread" -> "Unread comics"
        "read" -> "Read comics"
        "reading" -> "Currently reading"
        else -> "All comics"
    }

/**
 * Returns the compact label used for the filter toggle button.
 *
 * Recognizes "all", "unread", "read", and "reading"; unknown values return "All".
 *
 * @param filterMode filter mode identifier.
 * @return compact label string suitable for a button.
 */
private fun getFilterLabel(filterMode: String): String =
    when (filterMode) {
        "all" -> "All"
        "unread" -> "Unread"
        "read" -> "Read"
        "reading" -> "Reading"
        else -> "All"
    }

/**
 * Return a short label for the view-mode toggle button.
 *
 * @param viewMode one of "cover", "stack", "detail"; unknown values default to "Cover".
 * @return compact label for the view-mode control.
 */
private fun getViewModeLabel(viewMode: String): String =
    when (viewMode) {
        "cover" -> "Cover"
        "stack" -> "Stack"
        "detail" -> "Detail"
        else -> "Cover"
    }
