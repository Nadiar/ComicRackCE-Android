package com.comicrack.settings.catalog

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Sealed class representing the type of a setting.
 * Each setting has a type that determines how it's rendered and stored.
 */
sealed class SettingType {
    /**
     * Boolean toggle setting (Switch/Checkbox).
     *
     * Represents a simple on/off preference.
     */
    object Toggle : SettingType()

    /**
     * Numeric slider setting with min/max bounds.
     *
     * @property min The minimum allowed value (inclusive).
     * @property max The maximum allowed value (inclusive).
     * @property step The increment between consecutive values. Must be positive and no greater than (max - min).
     */
    data class Slider(
        val min: Float,
        val max: Float,
        val step: Float,
    ) : SettingType() {
        init {
            require(min < max) { "Min must be less than max" }
            require(step > 0) { "Step must be positive" }
            require(step <= (max - min)) { "Step must not exceed range" }
        }
    }

    /**
     * Choice selector (Dropdown/List).
     *
     * @property options A non-empty list of option labels presented to the user. Maximum recommended size is 20.
     */
    data class Choice(
        val options: List<String>,
    ) : SettingType() {
        init {
            require(options.isNotEmpty()) { "Options list cannot be empty" }
            require(options.size <= 20) { "Too many options (max 20)" }
        }
    }

    /**
     * Complex custom UI component (e.g., TapZones, SAF picker).
     *
     * Requires custom rendering via the app's component registry.
     */
    object Complex : SettingType()
}
