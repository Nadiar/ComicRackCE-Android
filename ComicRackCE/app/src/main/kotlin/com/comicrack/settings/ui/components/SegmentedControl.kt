package com.comicrack.settings.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * SegmentedControl â€” M3E Styled Segmented Button Row
 *
 * A reusable component for exclusive choice selection (Radio button alternative).
 * Styled with "Expressive" Pill shapes and SecondaryContainer colors.
 *
 * @param items List of options
 * @param selectedItem Current selected value
 * @param onItemSelection Callback on select
 * @param labelProvider Converts item to string label
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Composable
fun <T> SegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelection: (T) -> Unit,
    labelProvider: (T) -> String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = item == selectedItem
            val isFirst = index == 0
            val isLast = index == items.size - 1

            // Fully rounded "Pill" ends (50% height, or generous 24.dp)
            val shape =
                when {
                    isFirst ->
                        RoundedCornerShape(
                            topStart = 24.dp,
                            bottomStart = 24.dp,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp,
                        )
                    isLast -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(0.dp)
                }

            OutlinedButton(
                onClick = { onItemSelection(item) },
                shape = shape,
                modifier =
                    Modifier
                        .weight(1f)
                        .zIndex(if (isSelected) 1f else 0f)
                        .offset(x = if (index > 0) (-1 * index).dp else 0.dp),
                colors =
                    if (isSelected) {
                        // M3E: Active selection uses SecondaryContainer
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        // M3E: Inactive uses SurfaceContainerHigh to contrast with Surface/Card background
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                border = null,
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp), // Taller touch target
            ) {
                Text(
                    text = labelProvider(item),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}
