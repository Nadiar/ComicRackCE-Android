package com.comicrack.library.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comicrack.core.storage.provider.StorageType

/**
 * Row of filter chips for filtering comics by storage source.
 *
 * Shows "All", "Local", and "Google Drive" chips. The currently selected
 * chip is highlighted. Selecting "All" (null) shows all comics.
 *
 * Phase 6: Unified Library UI (SRC-05-007 v2)
 */
@Composable
fun StorageFilterChips(
    selectedType: StorageType?,
    onTypeSelected: (StorageType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedType == null,
            onClick = { onTypeSelected(null) },
            label = { Text("All") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )

        FilterChip(
            selected = selectedType == StorageType.LOCAL || selectedType == StorageType.SAF,
            onClick = { onTypeSelected(StorageType.SAF) },
            label = { Text("Local") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )

        FilterChip(
            selected = selectedType == StorageType.GOOGLE_DRIVE,
            onClick = { onTypeSelected(StorageType.GOOGLE_DRIVE) },
            label = { Text("Google Drive") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
}
