package com.comicrack.settings.screens
// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.data.preferences.ReaderTapAction
import com.comicrack.data.preferences.UserPreferences
import com.comicrack.data.preferences.ZoneSize
import com.comicrack.settings.composables.SettingsGroupContainer
import com.comicrack.settings.composables.SettingsHeader
import com.comicrack.settings.composables.SettingsSubLabel
import com.comicrack.settings.ui.components.TapZoneGrid
import com.comicrack.settings.ui.components.ZoneIndicatorState

/**
 * TapZoneSettingsScreen - Configures the 3x3 Reader Tap Zones.
 * Allows setting Single Tap, Double Tap, and Long Press actions.
 */
@Composable
fun TapZoneSettingsScreen() {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 0-8 for the 9 zones. -1 means no zone selected yet.
    // Default to Center (index 4) so the screen isn't empty on load.
    var selectedZoneIndex by remember { mutableIntStateOf(4) }

    // Bottom Sheet State
    var showActionSheet by remember { mutableStateOf(false) }
    var actionSheetType by remember { mutableStateOf<PreferencesViewModel.ActionType?>(null) }

    if (showActionSheet && actionSheetType != null) {
        actionSheetType?.let { type ->
            com.comicrack.settings.composables.ActionSelectionBottomSheet(
                title = "${getActionTitle(type)} for ${getDescriptiveZoneName(selectedZoneIndex)}",
                onActionSelected = { action ->
                    viewModel.updateAction(selectedZoneIndex, type, action)
                    showActionSheet = false
                },
                onDismiss = { showActionSheet = false },
            )
        }
    }

    // Scrollable Container
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        SettingsHeader("TAP ZONE CONFIGURATION")

        SettingsGroupContainer {
            SettingsSubLabel("Select a region on the grid to configure functionality.")

            // Calculate states for all 9 zones to display indicators
            val zoneStates =
                remember(state) {
                    List(9) { index ->
                        ZoneIndicatorState(
                            hasSingleTap =
                                viewModel.getActionForZone(
                                    state,
                                    index,
                                    PreferencesViewModel.ActionType.SINGLE_TAP,
                                ) != ReaderTapAction.NONE,
                            hasDoubleTap =
                                viewModel.getActionForZone(
                                    state,
                                    index,
                                    PreferencesViewModel.ActionType.DOUBLE_TAP,
                                ) != ReaderTapAction.NONE,
                            hasLongPress =
                                viewModel.getActionForZone(
                                    state,
                                    index,
                                    PreferencesViewModel.ActionType.LONG_TAP,
                                ) != ReaderTapAction.NONE,
                        )
                    }
                }

            // The Visual Selector
            TapZoneGrid(
                selectedZoneIndex = selectedZoneIndex,
                zoneStates = zoneStates,
                centerZoneSize = state.centerZoneSize,
                onZoneSelected = { zoneIndex -> selectedZoneIndex = zoneIndex },
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )

            // Zone Size Control
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Center Zone Size",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SegmentedButton(
                        selected = state.centerZoneSize == ZoneSize.SMALL,
                        onClick = { viewModel.setCenterZoneSize(ZoneSize.SMALL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    ) {
                        Text("Small")
                    }
                    SegmentedButton(
                        selected = state.centerZoneSize == ZoneSize.MEDIUM,
                        onClick = { viewModel.setCenterZoneSize(ZoneSize.MEDIUM) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    ) {
                        Text("Medium")
                    }
                    SegmentedButton(
                        selected = state.centerZoneSize == ZoneSize.LARGE,
                        onClick = { viewModel.setCenterZoneSize(ZoneSize.LARGE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    ) {
                        Text("Large")
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Selected Zone Configuration
            if (selectedZoneIndex in 0..8) {
                ZoneConfigurationPanel(
                    zoneIndex = selectedZoneIndex,
                    state = state,
                    viewModel = viewModel,
                    onConfigureSingleTap = {
                        actionSheetType = PreferencesViewModel.ActionType.SINGLE_TAP
                        showActionSheet = true
                    },
                    onConfigureDoubleTap = {
                        actionSheetType = PreferencesViewModel.ActionType.DOUBLE_TAP
                        showActionSheet = true
                    },
                    onConfigureLongTap = {
                        actionSheetType = PreferencesViewModel.ActionType.LONG_TAP
                        showActionSheet = true
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// --- Helper Functions & Enums ---

// --- Helper Functions ---

@Composable
private fun getActionTitle(type: PreferencesViewModel.ActionType): String =
    when (type) {
        PreferencesViewModel.ActionType.SINGLE_TAP -> "Single Tap"
        PreferencesViewModel.ActionType.DOUBLE_TAP -> "Double Tap"
        PreferencesViewModel.ActionType.LONG_TAP -> "Long Press"
    }

@Composable
private fun getDescriptiveZoneName(index: Int): String =
    when (index) {
        0 -> androidx.compose.ui.res.stringResource(com.comicrack.reader.R.string.reader_top_left_title)
        1 -> androidx.compose.ui.res.stringResource(com.comicrack.reader.R.string.reader_top_center_title)
        2 -> androidx.compose.ui.res.stringResource(com.comicrack.reader.R.string.reader_top_right_title)
        3 -> androidx.compose.ui.res.stringResource(com.comicrack.reader.R.string.reader_middle_left_title)
        4 -> androidx.compose.ui.res.stringResource(com.comicrack.reader.R.string.reader_center_title)
        5 -> androidx.compose.ui.res.stringResource(com.comicrack.reader.R.string.reader_middle_right_title)
        6 -> androidx.compose.ui.res.stringResource(com.comicrack.reader.R.string.reader_bottom_left_title)
        7 -> androidx.compose.ui.res.stringResource(com.comicrack.reader.R.string.reader_bottom_center_title)
        8 -> androidx.compose.ui.res.stringResource(com.comicrack.reader.R.string.reader_bottom_right_title)
        else -> "Unknown Zone"
    }

// --- Composables ---

@Composable
private fun ZoneConfigurationPanel(
    zoneIndex: Int,
    state: UserPreferences,
    viewModel: PreferencesViewModel,
    onConfigureSingleTap: () -> Unit,
    onConfigureDoubleTap: () -> Unit,
    onConfigureLongTap: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val zoneName = getDescriptiveZoneName(zoneIndex)

        Text(
            text = "$zoneName Settings",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // Single Tap Card
        ActionDisplayCard(
            label = "Single Tap",
            currentAction = viewModel.getActionForZone(state, zoneIndex, PreferencesViewModel.ActionType.SINGLE_TAP),
            onClick = onConfigureSingleTap,
        )

        // Double Tap Card
        ActionDisplayCard(
            label = "Double Tap",
            currentAction = viewModel.getActionForZone(state, zoneIndex, PreferencesViewModel.ActionType.DOUBLE_TAP),
            onClick = onConfigureDoubleTap,
        )

        // Long Tap Card
        ActionDisplayCard(
            label = "Long Press",
            currentAction = viewModel.getActionForZone(state, zoneIndex, PreferencesViewModel.ActionType.LONG_TAP),
            onClick = onConfigureLongTap,
        )
    }
}

@Composable
private fun ActionDisplayCard(
    label: String,
    currentAction: ReaderTapAction,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = currentAction.displayName, // Use property from Enum
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_edit),
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
