package com.comicrack.reader.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.FitMode
import com.comicrack.data.preferences.ReadingDirection
import com.comicrack.reader.ComicViewerViewModel
import com.comicrack.settings.composables.AnimatedToggleButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualSettingsBottomSheet(
    viewModel: ComicViewerViewModel,
    onDismiss: () -> Unit,
) {
    /**
     * Visual Settings bottom sheet.
     *
     * Notes:
     * - Value ranges for sliders (brightness, saturation, sharpness) should be
     *   validated in ViewModel and documented to ensure UI and persistence agree.
     * - Provide tests that simulate adjusting values and asserting those values are
     *   saved to preferences and respected by the renderer.
     */
    // Collect ViewModel state
    val brightness by viewModel.readerBrightness.collectAsState(0.5f)
    val brightnessEnabled by viewModel.readerBrightnessEnabled.collectAsState(false)
    val saturation by viewModel.readerSaturation.collectAsState(1.0f)
    val contrast by viewModel.readerContrast.collectAsState(1.0f)
    val sharpness by viewModel.readerSharpness.collectAsState(1.0f)
    val pageFitMode by viewModel.pageFitMode.collectAsState(FitMode.FIT_WIDTH)
    val rotation by viewModel.rotation.collectAsState(0)

    val isRtl by viewModel.readingDirection.collectAsState(ReadingDirection.LTR)
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Handle (Drag Indicator is automatic in ModalBottomSheet usually, but we are just content here)

        // 1. Brightness Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Display", style = MaterialTheme.typography.titleMedium)
                AnimatedToggleButton(
                    isEnabled = brightnessEnabled,
                    onToggle = { viewModel.toggleBrightness(!brightnessEnabled) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(
                    Icons.Default.BrightnessLow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Slider(
                    value = brightness,
                    onValueChange = { viewModel.setBrightness(it) },
                    modifier = Modifier.weight(1f),
                    enabled = brightnessEnabled,
                )
                Icon(
                    Icons.Default.BrightnessHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        HorizontalDivider()

        // 2. Image Adjustments Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Image Adjustments", style = MaterialTheme.typography.titleMedium)

            // Saturation
            AdjustmentItem(
                label = "Saturation",
                value = saturation,
                onValueChange = { viewModel.setSaturation(it) },
            )

            // Contrast
            AdjustmentItem(
                label = "Contrast",
                value = contrast,
                onValueChange = { viewModel.setContrast(it) },
            )

            // Sharpness
            AdjustmentItem(
                label = "Sharpness",
                value = sharpness,
                onValueChange = { viewModel.setSharpness(it) },
            )
        }

        HorizontalDivider()

        // 3. Layout Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Layout & Rotation", style = MaterialTheme.typography.titleMedium)

            // Fit Mode Segmented Button (M3 Standard)
            val fitModes =
                listOf(
                    FitMode.FIT_WIDTH to "Width",
                    FitMode.FIT_HEIGHT to "Height",
                    FitMode.FIT_PAGE to "Page",
                )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                fitModes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = pageFitMode == mode,
                        onClick = { viewModel.setFitMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = fitModes.size),
                        icon = {
                            val icon =
                                when (mode) {
                                    FitMode.FIT_WIDTH -> Icons.Default.WidthWide
                                    FitMode.FIT_HEIGHT -> Icons.Default.Height
                                    FitMode.FIT_PAGE -> Icons.Default.FitScreen
                                    else -> Icons.Default.FitScreen
                                }
                            Icon(icon, null, modifier = Modifier.size(18.dp))
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            // Rotate as a prominent button
            Button(
                onClick = { viewModel.rotatePage() },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Icon(Icons.Default.RotateRight, null)
                Spacer(Modifier.width(12.dp))
                val rotationText =
                    when (rotation) {
                        90 -> "90\u00b0"
                        180 -> "180\u00b0"
                        270 -> "270\u00b0"
                        else -> "0\u00b0 (Reset)"
                    }
                Text("Rotate: $rotationText")
            }
        }

        HorizontalDivider()

        // 4. Reading Mode Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Reading Options", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Right-to-Left (Manga)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Reverses swipe and tap navigation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedToggleButton(
                    isEnabled = isRtl == ReadingDirection.RTL,
                    onToggle = { viewModel.toggleRtl(it) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 5. Full Settings Link
        OutlinedButton(
            onClick = {
                onDismiss()
                context.startActivity(
                    android.content.Intent(context, com.comicrack.settings.SettingsActivity::class.java),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("All Settings")
        }

        Spacer(Modifier.height(32.dp)) // Bottom padding
    }
}

@Composable
private fun AdjustmentItem(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.labelLarge) },
        supportingContent = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0.5f..1.5f,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        trailingContent = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${(value * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(vertical = 0.dp),
    )
}
