package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.comicrack.data.database.entities.SeriesPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for SeriesPreferences entities.
 *
 * Manages per-series reader settings (brightness, reading mode, text direction).
 * Allows consistent experience across all issues of a series.
 *
 * Phase B: Library Preferences System
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 */
@Dao
interface SeriesPreferencesDao {
    /**
     * Insert a new series preferences record.
     * On conflict, keep existing record (fail silently).
     *
     * @param preferences The SeriesPreferences entity
     * @return The row ID of the inserted record
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(preferences: SeriesPreferences): Long

    /**
     * Update an existing series preferences record.
     *
     * @param preferences The SeriesPreferences with updated values
     * @return Number of rows updated
     */
    @Update
    suspend fun update(preferences: SeriesPreferences): Int

    /**
     * Delete series preferences.
     *
     * @param preferences The SeriesPreferences to delete
     * @return Number of rows deleted
     */
    @Delete
    suspend fun delete(preferences: SeriesPreferences): Int

    /**
     * Get preferences for a specific series.
     * Returns null if series preferences haven't been created yet.
     *
     * @param seriesName The series name to look up
     * @return The SeriesPreferences, or null if not found
     */
    @Query("SELECT * FROM series_preferences WHERE seriesName = :seriesName LIMIT 1")
    suspend fun getPreferences(seriesName: String): SeriesPreferences?

    /**
     * Observe preferences for a specific series.
     * Reactive - updates when preferences change.
     * Emits null if series preferences haven't been created yet.
     *
     * @param seriesName The series name to observe
     * @return Flow of SeriesPreferences (reactive)
     */
    @Query("SELECT * FROM series_preferences WHERE seriesName = :seriesName LIMIT 1")
    fun observePreferences(seriesName: String): Flow<SeriesPreferences?>

    /**
     * Get or create preferences for a series.
     * If preferences don't exist, inserts default preferences.
     *
     * @param seriesName The series name
     * @return The existing or newly created SeriesPreferences
     */
    @Query("SELECT * FROM series_preferences WHERE seriesName = :seriesName LIMIT 1")
    suspend fun getOrCreate(seriesName: String): SeriesPreferences?

    /**
     * Get all series preferences, sorted by most recently read.
     *
     * @return List of all SeriesPreferences
     */
    @Query("SELECT * FROM series_preferences ORDER BY lastReadTime DESC")
    suspend fun getAllPreferences(): List<SeriesPreferences>

    /**
     * Observe all series preferences, sorted by most recently read.
     * Reactive - updates when any series preferences change.
     *
     * @return Flow of all SeriesPreferences
     */
    @Query("SELECT * FROM series_preferences ORDER BY lastReadTime DESC")
    fun observeAllPreferences(): Flow<List<SeriesPreferences>>

    /**
     * Update last read timestamp for a series.
     * Called when entering the reader to track recent reading.
     *
     * @param seriesName The series name
     * @param now Current timestamp
     */
    @Query("UPDATE series_preferences SET lastReadTime = :now WHERE seriesName = :seriesName")
    suspend fun updateLastReadTime(
        seriesName: String,
        now: Long,
    )

    /**
     * Check if a series has any custom preferences set.
     * Returns true if brightness != 0 OR isDoublePage OR isRtl.
     *
     * @param seriesName The series name
     * @return true if series has custom settings
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM series_preferences
            WHERE seriesName = :seriesName
            AND (brightness != 0 OR isDoublePage = 1 OR isRtl = 1)
        )
    """,
    )
    suspend fun hasCustomSettings(seriesName: String): Boolean

    /**
     * Delete preferences for a series.
     * Useful if user wants to reset all settings for a series.
     *
     * @param seriesName The series name
     */
    @Query("DELETE FROM series_preferences WHERE seriesName = :seriesName")
    suspend fun deletePreferences(seriesName: String)

    /**
     * Clear all preferences (complete reset).
     * Useful for testing or user-requested clear.
     */
    @Query("DELETE FROM series_preferences")
    suspend fun clearAll()
}
