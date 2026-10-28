package com.comicrack.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comicrack.reader.image.ComicPageRequest
import com.comicrack.reader.state.ReadingState

/**
 * Vertical "endless" layout tailored for webtoon-style comics.
 *
 * This composable presents pages in a single continuous vertical column
 * using [LazyColumn]. It keeps the visible page synchronized with
 * [ReadingState.currentAbsoluteIndex] and forwards observed page changes
 * to [onPageChanged]. The list state is initialized from [readingState]
 * and external updates to that state will programmatically scroll the
 * list to the requested index.
 *
 * Behavior summary:
 * - Initializes the internal lazy list state to [readingState.currentAbsoluteIndex].
 * - When [readingState.currentAbsoluteIndex] changes externally the layout
 *   will scroll to keep that page visible.
 * - Visible-item changes are observed and delivered via [onPageChanged].
 *
 * @param readingState current reading state containing the current absolute index
 *        and total pages; used to initialize and reconcile scroll position.
 * @param archivePath filesystem path to the comic archive used to request page images.
 * @param modifier optional [Modifier] applied to the root [LazyColumn].
 * @param onTap invoked with the tap [androidx.compose.ui.geometry.Offset]
 *        within a page; defaults to no-op.
 * @param onLongPress invoked with the long-press
 *        [androidx.compose.ui.geometry.Offset] within a page; defaults to no-op.
 * @param onPageChanged callback that receives the current visible page index when it changes.
 * @param viewModel the [ComicViewerViewModel] used by page viewers for actions and state.
 *
 * @see com.comicrack.reader.image.ComicPageRequest
 * @see com.comicrack.reader.ComicPageViewerArchive
 */
@Composable
fun EndlessVerticalLayout(
    readingState: ReadingState,
    archivePath: String,
    modifier: Modifier = Modifier,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onLongPress: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    viewModel: ComicViewerViewModel,
) {
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = readingState.currentAbsoluteIndex,
        )

    // Sync external state changes (e.g. from seekbar) to scroll position
    LaunchedEffect(readingState.currentAbsoluteIndex) {
        if (listState.firstVisibleItemIndex != readingState.currentAbsoluteIndex) {
            listState.scrollToItem(readingState.currentAbsoluteIndex)
        }
    }

    // Sync scroll changes back to ViewModel
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index != readingState.currentAbsoluteIndex) {
                    onPageChanged(index)
                }
            }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp), // Continuous
    ) {
        items(
            count = readingState.totalPages,
            key = { index -> "page_$index" },
        ) { index ->
            ComicPageViewerArchive(
                request = ComicPageRequest(archivePath, index),
                viewModel = viewModel,
                onClick = onTap,
                onLongClick = onLongPress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
