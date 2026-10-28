package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.comicrack.data.database.entities.LibraryPath
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for LibraryPath entity.
 *
 * Provides database queries for managing library paths:
 * - Insert new library paths (with SAF URIs)
 * - Query active library paths
 * - Update scan timestamps and comic counts
 * - Delete or deactivate paths
 *
 * Phase 3: Task 3.1 â€” Room Database Schema
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 */
@Dao
interface LibraryPathDao {
    /**
     * Insert a new library path.
     * If a path with the same ID exists, replace it.
     *
     * @param path The LibraryPath to insert
     * @return The auto-generated ID of the inserted path
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryPath(path: LibraryPath): Long

    /**
     * Insert multiple library paths in a transaction.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryPaths(vararg paths: LibraryPath)

    /**
     * Update an existing library path.
     * Useful for updating scan timestamps and comic counts.
     *
     * @param path The LibraryPath with updated fields
     */
    @Update
    suspend fun updateLibraryPath(path: LibraryPath)

    /**
     * Delete a library path by ID.
     * Cascades to delete all ComicBook entries associated with this path.
     *
     * @param id The ID of the library path to delete
     */
    @Query("DELETE FROM library_paths WHERE id = :id")
    suspend fun deleteLibraryPathById(id: Long)

    /**
     * Delete a library path.
     * Cascades to delete all ComicBook entries associated with this path.
     *
     * @param path The LibraryPath to delete
     */
    @Delete
    suspend fun deleteLibraryPath(path: LibraryPath)

    /**
     * Get all active library paths.
     *
     * @return List of active library paths
     */
    @Query("SELECT * FROM library_paths WHERE isActive = 1 ORDER BY displayName ASC")
    suspend fun getActiveLibraryPaths(): List<LibraryPath>

    /**
     * Observe all active library paths.
     *
     * @return Flow that emits the list of active library paths
     */
    @Query("SELECT * FROM library_paths WHERE isActive = 1 ORDER BY displayName ASC")
    fun observeActiveLibraryPaths(): Flow<List<LibraryPath>>

    /**
     * Get all library paths (including inactive).
     *
     * @return List of all library paths
     */
    @Query("SELECT * FROM library_paths ORDER BY displayName ASC")
    suspend fun getAllLibraryPaths(): List<LibraryPath>

    /**
     * Get a library path by ID.
     *
     * @param id The ID of the library path
     * @return The LibraryPath, or null if not found
     */
    @Query("SELECT * FROM library_paths WHERE id = :id")
    suspend fun getLibraryPathById(id: Long): LibraryPath?

    /**
     * Get a library path by its path string.
     * Useful for checking if a path has already been added.
     *
     * @param path The path string (SAF URI or local path)
     * @return The LibraryPath, or null if not found
     */
    @Query("SELECT * FROM library_paths WHERE path = :path")
    suspend fun getLibraryPathByPath(path: String): LibraryPath?

    /**
     * Count the total number of comics across all active library paths.
     *
     * @return Total comic count
     */
    @Query("SELECT COALESCE(SUM(comicCount), 0) FROM library_paths WHERE isActive = 1")
    suspend fun getTotalComicCount(): Int

    /**
     * Deactivate a library path without deleting it.
     * Useful for temporarily disabling a path.
     *
     * @param id The ID of the library path
     */
    @Query("UPDATE library_paths SET isActive = 0 WHERE id = :id")
    suspend fun deactivateLibraryPath(id: Long)

    /**
     * Reactivate a deactivated library path.
     *
     * @param id The ID of the library path
     */
    @Query("UPDATE library_paths SET isActive = 1 WHERE id = :id")
    suspend fun reactivateLibraryPath(id: Long)

    /**
     * Delete all library paths.
     * Used for testing and cleanup.
     */
    @Query("DELETE FROM library_paths")
    suspend fun deleteAllLibraryPaths()

    // === Remote Storage Queries (Phase SRC-05-007 v2) ===

    /**
     * Get active library paths filtered by storage type.
     *
     * @param storageType The storage type value (e.g., "SAF", "GOOGLE_DRIVE")
     * @return List of active library paths with that storage type
     */
    @Query("SELECT * FROM library_paths WHERE storageType = :storageType AND isActive = 1 ORDER BY displayName ASC")
    suspend fun getByStorageType(storageType: String): List<LibraryPath>

    /**
     * Observe active library paths filtered by storage type as a reactive Flow.
     *
     * @param storageType The storage type value
     * @return Flow that emits filtered library path list
     */
    @Query("SELECT * FROM library_paths WHERE storageType = :storageType AND isActive = 1 ORDER BY displayName ASC")
    fun observeByStorageType(storageType: String): Flow<List<LibraryPath>>
}
