package com.comicrack.settings.screens

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.animation.core.Spring
import androidx.compose.ui.res.stringResource
import com.comicrack.reader.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.settings.composables.SettingsGroupContainer
import com.comicrack.settings.composables.SettingsHeader
import com.comicrack.settings.composables.ToggleListItem

/**
 * Controls Settings Screen - Input Device Configuration
 *
 * Structure:
 * - Touch Input: Tap navigation, haptic feedback, swipe
 * - Hardware Buttons: Volume,  Keyboard, Gamepad, Bluetooth Button (each navigates to subpage)
 * - Display Helpers: Debug options
 */
@Composable
fun ControlsSettingsScreen(
    onTapZonesClick: () -> Unit = {},
    onVolumeClick: () -> Unit = {},
    onKeyboardClick: () -> Unit = {},
    onGamepadClick: () -> Unit = {},
) {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        // TOUCH INPUT SECTION
        SettingsHeader(text = "TOUCH INPUT")

        SettingsGroupContainer {
            ToggleListItem(
                title = "Tap Navigation",
                checked = state.tapNavigation,
                onCheckedChange = { viewModel.setTapNavigation(it) },
            )

            if (state.tapNavigation) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                HardwareButtonNavigation(
                    label = "Configure Tap Zones",
                    onClick = onTapZonesClick,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ToggleListItem(
                title = "Double-Tap Zoom",
                checked = state.doubleTapEnabled,
                onCheckedChange = { viewModel.setDoubleTapEnabled(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ToggleListItem(
                title = "Haptic Feedback",
                checked = state.hapticFeedback,
                onCheckedChange = { viewModel.setHapticFeedback(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ToggleListItem(
                title = "Horizontal Swipe",
                checked = state.horizontalSwipeEnabled,
                onCheckedChange = { viewModel.setHorizontalSwipeEnabled(it) },
            )
        }

        // HARDWARE BUTTONS SECTION
        SettingsHeader(text = "HARDWARE BUTTONS")

        SettingsGroupContainer {
            // Volume Buttons - Toggle + Edit
            HardwareButtonToggleWithEdit(
                label = "Volume Buttons",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                isEnabled = state.volumeButtonsEnabled,
                onToggle = { viewModel.setVolumeButtonsEnabled(it) },
                onEdit = { onVolumeClick() },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Keyboard - Toggle + Edit
            HardwareButtonToggleWithEdit(
                label = "Keyboard",
                icon = Icons.Default.Keyboard,
                isEnabled = state.keyboardEnabled,
                onToggle = { viewModel.setKeyboardEnabled(it) },
                onEdit = { onKeyboardClick() },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Gamepad - Toggle + Edit
            HardwareButtonToggleWithEdit(
                label = "Gamepad Buttons",
                icon = Icons.Default.Gamepad,
                isEnabled = state.gamepadButtonsEnabled,
                onToggle = { viewModel.setGamepadButtonsEnabled(it) },
                onEdit = { onGamepadClick() },
            )

            // Bluetooth Buttons - Toggle + Edit
            HardwareButtonToggleWithEdit(
                label = "Bluetooth Buttons",
                icon = Icons.Default.Bluetooth,
                isEnabled = state.bluetoothButtonsEnabled,
                onToggle = { viewModel.setBluetoothButtonsEnabled(it) },
                onEdit = { onTapZonesClick() },
            )
        }

        // DISPLAY HELPERS SECTION
        SettingsHeader(text = "DISPLAY HELPERS")

        SettingsGroupContainer {
            ToggleListItem(
                title = "Show Tap Areas",
                checked = state.showTapAreas,
                onCheckedChange = { viewModel.setShowTapAreas(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ToggleListItem(
                title = stringResource(R.string.reader_rtl_navigation_enabled_title),
                checked = state.rtlNavigationEnabled,
                onCheckedChange = { viewModel.setRtlNavigationEnabled(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Hardware button configuration with split button design (Edit + Checkmark)
 *
 * Features:
 * - Status text showing if enabled/disabled with description
 * - Animated checkmark on left (scale + spring animation)
 * - Edit pencil on right
 * - Both buttons disabled (surfaceVariant colors) when control is disabled
 *
 * Layout:
 *   Title + Status description
 *   [Checkmark] | [Edit Pencil]  (split button design)
 */
@Composable
fun HardwareButtonToggleWithEdit(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Animation logic
    var animationTrigger by remember { mutableIntStateOf(0) }

    val checkmarkScale by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.8f, // Sync with isEnabled state
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "checkmarkScale",
    )

    val checkmarkRotation by animateFloatAsState(
        targetValue = if (animationTrigger > 0 && isEnabled) 360f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkmarkRotation",
    )

    // THE FIX: Change outer Column to Row
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, // Vertically centers text with button
    ) {
        // Left Side: Text Column (Weight 1f fills the available space)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon Background (Squircle) matching SettingsCategoryCard
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                // Slightly smaller than 16dp to fit better if needed
                shape = RoundedCornerShape(14.dp),
                modifier =
                    Modifier
                        // Slightly smaller than 48dp if constrained
                        .size(44.dp)
                        .padding(end = 0.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = if (isEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right Side: The Split Button
        Row(
            modifier = Modifier.height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LEFT HALF: Edit
            Surface(
                onClick = onEdit,
                enabled = isEnabled,
                color =
                    if (isEnabled) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    if (isEnabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                modifier = Modifier.height(32.dp).width(48.dp).semantics { role = Role.Button },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            VerticalDivider(
                modifier = Modifier.height(32.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )

            // RIGHT HALF: Toggle
            val (bgColor, contentColor) =
                if (isEnabled) {
                    MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant to
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }

            Surface(
                onClick = {
                    onToggle(!isEnabled)
                    animationTrigger++
                },
                color = bgColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.height(32.dp).width(44.dp).semantics { role = Role.Switch },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).scale(checkmarkScale).rotate(checkmarkRotation),
                    )
                }
            }
        }
    }
}

/**
 * Hardware button configuration row that navigates to subpage
 * Shows label and chevron indicating subpage navigation
 * Layout: Label on left, Chevron on right - single line
 */
@Composable
fun HardwareButtonNavigation(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Navigate to $label settings",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
