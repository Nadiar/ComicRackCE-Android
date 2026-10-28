package com.comicrack.settings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comicrack.settings.catalog.Setting

/**
 * Type alias for a custom renderer function.
 * Takes a setting, current value, and change callback.
 */
typealias SettingRenderer = @Composable (
    setting: Setting,
    value: Any?,
    onValueChange: (Any) -> Unit,
    modifier: Modifier,
) -> Unit

/**
 * Component Registry for mapping settings to specialized renderers.
 *
 * This registry allows custom renderers to be registered for specific settings
 * without modifying the RenderSetting factory directly.
 *
 * **Phase 2.1**: Registry structure only
 * **Phase 2.2**: Register TapZoneEditor, SAFPicker, ColorPicker, etc.
 * **Phase 3+**: Add more custom components dynamically
 *
 * Usage:
 * ```kotlin
 * val renderer = SettingComponentRegistry.getRenderer(setting)
 * renderer?.Invoke(setting, value, onValueChange)
 * ```
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
object SettingComponentRegistry {
    /**
     * Registry mapping setting IDs to their custom renderers.
     * Populated by register() calls during app initialization.
     */
    private val customRenderers = mutableMapOf<String, SettingRenderer>()

    /**
     * Register a custom renderer for a specific setting ID.
     *
     * Called during app initialization to bind custom components to settings.
     *
     * @param settingId The setting ID (e.g., "tap_zone_left_action")
     * @param renderer The composable renderer function
     */
    fun register(
        settingId: String,
        renderer: SettingRenderer,
    ) {
        customRenderers[settingId] = renderer
    }

    /**
     * Get the custom renderer for a setting, if one is registered.
     *
     * @param setting The setting to look up
     * @return The custom renderer, or null if no custom renderer is registered
     */
    fun getRenderer(setting: Setting): SettingRenderer? = customRenderers[setting.id]

    /**
     * Check if a setting has a custom renderer registered.
     *
     * @param setting The setting to check
     * @return True if a custom renderer is registered
     */
    fun hasCustomRenderer(setting: Setting): Boolean = customRenderers.containsKey(setting.id)

    /**
     * Get all registered custom renderer IDs.
     *
     * Useful for debugging and understanding which settings have custom UI.
     *
     * @return Set of setting IDs with custom renderers
     */
    fun getRegisteredIds(): Set<String> = customRenderers.keys.toSet()

    /**
     * Clear all registered renderers.
     *
     * Typically used in tests to reset state between test cases.
     */
    fun clear() {
        customRenderers.clear()
    }
}

/**
 * Render a setting using the component registry.
 *
 * If a custom renderer is registered for this setting, use it.
 * Otherwise, fall back to the default RenderSetting factory.
 *
 * @param setting The setting to render
 * @param value The current value
 * @param onValueChange Callback when value changes
 * @param modifier Optional layout modifier
 */
@Composable
fun RenderSettingWithRegistry(
    setting: Setting,
    value: Any?,
    onValueChange: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val customRenderer = SettingComponentRegistry.getRenderer(setting)

    if (customRenderer != null) {
        // Use custom renderer if registered
        customRenderer(setting, value, onValueChange, modifier)
    } else {
        // Fall back to default factory renderer
        RenderSetting(
            setting = setting,
            value = value,
            onValueChange = onValueChange,
            _modifier = modifier,
        )
    }
}
