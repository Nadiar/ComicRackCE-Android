package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.comicrack.data.database.entities.Settings
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for Settings entity.
 *
 * Provides methods to read/write application settings with Room database.
 * Settings are cached in-memory by Room and reactively observed via Flow.
 *
 * Single-instance pattern: Settings table contains exactly one row (id = 1).
 *
 * Suspending functions for coroutine-safe database operations.
 * Flow-based observers for reactive UI updates.
 *
 * Project Epsilon: Preferences System
 * Phase 1: Room-based DAO Layer
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 */
@Dao
interface SettingsDao {
    /**
     * Insert settings record (typically called once on app initialization).
     * Uses REPLACE strategy to handle case where id=1 already exists.
     *
     * @param settings The Settings object to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: Settings)

    /**
     * Update entire settings record.
     * Typically called when multiple settings change at once.
     *
     * @param settings The Settings object with updated values
     */
    @Update
    suspend fun update(settings: Settings)

    /**
     * Retrieve all settings (always one row with id=1).
     * Returns null if no settings have been initialized.
     *
     * Use observeSettings() for reactive updates instead of this method.
     *
     * @return The current Settings object, or null
     */
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): Settings?

    /**
     * Observe settings changes reactively.
     * Emits current Settings object immediately, then on each update.
     * Returns empty Flow if no settings exist (should not happen in normal operation).
     *
     * Use this in UI layer to subscribe to settings changes:
     * ```kotlin
     * viewModel.observeSettings()
     *     .collect { settings -> updateUI(settings) }
     * ```
     *
     * @return Flow emitting Settings updates
     */
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<Settings?>

    /**
     * Get a specific setting value by field name.
     * Example: SELECT theme FROM settings WHERE id = 1
     *
     * @return The theme string, or "system" if not set
     */
    @Query("SELECT theme FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getTheme(): String?

    /**
     * Get library view mode.
     *
     * @return View mode string ("grid", "stack", "list")
     */
    @Query("SELECT libraryViewMode FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getLibraryViewMode(): String?

    /**
     * Get library zoom level.
     *
     * @return Zoom level (0.5f to 2.0f, default 1.0f)
     */
    @Query("SELECT libraryZoomLevel FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getLibraryZoomLevel(): Float

    /**
     * Get haptic feedback enabled state.
     *
     * @return true if haptic feedback enabled, false otherwise
     */
    @Query("SELECT hapticFeedbackEnabled FROM settings WHERE id = 1 LIMIT 1")
    suspend fun isHapticFeedbackEnabled(): Boolean

    /**
     * Get reader brightness setting.
     *
     * @return Brightness level (0-100), 0 = system default
     */
    @Query("SELECT readerBrightness FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getReaderBrightness(): Int

    /**
     * Delete all settings (resets to factory defaults).
     * Used for testing and cache reset.
     * In production, prefer update() to maintain audit trail.
     */
    @Query("DELETE FROM settings")
    suspend fun deleteAll()

    /**
     * Check if settings have been initialized.
     * Returns count of settings rows (should be 0 or 1).
     *
     * @return 1 if initialized, 0 otherwise
     */
    @Query("SELECT COUNT(*) FROM settings")
    suspend fun getSettingsCount(): Int
}
