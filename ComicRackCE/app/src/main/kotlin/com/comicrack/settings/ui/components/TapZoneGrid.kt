package com.comicrack.settings.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.comicrack.data.preferences.ZoneSize

/**
 * State indicating which gesture actions are configured for a zone.
 */
data class ZoneIndicatorState(
    val hasSingleTap: Boolean,
    val hasDoubleTap: Boolean,
    val hasLongPress: Boolean,
)

private val ZONE_LABELS = listOf("TL", "TC", "TR", "ML", "C", "MR", "BL", "BC", "BR")
private const val PHONE_ASPECT_RATIO = 9f / 16f
private const val SELECTED_SCALE = 1.02f
private const val ICON_SIZE = 14

/**
 * Clean Material Design 3 tap zone grid rendered inside a phone-shaped frame.
 *
 * @param selectedZoneIndex The currently selected zone index (0-8), or -1 if none.
 * @param zoneStates List of 9 states indicating which actions are configured per zone.
 * @param centerZoneSize Configurable center zone size affecting row/column weights.
 * @param onZoneSelected Callback when a zone cell is tapped.
 */
@Composable
fun TapZoneGrid(
    selectedZoneIndex: Int,
    zoneStates: List<ZoneIndicatorState>,
    centerZoneSize: ZoneSize = ZoneSize.MEDIUM,
    onZoneSelected: (Int) -> Unit,
) {
    val centerRatio = centerZoneSize.ratio
    val sideRatio = (1f - centerRatio) / 2f
    val weights = listOf(sideRatio, centerRatio, sideRatio)

    // Phone-shaped frame
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PHONE_ASPECT_RATIO)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp),
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (rowIndex in 0 until 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(weights[rowIndex]),
                    ) {
                        for (colIndex in 0 until 3) {
                            val index = rowIndex * 3 + colIndex
                            val state = zoneStates.getOrElse(index) {
                                ZoneIndicatorState(false, false, false)
                            }

                            ZoneCell(
                                index = index,
                                isSelected = index == selectedZoneIndex,
                                state = state,
                                onClick = { onZoneSelected(index) },
                                modifier = Modifier.weight(weights[colIndex]),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneCell(
    index: Int,
    isSelected: Boolean,
    state: ZoneIndicatorState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAnyAction = state.hasSingleTap || state.hasDoubleTap || state.hasLongPress

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            hasAnyAction -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "zoneBg",
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "zoneBorder",
    )

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) SELECTED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "zoneScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = borderColor,
            )
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp),
        ) {
            // Zone label
            Text(
                text = ZONE_LABELS.getOrElse(index) { "" },
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )

            // Action indicator icons
            if (hasAnyAction) {
                ZoneActionIcons(
                    state = state,
                    tint = contentColor,
                )
            }
        }
    }
}

@Composable
private fun ZoneActionIcons(
    state: ZoneIndicatorState,
    tint: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        if (state.hasSingleTap) {
            Icon(
                imageVector = Icons.Rounded.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE.dp),
                tint = tint,
            )
        }
        if (state.hasDoubleTap) {
            Icon(
                imageVector = Icons.Rounded.Repeat,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE.dp),
                tint = tint,
            )
        }
        if (state.hasLongPress) {
            Icon(
                imageVector = Icons.Rounded.BackHand,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE.dp),
                tint = tint,
            )
        }
    }
}
