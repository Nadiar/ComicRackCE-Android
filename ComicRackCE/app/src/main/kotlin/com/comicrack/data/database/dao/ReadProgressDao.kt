package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.comicrack.data.database.entities.ReadProgress
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for ReadProgress entities.
 *
 * Handles reading progress tracking - persisting user's page position for resume functionality
 * and tracking read status for filtering.
 *
 * Phase B: Library Preferences System
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 */
@Dao
interface ReadProgressDao {
    /**
     * Insert a new reading progress record.
     * Used when first opening a comic.
     *
     * @param progress The ReadProgress entity to insert
     * @return The row ID of the inserted record
     */
    @Insert
    suspend fun insert(progress: ReadProgress): Long

    /**
     * Update an existing reading progress record.
     * Used to save current page position periodically while reading.
     *
     * @param progress The ReadProgress entity with updated values
     * @return Number of rows updated (should be 1)
     */
    @Update
    suspend fun update(progress: ReadProgress): Int

    /**
     * Delete a reading progress record.
     * Cascading delete handled by ComicBook deletion.
     *
     * @param progress The ReadProgress entity to delete
     * @return Number of rows deleted
     */
    @Delete
    suspend fun delete(progress: ReadProgress): Int

    /**
     * Get reading progress for a specific comic.
     * Returns null if comic hasn't been read yet.
     *
     * @param comicId The ID of the comic
     * @return The ReadProgress, or null if not found
     */
    @Query("SELECT * FROM read_progress WHERE comicId = :comicId LIMIT 1")
    suspend fun getProgress(comicId: Long): ReadProgress?

    /**
     * Observe reading progress for a specific comic.
     * Reactive - updates when progress changes.
     * Emits null if comic hasn't been read yet.
     *
     * @param comicId The ID of the comic
     * @return Flow of ReadProgress (reactive)
     */
    @Query("SELECT * FROM read_progress WHERE comicId = :comicId LIMIT 1")
    fun observeProgress(comicId: Long): Flow<ReadProgress?>

    /**
     * Get all unread comics (those without progress or not marked as read).
     *
     * @return Flow of unread comic IDs
     */
    @Query(
        """
        SELECT c.id FROM comics c
        LEFT JOIN read_progress rp ON c.id = rp.comicId
        WHERE rp.id IS NULL OR rp.isRead = 0
        ORDER BY c.title ASC
    """,
    )
    fun observeUnreadComicIds(): Flow<List<Long>>

    /**
     * Get all read comics (those marked as read).
     *
     * @return Flow of read comic IDs
     */
    @Query(
        """
        SELECT c.id FROM comics c
        JOIN read_progress rp ON c.id = rp.comicId
        WHERE rp.isRead = 1
        ORDER BY rp.lastReadTime DESC
    """,
    )
    fun observeReadComicIds(): Flow<List<Long>>

    /**
     * Get currently reading comics (unfinished, has progress).
     * Sorted by most recently read.
     *
     * @return Flow of currently reading comic IDs
     */
    @Query(
        """
        SELECT c.id FROM comics c
        JOIN read_progress rp ON c.id = rp.comicId
        WHERE rp.isRead = 0 AND rp.currentPage > 0
        ORDER BY rp.lastReadTime DESC
    """,
    )
    fun observeReadingComicIds(): Flow<List<Long>>

    /**
     * Mark a comic as read.
     * Sets isRead flag and updates timestamp.
     *
     * @param comicId The ID of the comic
     * @param totalPages The total page count (for caching)
     * @param now Current timestamp
     */
    @Query(
        """
        UPDATE read_progress 
        SET isRead = 1, currentPage = :totalPages, lastReadTime = :now
        WHERE comicId = :comicId
    """,
    )
    suspend fun markAsRead(
        comicId: Long,
        totalPages: Int,
        now: Long,
    )

    /**
     * Mark a comic as unread.
     * Clears isRead flag.
     *
     * @param comicId The ID of the comic
     */
    @Query(
        """
        UPDATE read_progress
        SET isRead = 0
        WHERE comicId = :comicId
    """,
    )
    suspend fun markAsUnread(comicId: Long)

    /**
     * Get all progress records for cleanup.
     * Useful for batch operations.
     *
     * @return List of all ReadProgress records
     */
    @Query("SELECT * FROM read_progress ORDER BY lastReadTime DESC")
    suspend fun getAllProgress(): List<ReadProgress>

    /**
     * Clear progress for a stale comic (older than X days).
     * Used for cleanup of deleted files.
     *
     * @param olderThanTimestamp Remove progress older than this timestamp
     */
    @Query("DELETE FROM read_progress WHERE lastReadTime < :olderThanTimestamp")
    suspend fun deleteStaleProgress(olderThanTimestamp: Long)
}
