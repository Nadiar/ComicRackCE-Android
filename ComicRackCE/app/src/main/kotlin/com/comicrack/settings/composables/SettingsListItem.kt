package com.comicrack.settings.composables

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * SettingsListItem â€” M3E Standardized Clickable List Item
 *
 * Generic list item for navigation or dialog triggers in settings.
 * Matches styling of ToggleListItem.
 *
 * @param title Headline text
 * @param subtitle Supporting description text (optional)
 * @param icon Leading icon (optional)
 * @param onClick Action when clicked
 * @param modifier Layout modifier
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Composable
fun SettingsListItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent =
            icon?.let {
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        modifier = modifier.clickable { onClick() },
        colors =
            androidx.compose.material3.ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
    )
}
