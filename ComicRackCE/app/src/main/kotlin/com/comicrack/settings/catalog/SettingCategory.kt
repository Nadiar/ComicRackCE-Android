package com.comicrack.settings.catalog

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Enumeration of static setting categories.
 * These are the primary categories that settings belong to.
 */
enum class SettingCategory(
    val displayName: String,
) {
    GENERAL("General Settings"),
    READING("Reading & Display"),
    CONTROLS("Controls & Input"),
    WALLPAPER("Wallpaper"),
}
