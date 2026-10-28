package com.comicrack.settings.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * SettingsHeader â€” M3E Settings Architecture
 *
 * Standardized category header for settings sections.
 * - Uppercase text (semantic: section marker)
 * - Primary color (visual hierarchy)
 * - Consistent padding (24.dp start, top; 8.dp bottom)
 *
 * Usage: Place at the start of each settings category (Appearance, Library, etc.)
 *
 * @param text Section title (will be uppercased)
 * @param modifier Layout modifier
 */
@Composable
fun SettingsHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

/**
 * SettingsSubLabel â€” M3E Settings Architecture
 *
 * In-group control label for individual settings within a container.
 * - Smaller than header (bodySmall)
 * - Secondary color (onSurfaceVariant)
 * - Consistent horizontal padding (16.dp)
 *
 * Usage: Label for switches, toggles, or control groups within SettingsGroupContainer
 *
 * @param text Label text
 * @param modifier Layout modifier
 */
@Composable
fun SettingsSubLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * SettingsGroupContainer â€” M3E Settings Architecture
 *
 * Unified wrapper for grouping related settings (High-radius container, surfaceContainerLow).
 * - OutlinedCard with 24.dp corner radius (pill shape)
 * - surfaceContainerLow background (subtle layer)
 * - 1.dp outlineVariant border (visual separation)
 * - Reduces cognitive load by providing clear visual boundaries
 *
 * Usage:
 * ```
 * SettingsHeader("APPEARANCE")
 * SettingsGroupContainer {
 *     SettingsSubLabel("Theme")
 *     // ... controls (switch, segmented button, etc.)
 * }
 * ```
 *
 * @param modifier Layout modifier (default: 16.dp horizontal, 4.dp vertical padding)
 * @param content Composable lambda for container children
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Composable
fun SettingsGroupContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            content = content,
        )
    }
}
