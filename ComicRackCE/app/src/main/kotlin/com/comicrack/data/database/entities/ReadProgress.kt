package com.comicrack.data.database.entities

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for comic reading progress.
 *
 * Tracks which page the user last viewed in each comic, enabling resume functionality
 * and read status tracking.
 *
 * Features:
 * - One-to-one relationship with ComicBook
 * - Cascade delete when comic is removed
 * - Timestamp for last read action (enables "Reading" status filter)
 * - Read status calculation based on currentPage vs totalPages
 *
 * Phase B: Library Preferences System
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 *
 * @property id Auto-generated primary key
 * @property comicId Foreign key reference to the comic being tracked
 * @property currentPage Current page the user was on (0-indexed)
 * @property totalPages Total pages in the comic (cached from when opened)
 * @property lastReadTime Timestamp of last page view (milliseconds since epoch)
 * @property isRead Whether the comic has been fully read
 */
@Entity(
    tableName = "read_progress",
    foreignKeys = [
        ForeignKey(
            entity = ComicBook::class,
            parentColumns = ["id"],
            childColumns = ["comicId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("comicId"),
    ],
)
data class ReadProgress(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /**
     * Foreign key reference to the comic being tracked.
     */
    @ColumnInfo(name = "comicId")
    val comicId: Long = 0,
    /**
     * Current page the user was on (0-indexed).
     * Page 0 is the first page.
     */
    var currentPage: Int = 0,
    /**
     * Total pages in the comic (cached from when opened).
     * Used to calculate percentage complete without opening archive.
     */
    var totalPages: Int = 0,
    /**
     * Timestamp (milliseconds since epoch) of last page view.
     * Used to:
     * - Sort "Reading" filter (most recent first)
     * - Detect stale progress for cleanup
     */
    var lastReadTime: Long = System.currentTimeMillis(),
    /**
     * Whether the comic has been fully read (reached end).
     * Automatically set when currentPage >= totalPages - 1
     * Can also be manually set for quick marking.
     */
    var isRead: Boolean = false,
) {
    companion object {
        // Progress tracking constants
        private const val PERCENTAGE_MAX = 100
        private const val NEARLY_COMPLETE_THRESHOLD = 90
        private const val PAGE_OFFSET_FOR_DISPLAY = 1
    }

    /**
     * Get percentage complete (0-100).
     * Converts 0-indexed page to 1-indexed for display.
     * Example: page 0 of 20 = 5%, page 19 of 20 = 100%
     */
    fun getPercentComplete(): Int =
        when {
            totalPages == 0 -> 0
            else -> ((currentPage + PAGE_OFFSET_FOR_DISPLAY) * PERCENTAGE_MAX / totalPages)
        }

    /**
     * Check if reading is 90%+ complete (threshold for auto-mark as read).
     */
    fun isNearlyComplete(): Boolean = getPercentComplete() >= NEARLY_COMPLETE_THRESHOLD
}
