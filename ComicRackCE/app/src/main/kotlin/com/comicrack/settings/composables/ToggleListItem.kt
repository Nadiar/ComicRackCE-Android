package com.comicrack.settings.composables

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * ToggleListItem â€” M3E Standardized Toggle List Item
 *
 * Combines a title with AnimatedToggleButton for consistent toggle UX across all settings screens.
 * Replaces screen-specific SwitchListItem implementations (GeneralSwitchListItem, ControlsSwitchListItem, etc.).
 *
 * Features:
 * - Consistent layout: title on left, animated toggle on right
 * - Clickable row for accessibility
 * - Animated checkmark with 360Â° spin + bounce
 *
 * @param title Label text
 * @param checked State of the toggle
 * @param onCheckedChange Callback when toggled
 * @param modifier Layout modifier
 */
@Composable
fun ToggleListItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            AnimatedToggleButton(
                isEnabled = checked,
                onToggle = onCheckedChange,
            )
        },
        modifier = modifier.clickable { onCheckedChange(!checked) },
        colors =
            androidx.compose.material3.ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
    )
}

/**
 * ToggleListItemWithSubtitle â€” M3E Toggle List Item with Description
 *
 * Extended variant with supporting subtitle text.
 * Uses ListItem layout for Material3 compliance.
 *
 * @param title Headline text
 * @param subtitle Supporting description text
 * @param checked State of the toggle
 * @param onCheckedChange Callback when toggled
 * @param modifier Layout modifier
 */
@Composable
fun ToggleListItemWithSubtitle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            AnimatedToggleButton(
                isEnabled = checked,
                onToggle = onCheckedChange,
            )
        },
        modifier = modifier.clickable { onCheckedChange(!checked) },
        colors =
            androidx.compose.material3.ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
    )
}
