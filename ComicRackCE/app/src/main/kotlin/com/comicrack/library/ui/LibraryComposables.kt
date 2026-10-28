/**
 * Library UI composables: compact, reusable pieces used by library screens.
 *
 * Guidelines:
 * - Keep composables side-effect free; move data-fetching or persistence to ViewModels.
 * - Add small, focused tests for layout and text visibility (RTL and density variations).
 */
package com.comicrack.library.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Comic Detail Card - Displays a comic book entry in the library list
 *
 * Layout (Row):
 * - Left: Cover image (2:3 aspect ratio, ~90dp width)
 * - Right: Column with metadata and description
 *   - Title (Series Name) - Bold, single line
 *   - Label (Volume/Issue) - Small, gray
 *   - Metadata (Writer/Artist) - Small, gray
 *   - Description - 3 lines max
 */
@Composable
fun ComicDetailCard(
    title: String,
    volumeNumber: String? = null,
    issueNumber: String? = null,
    writer: String? = null,
    artist: String? = null,
    description: String? = null,
    coverImageUrl: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            // Cover Image
            Box(
                modifier =
                    Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (coverImageUrl != null) {
                    AsyncImage(
                        model = coverImageUrl,
                        contentDescription = "$title cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight(),
                    )
                } else {
                    Text(
                        text = "ðŸ“–",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            // Metadata Column
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Volume/Issue Label
                if (volumeNumber != null || issueNumber != null) {
                    val label =
                        buildString {
                            if (volumeNumber != null) append("Vol. $volumeNumber")
                            if (issueNumber != null) {
                                if (volumeNumber != null) append(" â€¢ ")
                                append("Issue $issueNumber")
                            }
                        }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Writer/Artist Metadata
                if (writer != null || artist != null) {
                    val metadata =
                        buildString {
                            if (writer != null) {
                                append("Writer: ")
                                append(writer.take(20))
                                if (writer.length > 20) append("...")
                            }
                            if (artist != null) {
                                if (writer != null) append(" â€¢ ")
                                append("Artist: ")
                                append(artist.take(20))
                                if (artist.length > 20) append("...")
                            }
                        }
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Description (placeholder or actual)
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "[No description available]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
