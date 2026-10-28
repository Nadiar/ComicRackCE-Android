package com.comicrack.data.database.entities

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for per-series reader preferences.
 *
 * Stores settings that should be consistent across all issues of a series:
 * - Brightness level
 * - Reading mode (single vs two-page)
 * - Text direction (RTL for manga/international comics)
 *
 * Keyed by series name to allow consistent experience when reading multiple issues.
 * Timestamp tracks when series was last read (for priority sorting).
 *
 * Phase B: Library Preferences System
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 *
 * @property id Auto-generated primary key
 * @property seriesName Series name (e.g., "Buffy the Last Vampire Slayer")
 * @property brightness Brightness override for this series (0-100)
 * @property isDoublePage Reading mode toggle (false = single page, true = two pages)
 * @property isRtl Right-to-left reading toggle
 * @property lastReadTime Timestamp of last read action in this series
 */
@Entity(
    tableName = "series_preferences",
    indices = [
        Index("seriesName"),
    ],
)
data class SeriesPreferences(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /**
     * Series name (e.g., "Buffy the Last Vampire Slayer").
     * Should match ComicBook.series for consistency.
     * Unique key for lookups.
     */
    @ColumnInfo(name = "seriesName")
    val seriesName: String = "",
    /**
     * Brightness override for this series (0-100).
     * 0 = system default
     * 1-100 = custom brightness level
     * Optional: can use global reader brightness if set to 0
     */
    var brightness: Int = 0,
    /**
     * Reading mode toggle:
     * false = single page per screen (default)
     * true = two pages side-by-side (for landscape/tablets)
     */
    var isDoublePage: Boolean = false,
    /**
     * Right-to-left reading toggle.
     * false = left-to-right (default, Western comics)
     * true = right-to-left (manga, some international comics)
     * Affects page navigation direction (left arrow = next page)
     */
    var isRtl: Boolean = false,
    /**
     * Timestamp (milliseconds since epoch) of last read action in this series.
     * Used for sorting "recently reading" series.
     */
    var lastReadTime: Long = System.currentTimeMillis(),
) {
    /**
     * Update last read timestamp to now.
     */
    fun updateLastReadTime() {
        lastReadTime = System.currentTimeMillis()
    }
}
