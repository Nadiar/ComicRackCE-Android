package com.comicrack.settings.ui.components

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.ReaderTapAction

/**
 * TapZoneEditor â€” Configure tap actions for the 4 screen edges.
 *
 * Edges:
 * - LEFT: Left edge of screen
 * - RIGHT: Right edge of screen
 * - TOP: Top edge of screen
 * - BOTTOM: Bottom edge of screen
 *
 * @param leftEdgeAction Current action for left edge
 * @param rightEdgeAction Current action for right edge
 * @param topEdgeAction Current action for top edge
 * @param bottomEdgeAction Current action for bottom edge
 * @param onLeftEdgeChange Callback when left edge action changes
 * @param onRightEdgeChange Callback when right edge action changes
 * @param onTopEdgeChange Callback when top edge action changes
 * @param onBottomEdgeChange Callback when bottom edge action changes
 */
@Composable
fun TapZoneEditor(
    leftEdgeAction: String,
    rightEdgeAction: String,
    topEdgeAction: String,
    bottomEdgeAction: String,
    onLeftEdgeChange: (String) -> Unit,
    onRightEdgeChange: (String) -> Unit,
    onTopEdgeChange: (String) -> Unit,
    onBottomEdgeChange: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Left Edge
        TapZoneSelectorRow(
            label = "Left Tap Zone Action",
            subtitle = "Action performed when left edge is tapped",
            currentActionKey = leftEdgeAction,
            onActionChange = onLeftEdgeChange,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
        )

        // Right Edge
        TapZoneSelectorRow(
            label = "Right Tap Zone Action",
            subtitle = "Action performed when right edge is tapped",
            currentActionKey = rightEdgeAction,
            onActionChange = onRightEdgeChange,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
        )

        // Top Edge
        TapZoneSelectorRow(
            label = "Top Tap Zone Action",
            subtitle = "Action performed when top edge is tapped",
            currentActionKey = topEdgeAction,
            onActionChange = onTopEdgeChange,
            icon = Icons.Filled.ArrowUpward,
        )

        // Bottom Edge
        TapZoneSelectorRow(
            label = "Bottom Tap Zone Action",
            subtitle = "Action performed when bottom edge is tapped",
            currentActionKey = bottomEdgeAction,
            onActionChange = onBottomEdgeChange,
            icon = Icons.Filled.ArrowDownward,
        )
    }
}

/**
 * Single tap zone selector row with icon, label, and dropdown.
 */
@Composable
private fun TapZoneSelectorRow(
    label: String,
    subtitle: String,
    currentActionKey: String,
    onActionChange: (String) -> Unit,
    icon: ImageVector,
) {
    var isDialogOpen by remember { mutableStateOf(false) }
    val currentAction = ReaderTapAction.fromKey(currentActionKey)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { isDialogOpen = true }
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = currentAction.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    // Dialog for action selection
    if (isDialogOpen) {
        val availableActions =
            ReaderTapAction
                .values()
                .filter { it != ReaderTapAction.NONE }
                .sortedBy { it.displayName }

        AlertDialog(
            onDismissRequest = { isDialogOpen = false },
            title = {
                Text(
                    text = "Select Action",
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = availableActions,
                            key = { it.key },
                        ) { action ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onActionChange(action.key)
                                            isDialogOpen = false
                                        }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(
                                    selected = action == currentAction,
                                    onClick = {
                                        onActionChange(action.key)
                                        isDialogOpen = false
                                    },
                                )
                                Text(
                                    text = action.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isDialogOpen = false }) {
                    Text("Close")
                }
            },
        )
    }
}
