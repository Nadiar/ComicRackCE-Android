package com.comicrack.settings.catalog

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Represents a single setting in the ComicRack settings catalog.
 *
 * @property id Unique identifier (used as preference key)
 * @property title Localized UI title
 * @property description Localized UI description
 * @property category Primary category
 * @property subcategory Optional subcategory for grouping
 * @property type The type of setting (Toggle, Slider, Choice, Complex)
 * @property defaultValue Default value when no preference is set
 * @property tags Language-specific tags for search and categorization (loaded from JSON)
 * @property icon Optional Material icon ID for UI display
 * @property isAdvanced If true, hidden in basic settings mode
 */
data class Setting(
    val id: String,
    val title: String,
    val description: String,
    val category: SettingCategory,
    val subcategory: String? = null,
    val type: SettingType,
    val defaultValue: Any,
    val tags: List<String> = emptyList(),
    val icon: Int? = null,
    val isAdvanced: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Setting id cannot be blank" }
        require(title.isNotBlank()) { "Setting title cannot be blank" }
        require(description.isNotBlank()) { "Setting description cannot be blank" }
    }
}
