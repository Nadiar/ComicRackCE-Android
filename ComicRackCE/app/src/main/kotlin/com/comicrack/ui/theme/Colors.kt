/**
 * UI color definitions for Light and Dark themes.
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
package com.comicrack.ui.theme

import androidx.compose.ui.graphics.Color

// Light Mode Colors
object LightColors {
    val Primary = Color(0xFFC9A961)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFE8D5B7)
    val OnPrimaryContainer = Color(0xFF3E2C1A)

    // Updated Light Mode: Gold Primary, Blue Secondary (Brand)
    val Secondary = Color(0xFF1F77D4) // ComicRack Blue
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFD1E4FF)
    val OnSecondaryContainer = Color(0xFF001D36)

    val Tertiary = Color(0xFF666666)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFF999999)
    val OnTertiaryContainer = Color(0xFFFFFFFF)

    val Background = Color(0xFFFAFAF8)
    val OnBackground = Color(0xFF1A1A1A)
    val Surface = Color(0xFFFAFAF8)
    val OnSurface = Color(0xFF1A1A1A)

    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)
}

// Dark Mode Colors
object DarkColors {
    val Primary = Color(0xFF4DA3FF) // Lighter blue for better contrast
    val OnPrimary = Color(0xFF003258)
    val PrimaryContainer = Color(0xFF00497D)
    val OnPrimaryContainer = Color(0xFFD1E4FF)

    // Swapped Secondary/Tertiary to give Cyan prominence (as requested)
    // Secondary is now Cyan (Teal)
    val Secondary = Color(0xFF03DAC5)
    val OnSecondary = Color(0xFF003732)
    val SecondaryContainer = Color(0xFF005049)
    val OnSecondaryContainer = Color(0xFF70F7EB)

    // Tertiary is now Indigo
    val Tertiary = Color(0xFF6366F1)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFF4F46E5)
    val OnTertiaryContainer = Color(0xFFE0E7FF)

    val Background = Color(0xFF121212)
    val OnBackground = Color(0xFFFFFFFF)
    val Surface = Color(0xFF1E1E1E)
    val OnSurface = Color(0xFFFFFFFF)

    val Error = Color(0xFFF2B8B5)
    val OnError = Color(0xFF601410)
}
