package com.comicrack.ui.theme

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * ComicRack Material 3 Expressive Shapes
 *
 * Follows M3 "Expressive" guidelines:
 * - Extra Small: 4dp (Tags)
 * - Small: 8dp (Buttons, Chips)
 * - Medium: 16dp (Cards, Small Dialogs)
 * - Large: 24dp (Action Sheets)
 * - Extra Large: 32dp (Large Dialogs)
 */
val Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
