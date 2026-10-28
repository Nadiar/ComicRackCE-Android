package com.comicrack.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.data.preferences.ReaderTapAction
import com.comicrack.settings.composables.SettingsGroupContainer
import com.comicrack.settings.composables.SettingsHeader

/**
 * Volume Buttons Settings Screen
 * Configure actions for Volume Up and Volume Down buttons
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Composable
fun VolumeButtonsSettingsScreen() {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        SettingsHeader(text = "VOLUME BUTTONS")

        SettingsGroupContainer {
            ActionSelector(
                label = "Volume Up Action",
                currentAction = state.hardwareKeyBindings.volumeUpAction,
                onActionSelected = { viewModel.setKeyboardVolumeUp(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Volume Up Long Press",
                currentAction = state.hardwareKeyBindings.volumeUpLongPressAction,
                onActionSelected = { viewModel.setKeyboardVolumeUpLongPress(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Volume Down Action",
                currentAction = state.hardwareKeyBindings.volumeDownAction,
                onActionSelected = { viewModel.setKeyboardVolumeDown(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Volume Down Long Press",
                currentAction = state.hardwareKeyBindings.volumeDownLongPressAction,
                onActionSelected = { viewModel.setKeyboardVolumeDownLongPress(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * DPad Settings Screen
 */
@Composable
fun DPadSettingsScreen() {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        SettingsHeader(text = "D-PAD / ARROW KEYS")

        SettingsGroupContainer {
            ActionSelector(
                label = "Up",
                currentAction =
                    state.hardwareKeyBindings.keyboardBindings[android.view.KeyEvent.KEYCODE_DPAD_UP]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setKeyboardUp(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Down",
                currentAction =
                    state.hardwareKeyBindings.keyboardBindings[android.view.KeyEvent.KEYCODE_DPAD_DOWN]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setKeyboardDown(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Left",
                currentAction =
                    state.hardwareKeyBindings.keyboardBindings[android.view.KeyEvent.KEYCODE_DPAD_LEFT]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setKeyboardLeft(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Right",
                currentAction =
                    state.hardwareKeyBindings.keyboardBindings[android.view.KeyEvent.KEYCODE_DPAD_RIGHT]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setKeyboardRight(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Space",
                currentAction =
                    state.hardwareKeyBindings.keyboardBindings[android.view.KeyEvent.KEYCODE_SPACE]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setKeyboardSpace(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Page Up",
                currentAction =
                    state.hardwareKeyBindings.keyboardBindings[android.view.KeyEvent.KEYCODE_PAGE_UP]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setKeyboardPageUp(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Page Down",
                currentAction =
                    state.hardwareKeyBindings.keyboardBindings[android.view.KeyEvent.KEYCODE_PAGE_DOWN]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setKeyboardPageDown(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Escape",
                currentAction =
                    state.hardwareKeyBindings.keyboardBindings[android.view.KeyEvent.KEYCODE_ESCAPE]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setKeyboardEscape(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Arrow Keys are just DPad in this implementation
@Composable
fun ArrowKeysSettingsScreen() {
    DPadSettingsScreen()
}

/**
 * Gamepad Settings Screen
 * NOTE: Currently maps to standard detailed navigation keys.
 * Expansion needed if specific Gamepad KeyCodes (A, B, X, Y) are supported in future.
 */
@Composable
fun GamepadSettingsScreen() {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        SettingsHeader(text = "GAMEPAD BUTTONS (MAPPED TO D-PAD)")

        Text(
            text = "Gamepads typically map D-Pad to Arrow Keys. Configured below:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SettingsGroupContainer {
            ActionSelector(
                label = "D-Pad Up",
                currentAction =
                    state.hardwareKeyBindings.gamepadBindings[android.view.KeyEvent.KEYCODE_DPAD_UP]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setGamepadUp(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "D-Pad Down",
                currentAction =
                    state.hardwareKeyBindings.gamepadBindings[android.view.KeyEvent.KEYCODE_DPAD_DOWN]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setGamepadDown(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "D-Pad Left",
                currentAction =
                    state.hardwareKeyBindings.gamepadBindings[android.view.KeyEvent.KEYCODE_DPAD_LEFT]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setGamepadLeft(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "D-Pad Right",
                currentAction =
                    state.hardwareKeyBindings.gamepadBindings[android.view.KeyEvent.KEYCODE_DPAD_RIGHT]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setGamepadRight(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Button A",
                currentAction =
                    state.hardwareKeyBindings.gamepadBindings[android.view.KeyEvent.KEYCODE_BUTTON_A]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setGamepadA(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ActionSelector(
                label = "Button B",
                currentAction =
                    state.hardwareKeyBindings.gamepadBindings[android.view.KeyEvent.KEYCODE_BUTTON_B]
                        ?: ReaderTapAction.NONE,
                onActionSelected = { viewModel.setGamepadB(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ActionSelector(
    label: String,
    currentAction: ReaderTapAction,
    onActionSelected: (ReaderTapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSheetOpen by remember { mutableStateOf(false) }

    // Use Shared BottomSheet for selection
    if (isSheetOpen) {
        com.comicrack.settings.composables.ActionSelectionBottomSheet(
            title = "Select Action for $label",
            currentAction = currentAction,
            onActionSelected = { action ->
                onActionSelected(action)
                isSheetOpen = false
            },
            onDismiss = { isSheetOpen = false },
        )
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { isSheetOpen = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = currentAction.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )

        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = "Select",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
