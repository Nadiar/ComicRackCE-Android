package com.comicrack.data.database.entities

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for comic book metadata.
 *
 * Stores information about discovered comic files (CBZ, CBR, etc.) in library paths.
 * Each comic is associated with a LibraryPath and can have cached metadata (page count, etc.).
 *
 * Phase 1: Room Database Schema (Kotlin)
 */
@Entity(
    tableName = "comics",
    foreignKeys = [
        ForeignKey(
            entity = LibraryPath::class,
            parentColumns = ["id"],
            childColumns = ["libraryPathId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("libraryPathId"),
        Index("filePath", unique = true),
    ],
)
data class ComicBook(
    /**
     * Foreign key reference to the LibraryPath this comic was discovered in.
     */
    @ColumnInfo(name = "libraryPathId")
    @JvmField
    var libraryPathId: Long = 0,
    /**
     * Display title of the comic (usually filename without extension).
     */
    @JvmField
    var title: String = "",
    /**
     * Full path to the comic file.
     * Can be:
     *   - SAF URI: "content://com.android.externalstorage.documents/document/primary%3AComics%2FSaga.cbz"
     *   - Local path: "/sdcard/Comics/Saga.cbz" (only for app-specific dirs or after migration)
     */
    @JvmField
    var filePath: String = "",
    /**
     * Just the filename (e.g., "Saga.cbz").
     * Used for display and quick lookups.
     */
    @JvmField
    var fileName: String = "",
    @PrimaryKey(autoGenerate = true)
    @JvmField
    var id: Long = 0,
    /**
     * Source path used for caching (same as filePath in most cases).
     * Migration: Retained from legacy "books.Source" column for backward compatibility.
     */
    @JvmField
    var source: String = "",
    /**
     * Last modification timestamp of the source file.
     * Used to detect if file content has changed since last cached.
     * Migration: From legacy "books.SourceModified" column.
     */
    @JvmField
    var sourceModified: Long = 0,
    /**
     * File size in bytes.
     * Useful for disk space calculations.
     */
    @JvmField
    var fileSize: Long = 0,
    /**
     * Last modification timestamp (milliseconds since epoch).
     * Used to detect if file has changed since last scan.
     */
    @JvmField
    var dateModified: Long = 0,
    /**
     * Cached page count (number of images in the archive).
     * Populated by archive reader after first open.
     * Can be 0 if not yet scanned.
     */
    @JvmField
    var pageCount: Int = 0,
    /**
     * Format of the comic file (cbz, cbr, cb7, cbt, zip, rar, etc.).
     * Determined by file extension.
     */
    @JvmField
    var format: String = "",
    /**
     * Series name extracted from filename (e.g., "Amazing Spider-Man").
     */
    @JvmField
    var series: String = "",
    /**
     * Issue number extracted from filename (e.g., "001").
     */
    @JvmField
    var issueNumber: String = "",
    // === Comic Metadata (ComicInfo.xml and legacy fields) ===
    /**
     * Alternate series name (e.g., "Spider-Man" when series is "Amazing Spider-Man").
     * Migration: From legacy "books.AlternateSeries".
     */
    @JvmField
    var alternateSeries: String? = null,
    /**
     * Alternate issue number.
     * Migration: From legacy "books.AlternateNumber".
     */
    @JvmField
    var alternateNumber: String? = null,
    /**
     * Number of issues in alternate series.
     * Migration: From legacy "books.AlternateCount".
     */
    @JvmField
    var alternateCount: Int = 0,
    /**
     * Story arc this comic belongs to (e.g., "Civil War").
     * Migration: From legacy "books.StoryArc".
     */
    @JvmField
    var storyArc: String? = null,
    /**
     * Series group this comic belongs to (e.g., "Marvel Universe").
     * Migration: From legacy "books.SeriesGroup".
     */
    @JvmField
    var seriesGroup: String? = null,
    /**
     * Number of issues in main series.
     * Migration: From legacy "books.Count".
     */
    @JvmField
    var count: Int = 0,
    /**
     * Volume number of the series.
     * Migration: From legacy "books.Volume".
     */
    @JvmField
    var volume: Int = 0,
    /**
     * Primary writer/author.
     * Migration: From legacy "books.Writer".
     */
    @JvmField
    var writer: String? = null,
    /**
     * Pencil artist.
     * Migration: From legacy "books.Penciller".
     */
    @JvmField
    var penciller: String? = null,
    /**
     * Inker artist.
     * Migration: From legacy "books.Inker".
     */
    @JvmField
    var inker: String? = null,
    /**
     * Colorist.
     * Migration: From legacy "books.Colorist".
     */
    @JvmField
    var colorist: String? = null,
    /**
     * Letterer.
     * Migration: From legacy "books.Letterer".
     */
    @JvmField
    var letterer: String? = null,
    /**
     * Cover artist.
     * Migration: From legacy "books.CoverArtist".
     */
    @JvmField
    var coverArtist: String? = null,
    /**
     * Editor.
     * Migration: From legacy "books.Editor".
     */
    @JvmField
    var editor: String? = null,
    /**
     * Publisher name.
     * Migration: From legacy "books.Publisher".
     */
    @JvmField
    var publisher: String? = null,
    /**
     * Publisher imprint.
     * Migration: From legacy "books.Imprint".
     */
    @JvmField
    var imprint: String? = null,
    /**
     * Genre/category of the comic.
     * Migration: From legacy "books.Genre".
     */
    @JvmField
    var genre: String? = null,
    /**
     * Web URL (publisher website, etc.).
     * Migration: From legacy "books.Web".
     */
    @JvmField
    var web: String? = null,
    /**
     * Publication year.
     * Migration: From legacy "books.Year".
     */
    @JvmField
    var year: Int = 0,
    /**
     * Publication month (1-12).
     * Migration: From legacy "books.Month".
     */
    @JvmField
    var month: Int = 0,
    /**
     * Publication day of month (1-31).
     * Migration: From legacy "books.Day".
     */
    @JvmField
    var day: Int = 0,
    /**
     * Language code (ISO 639-1, e.g., "en", "fr").
     * Migration: From legacy "books.LanguageISO".
     */
    @JvmField
    var languageISO: String? = null,
    /**
     * Age rating (e.g., "4+", "12+", "16+", "18+").
     * Migration: From legacy "books.AgeRating".
     */
    @JvmField
    var ageRating: String? = null,
    /**
     * Whether the comic is in black and white.
     * Migration: From legacy "books.BlackAndWhite".
     */
    @JvmField
    var blackAndWhite: Boolean = false,
    /**
     * Whether the comic is in manga format (right-to-left reading).
     * Migration: From legacy "books.Manga".
     */
    @JvmField
    var manga: Boolean = false,
    /**
     * Index of the preferred front cover image.
     * Migration: From legacy "books.PreferredFrontCover".
     */
    @JvmField
    var preferredFrontCover: Int = 0,
    /**
     * Characters featured in this comic (pipe-separated list).
     * Migration: From legacy "books.Characters".
     */
    @JvmField
    var characters: String? = null,
    /**
     * Main character or team (pipe-separated list).
     * Migration: From legacy "books.MainCharacterOrTeam".
     */
    @JvmField
    var mainCharacterOrTeam: String? = null,
    /**
     * Teams featured (pipe-separated list).
     * Migration: From legacy "books.Teams".
     */
    @JvmField
    var teams: String? = null,
    /**
     * Locations featured (pipe-separated list).
     * Migration: From legacy "books.Locations".
     */
    @JvmField
    var locations: String? = null,
    /**
     * Community rating (0.0-10.0).
     * Migration: From legacy "books.CommunityRating".
     */
    @JvmField
    var communityRating: Float = 0f,
    /**
     * Scan information/notes.
     * Migration: From legacy "books.ScanInformation".
     */
    @JvmField
    var scanInformation: String? = null,
    /**
     * User rating (0-10).
     * Migration: From legacy "books.Rating".
     */
    @JvmField
    var rating: Int = 0,
    /**
     * When this comic was added to library (timestamp).
     * Migration: From legacy "books.Added".
     */
    @JvmField
    var added: Long = 0,
    /**
     * When this comic was last opened (timestamp).
     * Migration: From legacy "books.Opened".
     */
    @JvmField
    var opened: Long = 0,
    /**
     * Number of times this comic has been opened.
     * Migration: From legacy "books.OpenedCount".
     */
    @JvmField
    var openedCount: Int = 0,
    /**
     * Current page being read (0-based).
     * Migration: From legacy "books.CurrentPage".
     */
    @JvmField
    var currentPage: Int = 0,
    /**
     * Last page read (0-based).
     * Migration: From legacy "books.LastPageRead".
     */
    @JvmField
    var lastPageRead: Int = 0,
    /**
     * Binary data containing per-page metadata.
     * Migration: From legacy "books.PagesData".
     */
    @JvmField
    var pagesData: ByteArray? = null,
    /**
     * Complete series name (for sorting/grouping).
     * Migration: From legacy "books.FullSeries".
     */
    @JvmField
    var fullSeries: String? = null,
    /**
     * Caption/description.
     * Migration: From legacy "books.Caption".
     */
    @JvmField
    var caption: String? = null,
    /**
     * Flag indicating if the comic file is corrupt or cannot be opened.
     * Used to prevent repeated failed scan attempts.
     */
    @ColumnInfo(name = "isCorrupt")
    @JvmField
    var isCorrupt: Boolean = false,
    // === Remote Storage Fields (Phase SRC-05-007 v2) ===
    /**
     * Storage backend type for this comic.
     * Values: "LOCAL" (default), "SAF", "GOOGLE_DRIVE".
     * Used for unified library filtering by storage source.
     */
    @ColumnInfo(defaultValue = "'LOCAL'")
    @JvmField
    var storageType: String = "LOCAL",
    /**
     * Backend-specific identifier for remote comics (e.g., Google Drive file ID).
     * Null for local/SAF comics where filePath is sufficient.
     */
    @JvmField
    var remoteId: String? = null,
    /**
     * Local cache file path for downloaded remote comics.
     * When non-null, the comic file has been downloaded and can be read locally.
     * Managed by RemoteCacheManager with LRU eviction.
     */
    @JvmField
    var cacheLocalPath: String? = null,
    /**
     * Remote URL for the comic's cover thumbnail (e.g., Google Drive thumbnail URL).
     * Used by Coil for cover art loading without downloading the full archive.
     */
    @JvmField
    var thumbnailRemoteUrl: String? = null,
    /**
     * Timestamp when this comic was last synced with the remote provider.
     * Used for incremental sync to detect stale entries.
     */
    @ColumnInfo(defaultValue = "0")
    @JvmField
    var syncedAt: Long = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComicBook) return false
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ComicBook(id=$id, title=$title, series=$series, issueNumber=$issueNumber)"
}
