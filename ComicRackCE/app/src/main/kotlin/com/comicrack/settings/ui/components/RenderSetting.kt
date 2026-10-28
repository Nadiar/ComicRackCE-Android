package com.comicrack.settings.ui.components

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comicrack.settings.catalog.Setting
import com.comicrack.settings.catalog.SettingType

/**
 * Factory function that selects the appropriate composable renderer for a setting.
 *
 * This is the central dispatch point for rendering any setting from the catalog. It
 * converts a catalog entry ("Setting") into the appropriate UI for editing and
 * displaying the value. Prefer small, focused renderers (e.g., `RenderSimpleSetting`)
 * to keep individual components testable and themable.
 *
 * Phases:
 * - Phase 2.1: Basic types (Toggle, Slider, Choice) are supported and rendered by
 *   `RenderSimpleSetting`.
 * - Phase 2.2: Complex types (TapZoneEditor, SAF picker, ColorPicker, etc.) will be
 *   dispatched to specialized composables. Until those are implemented we show a
 *   placeholder to avoid crashing the settings screen.
 *
 * Usage:
 * - Use `setting.id` to decide which specialized renderer to return for complex
 *   types.
 * - Keep renderers free of heavy logic; convert business rules into `Setting`
 *   validation when possible.
 *
 * @param setting The `Setting` catalog entry to render. Must describe the type and
 *   UI hints required to render a value.
 * @param value The current value for this setting, or `null` when unavailable.
 * @param onValueChange Callback invoked when the user updates the value. The
 *   implementation should coerce the incoming value to the expected type when
 *   necessary.
 * @param modifier Optional layout modifier applied to the renderer.
 */
@Composable
fun RenderSetting(
    setting: Setting,
    value: Any?,
    onValueChange: (Any) -> Unit,
    _modifier: Modifier = Modifier,
) {
    when (setting.type) {
        // Simple types: render with RenderSimpleSetting
        is SettingType.Toggle,
        is SettingType.Slider,
        is SettingType.Choice,
        -> {
            RenderSimpleSetting(
                setting = setting,
                value = value,
                onValueChange = onValueChange,
            )
        }

        // Complex types: will dispatch to specialized renderers (Phase 2.2+)
        is SettingType.Complex -> {
            // Phase 2.2: Will dispatch based on setting.id
            // Examples (not exhaustive):
            // - "tap_zone_*" â†’ TapZoneEditor
            // - "library_path" â†’ SAFPickerComponent
            // - "theme_color" â†’ ColorPickerComponent
            // For now, show a safe placeholder that uses the catalog default.
            RenderComplexSettingPlaceholder(setting = setting)
        }
    }
}

/**
 * Temporary placeholder renderer for complex `Setting` entries that do not yet
 * have a dedicated component.
 *
 * This deliberately renders the catalog `defaultValue` and provides a no-op
 * `onValueChange` to avoid surprising side-effects (saving/validation) from the
 * placeholder UI. Replace this with a proper renderer when implementing Phase
 * 2.2 components.
 *
 * @param setting The complex `Setting` to render as a placeholder.
 */
@Composable
fun RenderComplexSettingPlaceholder(setting: Setting) {
    RenderSimpleSetting(
        setting = setting,
        value = setting.defaultValue,
        onValueChange = {},
    )
}
