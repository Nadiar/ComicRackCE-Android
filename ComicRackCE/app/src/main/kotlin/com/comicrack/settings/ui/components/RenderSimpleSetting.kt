package com.comicrack.settings.ui.components

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.comicrack.settings.catalog.Setting
import com.comicrack.settings.catalog.SettingType

/**
 * Renders a single setting with appropriate UI based on its type.
 * Supports Toggle, Slider, Choice, and Complex types.
 *
 * Notes:
 * - When possible, the value should be validated/coerced to the expected type
 *   before calling `onValueChange`. UI components coerce incoming values, but
 *   long-term correctness is ensured by repository-level validation.
 * - Complex `SettingType.Complex` entries require a custom renderer registered in
 *   `SettingComponentRegistry` and should not be handled here.
 *
 * @param setting The setting to render
 * @param value The current value of the setting
 * @param onValueChange Callback when user changes the value
 */
@Composable
fun RenderSimpleSetting(
    setting: Setting,
    value: Any?,
    onValueChange: (Any) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header: Title
            Text(
                text = setting.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Description
            if (setting.description.isNotBlank()) {
                Text(
                    text = setting.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Control based on type
            Spacer(modifier = Modifier.height(8.dp))

            when (setting.type) {
                is SettingType.Toggle -> {
                    RenderToggleSetting(
                        value = (value as? Boolean) ?: (setting.defaultValue as Boolean),
                        onValueChange = onValueChange,
                    )
                }

                is SettingType.Slider -> {
                    val slider = setting.type as SettingType.Slider
                    RenderSliderSetting(
                        value = (value as? Number)?.toFloat() ?: (setting.defaultValue as Number).toFloat(),
                        onValueChange = onValueChange,
                        min = slider.min,
                        max = slider.max,
                        step = slider.step,
                    )
                }

                is SettingType.Choice -> {
                    val choice = setting.type as SettingType.Choice
                    RenderChoiceSetting(
                        value = value as? String ?: setting.defaultValue as String,
                        onValueChange = onValueChange,
                        options = choice.options,
                    )
                }

                is SettingType.Complex -> {
                    Text(
                        text = "Complex setting - requires custom renderer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Renders a Toggle setting as a Material3 Switch.
 */
@Composable
fun RenderToggleSetting(
    value: Boolean,
    onValueChange: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isChecked by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        isChecked = value
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = isChecked,
            onCheckedChange = { newValue ->
                isChecked = newValue
                onValueChange(newValue)
            },
        )
    }
}

/**
 * Renders a Slider setting with numeric input and range display.
 */
@Composable
fun RenderSliderSetting(
    value: Float,
    onValueChange: (Any) -> Unit,
    min: Float,
    max: Float,
    step: Float,
    _modifier: Modifier = Modifier,
) {
    SimpleSlider(
        value = value,
        onValueChange = { newValue -> onValueChange(newValue) },
        min = min,
        max = max,
        step = step,
    )
}

/**
 * Renders a Choice setting as a dropdown/radio selection.
 */
@Composable
fun RenderChoiceSetting(
    value: String,
    onValueChange: (Any) -> Unit,
    options: List<String>,
    _modifier: Modifier = Modifier,
) {
    ChoiceDropdown(
        value = value,
        onValueChange = { newValue -> onValueChange(newValue) },
        options = options,
    )
}
