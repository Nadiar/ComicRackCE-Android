package com.comicrack.data.database.repository

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import com.comicrack.data.database.dao.SettingsDao
import com.comicrack.data.database.entities.Settings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Settings.
 *
 * Wraps SettingsDao with coroutine-safe suspend functions for UI layer.
 * Provides Flow<Settings> for reactive UI updates.
 * Manages singleton pattern: Settings table always has one row (id=1).
 *
 * Repository pattern isolates database access from UI layer:
 * - UI/ViewModels call repository methods (not DAO directly)
 * - Repository calls DAO methods with appropriate dispatchers
 * - Flow operators ensure database updates reach UI efficiently
 *
 * Usage in ViewModel (observing settings):
 * ```kotlin
 * settingsRepository.observeSettings()
 *     .collect { settings ->
 *         updateUI(settings)
 *     }
 * ```
 *
 * Usage for single-shot reads:
 * ```kotlin
 * launch {
 *     val settings = settingsRepository.getSettings()
 *     val theme = settings?.theme ?: "system"
 * }
 * ```
 *
 * Project Epsilon: Preferences System
 * Phase 1: Repository Pattern for Settings
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val settingsDao: SettingsDao,
    ) {

        /**
         * Get current settings (suspend function).
         *
         * Returns null if settings haven't been initialized.
         * Call from coroutine context or use observeSettings() for reactive updates.
         *
         * @return Settings object, or null if not initialized
         */
        suspend fun getSettings(): Settings? = settingsDao.getSettings()

        /**
         * Observe settings reactively.
         *
         * Recommended for UI layer. Emits immediately with current settings,
         * then on each update. Automatically handles threading via Room.
         *
         * Usage:
         * ```kotlin
         * viewModel.settingsFlow = settingsRepository.observeSettings()
         *     .stateIn(
         *         scope = viewModelScope,
         *         started = SharingStarted.Lazily,
         *         initialValue = null
         *     )
         * ```
         *
         * @return Flow emitting Settings on changes (may be null)
         */
        fun observeSettings(): Flow<Settings?> = settingsDao.observeSettings()

        /**
         * Initialize settings with defaults if not already set.
         *
         * Safe to call multiple times (uses REPLACE conflict strategy).
         * Typically called once on app startup.
         *
         * @param settings The default Settings object
         */
        suspend fun initializeSettings(settings: Settings) {
            if (settingsDao.getSettingsCount() == 0) {
                settingsDao.insert(settings)
            }
        }

        /**
         * Update entire settings object.
         *
         * Caller is responsible for reading current settings, modifying fields,
         * and calling this method. Automatically updates modification timestamp.
         *
         * Example:
         * ```kotlin
         * launch {
         *     val current = settingsRepository.getSettings()
         *     current?.theme = "dark"
         *     current?.updateModifiedTime()
         *     settingsRepository.updateSettings(current)
         * }
         * ```
         *
         * @param settings The Settings object with updated values
         */
        suspend fun updateSettings(settings: Settings) {
            settings.updateModifiedTime()
            settingsDao.update(settings)
        }

        /**
         * Get theme setting (quick access without reading entire Settings).
         *
         * @return Theme string ("light", "dark", "system"), or null if not set
         */
        suspend fun getTheme(): String? = settingsDao.getTheme()

        /**
         * Get library view mode setting.
         *
         * @return View mode ("grid", "stack", "list"), or null if not set
         */
        suspend fun getLibraryViewMode(): String? = settingsDao.getLibraryViewMode()

        /**
         * Get library zoom level setting.
         *
         * @return Zoom level (0.5f to 2.0f)
         */
        suspend fun getLibraryZoomLevel(): Float = settingsDao.getLibraryZoomLevel()

        /**
         * Check if haptic feedback is enabled.
         *
         * @return true if haptic enabled, false otherwise
         */
        suspend fun isHapticFeedbackEnabled(): Boolean = settingsDao.isHapticFeedbackEnabled()

        /**
         * Get reader brightness setting.
         *
         * @return Brightness (0-100), 0 = system default
         */
        suspend fun getReaderBrightness(): Int = settingsDao.getReaderBrightness()

        /**
         * Reset all settings to factory defaults.
         *
         * Used for testing and cache reset.
         * In production, prefer updateSettings() to maintain audit trail.
         */
        suspend fun resetSettings() {
            settingsDao.deleteAll()
        }

        /**
         * Check if settings have been initialized.
         *
         * @return true if at least one settings row exists
         */
        suspend fun isInitialized(): Boolean = settingsDao.getSettingsCount() > 0
    }
