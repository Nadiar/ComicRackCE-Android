/**
 * Library cards and tile components (cover card, stack card, detail card).
 *
 * Notes:
 * - Cards should avoid heavy work during composition (no blocking IO).
 * - Use placeholders and graceful fallback when images or metadata are missing.
 */
package com.comicrack.library.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.reader.image.ComicCoverRequest
import timber.log.Timber

/**
 * Standard aspect ratio for comic book covers (width:height = 2:3 = 0.667).
 * Represents typical comic book dimensions (e.g., 6.625" × 10.25").
 */
private const val COMIC_COVER_ASPECT_RATIO = 0.667f

/**
 * ComicCoverCard â€" Individual comic display card.
 *
 * Shows a cover image, simple rating UI, and title/issue metadata. Clicking
 * opens the comic; long-press performs haptic feedback and invokes `onLongPress`
 * with the comic's `filePath` (used to toggle read state or show context actions).
 *
 * @param comic The [ComicBook] backing this card; `filePath` is used for image and identity.
 * @param onClick Callback for tap/open action.
 * @param onLongPress Callback that receives the comic `filePath` on long press (haptic feedback applied).
 * @param isRead Whether the comic is marked read (controls badge).
 * @param elevation Card elevation in dp.
 * @param showTitle Whether to show the text overlay/title.
 * @param animateCard Whether to animate entrance/layout changes for this card.
 */
@Composable
fun ComicCoverCard(
    comic: ComicBook,
    onClick: () -> Unit,
    onLongPress: (String) -> Unit = {},
    isRead: Boolean = false,
    modifier: Modifier = Modifier,
    elevation: Float = 4.0f,
    _showTitle: Boolean = true,
    _animateCard: Boolean = true,
) {
    val view = LocalView.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = {
                            Timber.tag("ComicCoverCard").d("Long press on ${comic.title}")
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongPress(comic.filePath)
                        },
                    )
                },
    ) {
        // Cover image card
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = elevation.dp,
                    pressedElevation = (elevation + 4.0f).dp,
                ),
            shape = MaterialTheme.shapes.large,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Cover image
                if (comic.filePath.isNotEmpty()) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(ComicCoverRequest(comic.filePath))
                                .crossfade(true)
                                .build(),
                        contentDescription = comic.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { state ->
                            Timber
                                .tag(
                                    "ComicCoverCard",
                                ).w("Failed to load image for ${comic.title}: ${state.result.throwable}")
                        },
                    )
                } else {
                    // Placeholder if no path
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "?",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Gradient overlay for text readability
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.3f),
                                                Color.Black.copy(alpha = 0.7f),
                                            ),
                                    ),
                            ).padding(vertical = 12.dp, horizontal = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Series Name
                        val seriesText = if (!comic.series.isNullOrEmpty()) comic.series else comic.title
                        Text(
                            text = seriesText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Issue Number / Metadata
                        val issueText = if (!comic.issueNumber.isNullOrEmpty()) "#${comic.issueNumber}" else ""
                        if (issueText.isNotEmpty()) {
                            Text(
                                text = issueText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Read badge
                ReadBadge(
                    isRead = isRead,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                )
            }
        }

        // Metadata section below card
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 4.dp),
        ) {
            // Star rating (5 empty stars placeholder)
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(5) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Star",
                        modifier =
                            Modifier
                                .size(14.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            // Title below - only show if different from series (avoid duplicate)
            // When series is null/empty, overlay already shows title, so skip here
            // When series == title, also skip to avoid duplicate
            val showTitleBelow = !comic.series.isNullOrEmpty() && comic.series != comic.title

            // DEBUG: Log title/series values to diagnose duplicate issue
            Timber.tag("ComicCoverCard").d(
                "Comic: title='${comic.title}', series='${comic.series}', showTitleBelow=$showTitleBelow"
            )

            if (showTitleBelow) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * ComicStackVisual â€” Shows up to 3 stacked/rotated comic covers for a compact visual
 * representation of a multi-issue series. Rotation and offsets are intentionally
 * subtle to keep the stacked image recognizable.
 *
 * Implementation note: shows up to three covers and uses a small rotation offsets
 * ([-3Â°, 0Â°, +3Â°]); keep this visual lightweight to avoid layout jank on large lists.
 */
@Composable
fun ComicStackVisual(
    comics: List<ComicBook>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(0.667f)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)),
    ) {
        // Show up to 3 comics stacked with rotation
        val visibleComics = comics.take(3)
        val rotations = listOf(-3f, 0f, 3f)
        val offsets =
            listOf(
                Pair((-4).dp, (-4).dp),
                Pair(0.dp, 0.dp),
                Pair(4.dp, 4.dp),
            )

        visibleComics.forEachIndexed { index, comic ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset(offsets[index].first, offsets[index].second)
                        .graphicsLayer(rotationZ = rotations[index])
                        .clip(RoundedCornerShape(4.dp)),
            ) {
                if (comic.filePath.isNotEmpty()) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(ComicCoverRequest(comic.filePath))
                                .crossfade(true)
                                .build(),
                        contentDescription = comic.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("?", style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
        }
    }
}

/**
 * ReadBadge â€” Shows a checkmark when comic is marked as read
 */
@Composable
fun ReadBadge(
    isRead: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isRead) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier =
                modifier
                    .size(32.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Read",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * ComicStackCard â€” Shows a series stack with issue count
 */
@Composable
fun ComicStackCard(
    seriesName: String,
    comicCount: Int,
    seriesComics: List<ComicBook>,
    onClick: () -> Unit,
    onLongPress: (String) -> Unit = {},
    isRead: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = {
                            Timber.tag("ComicStackCard").d("Long press on $seriesName")
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongPress(seriesComics.firstOrNull()?.filePath ?: "")
                        },
                    )
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            // Cover image box with stacked visual
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(COMIC_COVER_ASPECT_RATIO),
            ) {
                // Use ComicStackVisual to show stacked images
                ComicStackVisual(
                    comics = seriesComics,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)),
                )

                // Stack count badge
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                ) {
                    Text(
                        text = "$comicCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Read badge
                ReadBadge(
                    isRead = isRead,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                )
            }

            // Series name
            Text(
                text = seriesName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Issue count indicator
            Text(
                text = if (comicCount == 1) "1 issue" else "$comicCount issues",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * ComicDetailCard â€” Shows detailed metadata overlay
 */
@Composable
fun ComicDetailCard(
    comic: ComicBook,
    onClick: () -> Unit,
    onLongPress: (String) -> Unit = {},
    isRead: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = {
                            Timber.tag("ComicDetailCard").d("Long press on ${comic.title}")
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongPress(comic.filePath)
                        },
                    )
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cover image (left side)
            Box(
                modifier =
                    Modifier
                        .width(120.dp)
                        .aspectRatio(0.667f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clip(RoundedCornerShape(4.dp)),
            ) {
                if (comic.filePath.isNotEmpty()) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(ComicCoverRequest(comic.filePath))
                                .crossfade(true)
                                .build(),
                        contentDescription = comic.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { state ->
                            Timber
                                .tag(
                                    "ComicDetailCard",
                                ).w(state.result.throwable, "Failed to load image for ${comic.title}")
                        },
                    )
                }
            }

            // Details (right side)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Title with read badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = comic.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Read badge
                    ReadBadge(
                        isRead = isRead,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                // Series name
                if (!comic.series.isNullOrBlank()) {
                    Text(
                        text = comic.series,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
