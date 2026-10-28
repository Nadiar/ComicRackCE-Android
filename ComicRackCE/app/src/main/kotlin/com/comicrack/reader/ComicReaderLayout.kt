package com.comicrack.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.comicrack.reader.image.ComicPageRequest
import com.comicrack.reader.state.ReadingState

/**
 * Main switchboard for the reader layout.
 *
 * Decides whether to show single-page, double-page, or webtoon (vertical scrolling)
 * layouts based on the provided [ReadingState]. The composable observes the
 * [ComicViewerViewModel] for UI updates such as the page background color and
 * animates transitions between pages using [AnimatedContent]. In Webtoon mode it
 * delegates rendering to [EndlessVerticalLayout].
 *
 * @param readingState current reading state containing visible page indices, layout mode, and direction
 * @param archivePath path to the archive file (e.g., CBZ/CBR) that supplies pages
 * @param viewModel the [ComicViewerViewModel] providing UI state and callbacks; used for color updates
 * @param modifier optional [Modifier] for layout decoration and sizing
 * @param onTap invoked when the reader area is tapped; receives the local offset of the tap
 * @param onLongPress invoked when the reader area is long-pressed; receives the local offset of the press
 * @param onPageChanged callback invoked when the visible page index changes
 */
@Composable
fun ComicReaderLayout(
    readingState: ReadingState,
    archivePath: String,
    // Add VM dependency (should be refactored to just pass state later)
    viewModel: com.comicrack.reader.ComicViewerViewModel,
    modifier: Modifier = Modifier,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onLongPress: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
) {
    val pageBackgroundColor by viewModel.pageBackgroundColor.collectAsState()

    // Smooth transition for background color
    val targetColor = pageBackgroundColor?.let { Color(it) } ?: Color.Black

    val animatedConfig by androidx.compose.animation.animateColorAsState(targetColor, label = "BgColor")

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(animatedConfig),
        // Dynamic background
        contentAlignment = Alignment.Center,
    ) {
        if (readingState.mode == com.comicrack.reader.state.ReadingMode.Webtoon) {
            EndlessVerticalLayout(
                readingState = readingState,
                archivePath = archivePath,
                onTap = onTap,
                onLongPress = onLongPress,
                onPageChanged = onPageChanged,
                viewModel = viewModel,
            )
        } else {
            // Use AnimatedContent for smooth transitions between pages
            AnimatedContent(
                targetState = readingState,
                transitionSpec = {
                    // Determine direction based on page indices
                    val initialPage = initialState.visiblePageIndices.firstOrNull() ?: 0
                    val targetPage = targetState.visiblePageIndices.firstOrNull() ?: 0
                    val isRtl = targetState.isRtl

                    val isMovingForward = targetPage > initialPage

                    // Slide In Animation
                    val enterTransition =
                        slideInHorizontally(animationSpec = tween(300)) { fullWidth ->
                            if (isMovingForward) {
                                if (isRtl) -fullWidth else fullWidth // Forward: Enter from Right (LTR) or Left (RTL)
                            } else {
                                if (isRtl) fullWidth else -fullWidth // Backward: Enter from Left (LTR) or Right (RTL)
                            }
                        } + fadeIn(animationSpec = tween(300))

                    // Slide Out Animation
                    val exitTransition =
                        slideOutHorizontally(animationSpec = tween(300)) { fullWidth ->
                            if (isMovingForward) {
                                // Forward: LTR old goes Left (-W), RTL old goes Right (+W)
                                if (isRtl) fullWidth else -fullWidth
                            } else {
                                // Backward: LTR old goes Right (+W), RTL old goes Left (-W)
                                if (isRtl) -fullWidth else fullWidth
                            }
                        } + fadeOut(animationSpec = tween(300))

                    enterTransition
                        .togetherWith(exitTransition)
                        .using(SizeTransform(clip = false))
                },
                label = "PageTransition",
            ) { targetState ->
                if (targetState.visiblePageIndices.isNotEmpty()) {
                    SinglePageLayout(
                        request = ComicPageRequest(archivePath, targetState.visiblePageIndices[0]),
                        viewModel = viewModel,
                        onTap = onTap,
                        onLongPress = onLongPress,
                    )
                }
            }
        }
    }
}

/**
 * Displays a single comic page using [ComicPageViewerArchive].
 *
 * This is a thin wrapper that maps a [ComicPageRequest] to the page viewer and
 * forwards user interactions and analyzed colors to the provided [ComicViewerViewModel].
 *
 * @param request describes which page of which archive to load
 * @param viewModel [ComicViewerViewModel] used to observe and update UI state (e.g., page background color)
 * @param onTap invoked when the page is tapped; receives the tap position in local coordinates
 * @param onLongPress invoked when the page is long-pressed; receives the press position in local coordinates
 */
@Composable
fun SinglePageLayout(
    request: ComicPageRequest,
    viewModel: com.comicrack.reader.ComicViewerViewModel, // Need VM for color update
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    onLongPress: (androidx.compose.ui.geometry.Offset) -> Unit,
) {
    // Reuse existing ComicPageViewerArchive
    ComicPageViewerArchive(
        request = request,
        viewModel = viewModel,
        // When analysis completes, update the VM
        onAnalyzeColor = { color -> viewModel.updatePageBackgroundColor(color) },
        onClick = onTap,
        onLongClick = onLongPress,
    )
}
