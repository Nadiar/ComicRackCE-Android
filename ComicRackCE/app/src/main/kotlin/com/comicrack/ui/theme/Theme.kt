package com.comicrack.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Light mode: Gold brand colors
private val LightColorScheme =
    lightColorScheme(
        primary = LightColors.Primary,
        onPrimary = LightColors.OnPrimary,
        primaryContainer = LightColors.PrimaryContainer,
        onPrimaryContainer = LightColors.OnPrimaryContainer,
        secondary = LightColors.Secondary,
        onSecondary = LightColors.OnSecondary,
        secondaryContainer = LightColors.SecondaryContainer,
        onSecondaryContainer = LightColors.OnSecondaryContainer,
        tertiary = LightColors.Tertiary,
        onTertiary = LightColors.OnTertiary,
        tertiaryContainer = LightColors.TertiaryContainer,
        onTertiaryContainer = LightColors.OnTertiaryContainer,
        background = LightColors.Background,
        onBackground = LightColors.OnBackground,
        surface = LightColors.Surface,
        onSurface = LightColors.OnSurface,
        error = LightColors.Error,
        onError = LightColors.OnError,
    )

// Dark mode: Deep blue sophisticated colors
private val DarkColorScheme =
    darkColorScheme(
        primary = DarkColors.Primary,
        onPrimary = DarkColors.OnPrimary,
        primaryContainer = DarkColors.PrimaryContainer,
        onPrimaryContainer = DarkColors.OnPrimaryContainer,
        secondary = DarkColors.Secondary,
        onSecondary = DarkColors.OnSecondary,
        secondaryContainer = DarkColors.SecondaryContainer,
        onSecondaryContainer = DarkColors.OnSecondaryContainer,
        tertiary = DarkColors.Tertiary,
        onTertiary = DarkColors.OnTertiary,
        tertiaryContainer = DarkColors.TertiaryContainer,
        onTertiaryContainer = DarkColors.OnTertiaryContainer,
        background = DarkColors.Background,
        onBackground = DarkColors.OnBackground,
        surface = DarkColors.Surface,
        onSurface = DarkColors.OnSurface,
        error = DarkColors.Error,
        onError = DarkColors.OnError,
    )

/**
 * ComicRack Material Design 3 Expressive Theme
 *
 * Supports:
 * - Light/Dark mode switching
 * - Dynamic Color (Material You) on Android 12+
 * - Full Material Design 3 color roles
 *
 * Architecture:
 * - Color.kt: Individual color definitions (brand identity)
 * - Theme.kt (this file): ColorScheme composition + dynamic color support
 * - XML: System-level colors (splash screen, status bar only)
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Composable
fun ComicRackMaterial3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Changed default to false to prioritize Brand Identity
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            // Dynamic color (Material You) on Android 12+
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            // Fallback to custom color schemes
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
