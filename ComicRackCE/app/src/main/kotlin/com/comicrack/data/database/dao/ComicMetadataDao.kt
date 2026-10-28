package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.comicrack.data.database.entities.ComicMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ComicMetadata entity (Kotlin).
 *
 * Provides database queries for managing parsed comic metadata:
 * - Insert metadata after parsing from archive XML
 * - Update metadata when sources change or new data available
 * - Query metadata by comic ID
 * - Delete metadata when comics are removed
 *
 * Phase 1: Database Schema (Kotlin)
 */
@Dao
interface ComicMetadataDao {
    /**
     * Insert a new metadata record.
     * If metadata for comic already exists, replace it.
     *
     * @param metadata The ComicMetadata to insert
     * @return The auto-generated ID of the inserted metadata
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: ComicMetadata): Long

    /**
     * Insert multiple metadata records in a transaction.
     * Useful for batch imports.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadataBatch(vararg metadata: ComicMetadata)

    /**
     * Update an existing metadata record.
     * Used when metadata is refreshed or supplemented.
     *
     * @param metadata The ComicMetadata with updated fields
     */
    @Update
    suspend fun updateMetadata(metadata: ComicMetadata)

    /**
     * Upsert (insert or update) metadata.
     * If metadata for comic already exists, update it; otherwise insert.
     *
     * @param metadata The ComicMetadata to insert or update
     * @return The ID of the inserted/updated metadata
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: ComicMetadata): Long

    /**
     * Get metadata for a specific comic by ID.
     * Returns null if no metadata exists.
     *
     * @param comicBookId The ID of the comic
     * @return The metadata, or null if not found
     */
    @Query("SELECT * FROM comic_metadata WHERE comicBookId = :comicBookId")
    suspend fun getMetadataByComicId(comicBookId: Long): ComicMetadata?

    /**
     * Get metadata as a Flow for reactive updates.
     * Useful for UI components that need to react to metadata changes.
     *
     * @param comicBookId The ID of the comic
     * @return Flow that emits metadata updates
     */
    @Query("SELECT * FROM comic_metadata WHERE comicBookId = :comicBookId")
    fun getMetadataFlow(comicBookId: Long): Flow<ComicMetadata?>

    /**
     * Get all metadata records (useful for debugging/inspection).
     *
     * @return List of all metadata records
     */
    @Query("SELECT * FROM comic_metadata")
    suspend fun getAllMetadata(): List<ComicMetadata>

    /**
     * Get all metadata as a Flow.
     *
     * @return Flow emitting all metadata records
     */
    @Query("SELECT * FROM comic_metadata")
    fun getAllMetadataFlow(): Flow<List<ComicMetadata>>

    /**
     * Check if metadata exists for a comic.
     *
     * @param comicBookId The ID of the comic
     * @return True if metadata exists, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM comic_metadata WHERE comicBookId = :comicBookId)")
    suspend fun hasMetadata(comicBookId: Long): Boolean

    /**
     * Get count of metadata records.
     * Useful for understanding parsing progress.
     *
     * @return Number of metadata records in database
     */
    @Query("SELECT COUNT(*) FROM comic_metadata")
    suspend fun getMetadataCount(): Int

    /**
     * Get metadata for comics parsed before a certain timestamp.
     * Useful for implementing TTL-based cache invalidation.
     *
     * @param beforeTimestamp Timestamp in milliseconds
     * @return List of metadata records older than timestamp
     */
    @Query("SELECT * FROM comic_metadata WHERE parsedAt < :beforeTimestamp")
    suspend fun getMetadataOlderThan(beforeTimestamp: Long): List<ComicMetadata>

    /**
     * Delete metadata for a specific comic.
     *
     * @param comicBookId The ID of the comic
     */
    @Query("DELETE FROM comic_metadata WHERE comicBookId = :comicBookId")
    suspend fun deleteMetadataByComicId(comicBookId: Long)

    /**
     * Delete a metadata record by ID.
     *
     * @param id The ID of the metadata record
     */
    @Query("DELETE FROM comic_metadata WHERE id = :id")
    suspend fun deleteMetadataById(id: Long)

    /**
     * Delete a metadata record.
     *
     * @param metadata The ComicMetadata to delete
     */
    @Delete
    suspend fun deleteMetadata(metadata: ComicMetadata)

    /**
     * Delete all metadata records (useful for cache clearing).
     */
    @Query("DELETE FROM comic_metadata")
    suspend fun deleteAllMetadata()

    /**
     * Truncate metadata from a certain date forward (for cache invalidation).
     * Useful for selective refresh based on source file modification time.
     *
     * @param afterTimestamp Timestamp in milliseconds
     */
    @Query("DELETE FROM comic_metadata WHERE parsedAt >= :afterTimestamp")
    suspend fun deleteMetadataNewerThan(afterTimestamp: Long)
}
