package com.comicrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Theme Manager - Observes app theme preferences and resolves to actual dark/light mode
 *
 * Handles:
 * - System theme detection (Android 10+)
 * - Manual light/dark mode selection
 * - Live theme switching via preference updates
 *
 * Usage:
 * ```kotlin
 * val themeManager = ThemeManager(repository)
 * val isDarkMode by themeManager.isDarkModeFlow().collectAsState(initial = null)
 * ```
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
class ThemeManager(
    private val preferencesRepository: AppPreferencesRepository,
) {
    /**
     * Flows the current dark mode state based on app theme preference
     * Considers system theme when APP_THEME is set to SYSTEM
     *
     * Returns:
     * - null: System should decide (isSystemInDarkTheme())
     * - true: Force dark mode
     * - false: Force light mode
     */
    fun isDarkModeFlow(): Flow<Boolean?> =
        preferencesRepository.appTheme.map { appTheme ->
            when (appTheme) {
                AppTheme.SYSTEM -> null // Let system decide
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
            }
        }

    /**
     * Composable helper to get dark mode state at composition time
     * Respects system theme when app theme is SYSTEM
     *
     * @param systemDarkMode Current system dark mode state (from isSystemInDarkTheme())
     * @return The resolved dark mode to use for theming
     */
    @Composable
    fun isDarkModeComposable(systemDarkMode: Boolean = isSystemInDarkTheme()): Boolean {
        val themePref = isDarkModeFlow().collectAsState(initial = null)
        return themePref.value ?: systemDarkMode
    }
}
