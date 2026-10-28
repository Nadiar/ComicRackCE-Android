package com.comicrack.data.database.entities

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for parsed metadata from ComicInfo.xml / MetronInfo.xml.
 *
 * This entity stores metadata extracted from archive XML files and tracks parsing state.
 * It acts as a supplementary table to ComicBook, allowing for incremental metadata loading
 * and independent schema evolution.
 *
 * Metadata Fields Strategy:
 * - Multi-value fields (Writer, Penciller, etc.) are stored as JSON arrays
 * - Parsed timestamp allows cache invalidation
 * - Source format (ComicInfo vs MetronInfo) helps with conflict resolution
 *
 * Phase 1: Database Schema (Kotlin)
 */
@Entity(
    tableName = "comic_metadata",
    foreignKeys = [
        ForeignKey(
            entity = ComicBook::class,
            parentColumns = ["id"],
            childColumns = ["comicBookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("comicBookId", unique = true), // One metadata per comic
    ],
)
data class ComicMetadata(
    /**
     * Foreign key reference to the ComicBook this metadata belongs to.
     */
    @ColumnInfo(name = "comicBookId")
    @JvmField
    var comicBookId: Long = 0,
    @PrimaryKey(autoGenerate = true)
    @JvmField
    var id: Long = 0,
    /**
     * Timestamp when metadata was last parsed (milliseconds since epoch).
     * Used for cache validation and metadata freshness tracking.
     */
    @ColumnInfo(name = "parsedAt")
    @JvmField
    var parsedAt: Long = 0,
    /**
     * Source format of the metadata: "ComicInfo" or "MetronInfo" or "hybrid".
     * Helps understand which XML file was primary source.
     */
    @ColumnInfo(name = "sourceFormat")
    @JvmField
    var sourceFormat: String = "",
    // === Core Metadata Fields ===
    /**
     * Comic title/issue title (parsed from XML, may differ from file title).
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "title")
    @JvmField
    var title: String? = null,
    /**
     * Series name (parsed from XML).
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "series")
    @JvmField
    var series: String? = null,
    /**
     * Issue number as string (e.g., "001", "2.5", "Annual 2023").
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "number")
    @JvmField
    var number: String? = null,
    /**
     * Total issues in series.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "count")
    @JvmField
    var count: Int? = null,
    /**
     * Volume number.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "volume")
    @JvmField
    var volume: Int? = null,
    /**
     * Story arc name (e.g., "Civil War", "Infinity Gauntlet").
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "storyArc")
    @JvmField
    var storyArc: String? = null,
    /**
     * Series group (e.g., "Marvel Universe", "DC Universe").
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "seriesGroup")
    @JvmField
    var seriesGroup: String? = null,
    /**
     * Plot summary/synopsis.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "summary")
    @JvmField
    var summary: String? = null,
    /**
     * User notes or scraper notes.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "notes")
    @JvmField
    var notes: String? = null,
    /**
     * Publication year.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "year")
    @JvmField
    var year: Int? = null,
    /**
     * Publication month (1-12).
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "month")
    @JvmField
    var month: Int? = null,
    /**
     * Publication day (1-31).
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "day")
    @JvmField
    var day: Int? = null,
    // === Personnel Fields (JSON Arrays) ===
    // These fields store JSON arrays of names for multi-value support
    // Example: ["writer1", "writer2", "writer3"]
    // Moshi handles serialization/deserialization
    /**
     * Writers/Authors (JSON array).
     * Example: ["Stan Lee", "Jack Kirby"]
     */
    @ColumnInfo(name = "writers")
    @JvmField
    var writers: String? = null, // JSON array: ["name1", "name2"]
    /**
     * Pencil artists (JSON array).
     */
    @ColumnInfo(name = "pencillers")
    @JvmField
    var pencillers: String? = null,
    /**
     * Inkers (JSON array).
     */
    @ColumnInfo(name = "inkers")
    @JvmField
    var inkers: String? = null,
    /**
     * Colorists (JSON array).
     */
    @ColumnInfo(name = "colorists")
    @JvmField
    var colorists: String? = null,
    /**
     * Letterers (JSON array).
     */
    @ColumnInfo(name = "letterers")
    @JvmField
    var letterers: String? = null,
    /**
     * Cover artists (JSON array).
     */
    @ColumnInfo(name = "coverArtists")
    @JvmField
    var coverArtists: String? = null,
    /**
     * Editors (JSON array).
     */
    @ColumnInfo(name = "editors")
    @JvmField
    var editors: String? = null,
    // === Publishing Info ===
    /**
     * Publisher name.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "publisher")
    @JvmField
    var publisher: String? = null,
    /**
     * Publisher imprint/label.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "imprint")
    @JvmField
    var imprint: String? = null,
    /**
     * Genres (JSON array).
     * Example: ["Superhero", "Action", "Comics"]
     */
    @ColumnInfo(name = "genres")
    @JvmField
    var genres: String? = null,
    /**
     * Publication website URL.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "web")
    @JvmField
    var web: String? = null,
    /**
     * Page count (number of images in archive).
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "pageCount")
    @JvmField
    var pageCount: Int? = null,
    /**
     * ISO language code (e.g., "en", "fr", "de").
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "languageISO")
    @JvmField
    var languageISO: String? = null,
    /**
     * Format description (e.g., "Tpb" = Trade Paperback, "Digital").
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "format")
    @JvmField
    var format: String? = null,
    /**
     * Age rating (e.g., "4+", "Teen", "16+", "18+").
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "ageRating")
    @JvmField
    var ageRating: String? = null,
    /**
     * Whether the comic is black and white.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "blackAndWhite")
    @JvmField
    var blackAndWhite: Boolean? = null,
    /**
     * Whether the comic is in manga format (right-to-left reading).
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "manga")
    @JvmField
    var manga: Boolean? = null,
    /**
     * Community rating (0.0-10.0 scale, typically).
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "communityRating")
    @JvmField
    var communityRating: Float? = null,
    // === Content Fields (JSON Arrays) ===
    /**
     * Characters featured (JSON array).
     * Example: ["Spider-Man", "Iron Man", "Doctor Strange"]
     */
    @ColumnInfo(name = "characters")
    @JvmField
    var characters: String? = null,
    /**
     * Teams featured (JSON array).
     * Example: ["Avengers", "Defenders"]
     */
    @ColumnInfo(name = "teams")
    @JvmField
    var teams: String? = null,
    /**
     * Locations featured (JSON array).
     * Example: ["New York", "Tokyo", "London"]
     */
    @ColumnInfo(name = "locations")
    @JvmField
    var locations: String? = null,
    /**
     * Alternate series name.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "alternateSeries")
    @JvmField
    var alternateSeries: String? = null,
    /**
     * Alternate issue number.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "alternateNumber")
    @JvmField
    var alternateNumber: String? = null,
    /**
     * Issues in alternate series.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "alternateCount")
    @JvmField
    var alternateCount: Int? = null,
    /**
     * Scan information/credits.
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "scanInformation")
    @JvmField
    var scanInformation: String? = null,
    /**
     * Main character or team (primary focus).
     * JSON priority: ComicInfo > MetronInfo
     */
    @ColumnInfo(name = "mainCharacterOrTeam")
    @JvmField
    var mainCharacterOrTeam: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComicMetadata) return false
        if (id != other.id) return false
        if (comicBookId != other.comicBookId) return false
        return true
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ComicMetadata(id=$id, comicBookId=$comicBookId, series=$series, number=$number)"
}
