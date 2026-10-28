package com.comicrack.reader.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.ComicMetadata
import com.comicrack.reader.ComicViewerViewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComicDetailsBottomSheet(
    viewModel: ComicViewerViewModel,
    modifier: Modifier = Modifier,
    _sheetState: SheetState = rememberModalBottomSheetState(),
    onDismiss: () -> Unit,
) {
    /**
     * Details bottom sheet for comic metadata and sharing.
     *
     * Notes:
     * - Sharing is implemented via `viewModel.sharePageScreenshot` and should be
     *   tested to ensure it handles permission and file errors gracefully.
     * - Make sure bottom sheet content is reachable to screen readers and that
     *   metadata labels are descriptive.
     */
    val comicBook by viewModel.comicBook.collectAsState()
    val metadata by viewModel.comicMetadataDetailed.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleLarge,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Share Button (Screenshot)
                val context = androidx.compose.ui.platform.LocalContext.current
                IconButton(onClick = {
                    viewModel.capturePageScreenshot(
                        context,
                        com.comicrack.reader.ComicViewerViewModel.CaptureMode.SHARE,
                    )
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Page",
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        if (comicBook != null) {
            comicBook?.let { ComicMetadataContent(it, metadata) }
        } else {
            Text(
                text = "No additional metadata available for this file.",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicMetadataContent(
    comic: ComicBook,
    metadata: ComicMetadata?,
) {
    // 1. Core Info (Series, Number, Title)
    val displaySeries = metadata?.series ?: comic.series.takeIf { it.isNotEmpty() } ?: "Unknown Series"
    val displayNumber = metadata?.number ?: comic.issueNumber
    val displayTitle = metadata?.title ?: comic.title

    Text(
        text = displaySeries,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )

    if (displayNumber.isNotEmpty()) {
        Text(
            text = "#$displayNumber",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    // Story Arc
    metadata?.storyArc?.takeIf { it.isNotEmpty() }?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Medium,
        )
    }

    if (displayTitle.isNotEmpty() && displayTitle != comic.fileName) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.titleMedium,
            fontStyle = FontStyle.Italic,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 2. Chips for Meta (Year, Format, Publisher)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Publisher
        val publisher = metadata?.publisher ?: comic.publisher
        publisher?.takeIf { it.isNotEmpty() }?.let {
            MetadataChip(
                label = it,
                icon = Icons.Default.Description,
            )
        }

        // Year
        val year = metadata?.year ?: comic.year
        if (year > 0) {
            MetadataChip(
                label = year.toString(),
                icon = Icons.Default.Event,
            )
        }

        // Page Count
        val pages = metadata?.pageCount ?: comic.pageCount
        if (pages > 0) {
            MetadataChip(
                label = "$pages Pages",
                icon = Icons.Default.Image,
            )
        }

        // Format
        val format = metadata?.format ?: comic.format
        if (format.isNotEmpty()) {
            MetadataChip(
                label = format.uppercase(),
                icon = Icons.Default.Storage,
            )
        }

        // Manga
        val isManga = metadata?.manga ?: comic.manga
        if (isManga) {
            MetadataChip(
                label = "Manga",
                icon = Icons.Default.Image,
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 3. Credits (Show from metadata if available for more richness)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CreditRow("Writer", metadata?.writers ?: comic.writer)
        CreditRow("Penciller", metadata?.pencillers ?: comic.penciller)
        CreditRow("Inker", metadata?.inkers ?: comic.inker)
        CreditRow("Colorist", metadata?.colorists ?: comic.colorist)
        CreditRow("Letterer", metadata?.letterers ?: comic.letterer)
        CreditRow("Editor", metadata?.editors ?: comic.editor)
    }

    // 4. Content (Characters, Teams, Locations from detailed metadata)
    metadata?.let { m ->
        val characters = parseMetadataList(m.characters)
        val teams = parseMetadataList(m.teams)
        val locations = parseMetadataList(m.locations)

        if (characters.isNotEmpty() || teams.isNotEmpty() || locations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Content", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (characters.isNotEmpty()) ContentSection("Characters", characters)
            if (teams.isNotEmpty()) ContentSection("Teams", teams)
            if (locations.isNotEmpty()) ContentSection("Locations", locations)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 5. Summary
    val summary = metadata?.summary ?: comic.caption
    summary?.takeIf { it.isNotEmpty() }?.let {
        Text(
            text = "Summary",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 6. Technical Info
    Text(
        text = "File Info",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = comic.filePath,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentSection(
    label: String,
    items: List<String>,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            Text(
                text = item,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp),
                        ).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private fun parseMetadataList(json: String?): List<String> {
    if (json.isNullOrEmpty()) return emptyList()
    return try {
        // Simple manual parse since it's just a JSON array of strings
        // ["Name 1", "Name 2"]
        json
            .trim('[', ']')
            .split(Regex("\",\\s*\""))
            .map { it.trim('\"') }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
fun MetadataChip(
    label: String,
    icon: ImageVector,
) {
    AssistChip(
        onClick = { },
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

@Composable
fun CreditRow(
    tole: String,
    name: String?,
) {
    if (name.isNullOrEmpty()) return

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$tole:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

fun formatFileSize(size: Long): String {
    val df = DecimalFormat("#.##")
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0

    return when {
        size >= gb -> "${df.format(size / gb)} GB"
        size >= mb -> "${df.format(size / mb)} MB"
        size >= kb -> "${df.format(size / kb)} KB"
        else -> "$size B"
    }
}
