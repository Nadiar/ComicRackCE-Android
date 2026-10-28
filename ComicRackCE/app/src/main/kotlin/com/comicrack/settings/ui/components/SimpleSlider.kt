package com.comicrack.settings.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * SimpleSlider - Enhanced slider component for numeric settings.
 *
 * Provides a Material3 Slider with:
 * - Visual slider track
 * - Current value display
 * - Min/Max range display
 * - Real-time value updates
 *
 * @param value Current value
 * @param onValueChange Callback when value changes
 * @param min Minimum value
 * @param max Maximum value
 * @param step Step size for slider increments
 * @param label Optional label for the setting
 *
 * DOCFIX: doc-only pass - 2026-01-15 (docfix/pilot-comments)
 */
@Composable
fun SimpleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    min: Float,
    max: Float,
    step: Float = 1f,
    label: String? = null,
    formatValue: (Float) -> String = { "%.0f".format(it) },
) {
    var sliderValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        sliderValue = value
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        // Label (optional)
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // Value display and range
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Value: ${formatValue(sliderValue)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${formatValue(min)} - ${formatValue(max)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        // Slider
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue
                onValueChange(newValue)
            },
            valueRange = min..max,
            steps = if (step > 0) ((max - min) / step).toInt() - 1 else 0,
            modifier = Modifier.fillMaxWidth(),
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            interactionSource = remember { MutableInteractionSource() },
        )
    }
}
