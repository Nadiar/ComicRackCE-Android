package com.comicrack.data.database.entities

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for application-wide settings and preferences.
 *
 * Stores user preferences that affect the library and viewer UI:
 * - Theme settings (dark mode, primary color)
 * - Library display settings (view mode, filter, sort)
 * - Haptic feedback toggle
 * - Reader defaults (brightness, scale mode)
 * - Wallpaper settings placeholder
 *
 * Single-instance table with ID always = 1 to maintain singleton pattern.
 * Timestamp tracks last modification for debugging and audit purposes.
 *
 * Project Epsilon: Preferences System
 * Phase 1: Room-based Settings Entity
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 *
 * @property id Primary key (always 1 for singleton pattern)
 * @property theme Theme setting ("light", "dark", "system")
 * @property useDynamicColor Use device wallpaper color palette (Material You)
 * @property libraryStoragePath Library storage path (SAF URI)
 * @property libraryViewMode Library view mode ("grid", "stack", "list")
 * @property libraryZoomLevel Current zoom level for library grid (0.5f to 2.0f)
 * @property libraryFilterMode Library filter mode ("all", "unread", "reading", "completed")
 * @property librarySortBy Library sort order ("title", "date", "series", "readProgress")
 * @property readerBrightness Reader brightness override (0-100)
 * @property readerScaleMode Reader scale mode ("fitPage", "fitWidth", "fitHeight", "original")
 * @property readerIsDoublePage Double-page reading mode for reader
 * @property readerIsRtl Right-to-left reading toggle
 * @property hapticFeedbackEnabled Haptic feedback toggle
 * @property gestureSensitivity Double-tap gesture sensitivity (0.5f to 1.5f)
 * @property wallpaperEnabled Live wallpaper enabled toggle
 * @property wallpaperAnimationSpeed Live wallpaper animation speed (0.5f to 2.0f)
 * @property wallpaperSeriesFilter Live wallpaper series filter
 * @property lastModifiedTime Timestamp of last modification
 * @property version Settings version for schema migration
 */
@Entity(tableName = "settings")
data class Settings(
    /**
     * Primary key (always 1 for singleton pattern).
     * This table contains exactly one row representing all global settings.
     */
    @PrimaryKey
    val id: Int = 1,
    // ========== GENERAL SETTINGS ==========
    /**
     * Theme setting.
     * Possible values: "light", "dark", "system" (default)
     * Affects Material Design 3 color scheme applied to all screens.
     */
    @ColumnInfo(name = "theme")
    var theme: String = "system",
    /**
     * Dynamic color toggle.
     * true = Use device wallpaper color palette (Material You)
     * false = Use standard Material Design 3 colors
     * Only applicable on Android 12+
     */
    @ColumnInfo(name = "useDynamicColor")
    var useDynamicColor: Boolean = true,
    /**
     * Library storage path (SAF URI).
     * Stores the selected root directory for comic library discovery.
     * Format: "content://com.android.externalstorage.documents/tree/..."
     */
    @ColumnInfo(name = "libraryStoragePath")
    var libraryStoragePath: String = "",
    // ========== LIBRARY DISPLAY SETTINGS ==========
    /**
     * Library view mode.
     * Possible values: "grid" (default), "stack", "list"
     * Determines how ComicBook items are displayed in LibraryScreen.
     * Grid = 3-column responsive grid with zoom support
     * Stack = Vertical stacked cards with pagination
     * List = Simple list view
     */
    @ColumnInfo(name = "libraryViewMode")
    var libraryViewMode: String = "grid",
    /**
     * Current zoom level for library grid.
     * Range: 0.5f to 2.0f
     * Default: 1.0f (3 columns on standard phone)
     * Persisted to restore user's preferred zoom level on app restart.
     */
    @ColumnInfo(name = "libraryZoomLevel")
    var libraryZoomLevel: Float = 1.0f,
    /**
     * Library filter mode.
     * Possible values: "all" (default), "unread", "reading", "completed"
     * Controls which comics are shown in LibraryScreen.
     */
    @ColumnInfo(name = "libraryFilterMode")
    var libraryFilterMode: String = "all",
    /**
     * Library sort order.
     * Possible values: "title", "date", "series" (default), "readProgress"
     * Determines the order of comics displayed in LibraryScreen.
     */
    @ColumnInfo(name = "librarySortBy")
    var librarySortBy: String = "series",
    // ========== READER SETTINGS ==========
    /**
     * Reader brightness override (0-100).
     * 0 = Use system brightness (default)
     * 1-100 = Force custom brightness in ComicRack.Viewer
     * Stored in ReadProgress and SeriesPreferences for per-series overrides.
     */
    @ColumnInfo(name = "readerBrightness")
    var readerBrightness: Int = 0,
    /**
     * Reader scale mode.
     * Possible values: "fitPage" (default), "fitWidth", "fitHeight", "original"
     * Affects how comic pages are scaled to the screen.
     * fitPage = Entire page visible
     * fitWidth = Page width matches screen width
     * fitHeight = Page height matches screen height
     * original = 100% zoom (may require panning)
     */
    @ColumnInfo(name = "readerScaleMode")
    var readerScaleMode: String = "fitPage",
    /**
     * Double-page reading mode for reader.
     * true = Display two pages side-by-side (landscape optimal)
     * false = Display one page per screen (default)
     * Per-series overrides available in SeriesPreferences.
     */
    @ColumnInfo(name = "readerIsDoublePage")
    var readerIsDoublePage: Boolean = false,
    /**
     * Right-to-left reading toggle (for manga/international comics).
     * true = RTL page navigation and layout
     * false = LTR (default, Western comics)
     * Per-series overrides available in SeriesPreferences.
     */
    @ColumnInfo(name = "readerIsRtl")
    var readerIsRtl: Boolean = false,
    // ========== INPUT & FEEDBACK SETTINGS ==========
    /**
     * Haptic feedback toggle.
     * true = Enable haptic feedback on gestures and interactions (default)
     * false = Disable all haptic feedback
     * Affects long-press on cards, gesture feedback, etc.
     * Uses View.performHapticFeedback() with HapticFeedbackConstants.
     */
    @ColumnInfo(name = "hapticFeedbackEnabled")
    var hapticFeedbackEnabled: Boolean = true,
    /**
     * Double-tap gesture sensitivity.
     * Range: 0.5f (very sensitive) to 1.5f (less sensitive)
     * Default: 1.0f
     * Affects gesture debouncing and minimum distance thresholds.
     */
    @ColumnInfo(name = "gestureSensitivity")
    var gestureSensitivity: Float = 1.0f,
    // ========== WALLPAPER SETTINGS ==========
    /**
     * Live wallpaper enabled toggle.
     * true = Enable ComicRack live wallpaper service
     * false = Disable (default)
     * User selects wallpaper through Android wallpaper picker.
     */
    @ColumnInfo(name = "wallpaperEnabled")
    var wallpaperEnabled: Boolean = false,
    /**
     * Live wallpaper animation speed.
     * Range: 0.5f (slow) to 2.0f (fast)
     * Default: 1.0f
     * Affects transition duration between comics in wallpaper mode.
     */
    @ColumnInfo(name = "wallpaperAnimationSpeed")
    var wallpaperAnimationSpeed: Float = 1.0f,
    /**
     * Live wallpaper series filter (optional).
     * Empty string = Show all comics
     * Non-empty = Show only comics from this series
     * User can set series filter through settings UI.
     */
    @ColumnInfo(name = "wallpaperSeriesFilter")
    var wallpaperSeriesFilter: String = "",
    // ========== METADATA ==========
    /**
     * Timestamp (milliseconds since epoch) of last settings modification.
     * Updated whenever any setting changes.
     * Used for debugging and potential cloud sync features.
     */
    @ColumnInfo(name = "lastModifiedTime")
    var lastModifiedTime: Long = System.currentTimeMillis(),
    /**
     * Settings version (for schema migration).
     * Incremented when settings structure changes.
     * Default: 1
     */
    @ColumnInfo(name = "version")
    var version: Int = 1,
) {
    /**
     * Update last modified timestamp.
     */
    fun updateModifiedTime() {
        lastModifiedTime = System.currentTimeMillis()
    }

    override fun toString(): String =
        "Settings(id=$id, theme='$theme', useDynamicColor=$useDynamicColor, " +
            "libraryViewMode='$libraryViewMode', libraryZoomLevel=$libraryZoomLevel, " +
            "readerBrightness=$readerBrightness, hapticFeedbackEnabled=$hapticFeedbackEnabled, " +
            "lastModifiedTime=$lastModifiedTime)"
}
