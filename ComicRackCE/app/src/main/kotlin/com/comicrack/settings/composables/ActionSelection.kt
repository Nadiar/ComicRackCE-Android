package com.comicrack.settings.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.ReaderTapAction

/**
 * Shared Bottom Sheet for selecting a Reader Tap Action.
 * Used by TapZoneSettings, VolumeSettings, KeyboardSettings, etc.
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSelectionBottomSheet(
    title: String,
    currentAction: ReaderTapAction? = null, // Optional: highlight current
    onActionSelected: (ReaderTapAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp),
            )

            HorizontalDivider()

            ReaderTapAction.entries.forEach { action ->
                ListItem(
                    headlineContent = { Text(action.displayName) },
                    modifier =
                        Modifier.clickable {
                            onActionSelected(action)
                        },
                    trailingContent = {
                        if (currentAction != null && action == currentAction) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}
