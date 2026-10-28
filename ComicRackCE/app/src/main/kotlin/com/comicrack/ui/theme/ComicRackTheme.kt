package com.comicrack.ui.theme

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * ComicRack Theme - Delegates to M3E implementation
 *
 * **DEPRECATED**: This file maintains backward compatibility.
 * All apps should use [ComicRackMaterial3Theme] directly.
 *
 * This composable now delegates to the proper M3E theme system with:
 * - Dynamic color support (Material You on Android 12+)
 * - Proper color role definitions
 * - Consistent branding across the app
 *
 * @param isDarkMode Optional override for dark mode (used for testing/preview)
 * @param content The composable content to apply the theme to
 */
@Composable
fun ComicRackTheme(
    isDarkMode: Boolean? = null,
    content: @Composable () -> Unit,
) {
    ComicRackMaterial3Theme(
        darkTheme = isDarkMode ?: isSystemInDarkTheme(),
        dynamicColor = false, // Disable Material You to ensure consistent branding/visibility
        content = content,
    )
}
