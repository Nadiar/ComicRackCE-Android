package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.comicrack.data.database.entities.ComicBook
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ComicBook entity (Kotlin).
 *
 * Provides database queries for managing comic book metadata:
 * - Insert discovered comics during library scan
 * - Query comics by library path
 * - Update page counts and other metadata
 * - Search for comics by title or filename
 * - Delete comics when paths are removed
 *
 * Phase 1: Room Database Schema (Kotlin)
 */
@Dao
interface ComicBookDao {
    /**
     * Insert a new comic book record.
     * If a comic with the same ID exists, replace it.
     *
     * @param comic The ComicBook to insert
     * @return The auto-generated ID of the inserted comic
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComicBook(comic: ComicBook): Long

    /**
     * Insert multiple comic books in a transaction.
     * Useful during library scan to insert all discovered comics.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComicBooks(vararg comics: ComicBook)

    /**
     * Update an existing comic book record.
     * Useful for updating cached metadata like page count.
     *
     * @param comic The ComicBook with updated fields
     */
    @Update
    suspend fun updateComicBook(comic: ComicBook)

    /**
     * Upsert (insert or update) a comic book.
     * If comic with same source path exists, update it; otherwise insert.
     * Migration: Replaces legacy ComicRackDatabase.writeCachedInfo()
     *
     * @param comic The ComicBook to insert or update
     * @return The ID of the inserted/updated comic
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComicBook(comic: ComicBook): Long

    /**
     * Delete a comic book by ID.
     *
     * @param id The ID of the comic to delete
     */
    @Query("DELETE FROM comics WHERE id = :id")
    suspend fun deleteComicBookById(id: Long)

    /**
     * Delete a comic book by source path.
     * Migration: Replaces legacy ComicRackDatabase.deleteBySource()
     *
     * @param source The source path to delete
     */
    @Query("DELETE FROM comics WHERE source = :source")
    suspend fun deleteBySourcePath(source: String)

    /**
     * Delete a comic book.
     *
     * @param comic The ComicBook to delete
     */
    @Delete
    suspend fun deleteComicBook(comic: ComicBook)

    /**
     * Get all comics in a library path.
     * Sorted by title for display.
     *
     * @param libraryPathId The ID of the library path
     * @return List of comics in the library path
     */
    @Query("SELECT * FROM comics WHERE libraryPathId = :libraryPathId ORDER BY title ASC")
    suspend fun getComicsByLibraryPath(libraryPathId: Long): List<ComicBook>

    /**
     * Get a comic by ID.
     *
     * @param id The ID of the comic
     * @return The ComicBook, or null if not found
     */
    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun getComicBookById(id: Long): ComicBook?

    /**
     * Get a comic by file path.
     * Useful for checking if a file has already been indexed.
     *
     * @param filePath The file path (SAF URI or local path)
     * @return The ComicBook, or null if not found
     */
    @Query("SELECT * FROM comics WHERE filePath = :filePath")
    suspend fun getComicBookByPath(filePath: String): ComicBook?

    /**
     * Search for comics by title (case-insensitive partial match).
     *
     * @param titleQuery The search query (e.g., "saga" matches "Saga of the Swamp Thing")
     * @return List of matching comics
     */
    @Query("SELECT * FROM comics WHERE LOWER(title) LIKE LOWER('%' || :titleQuery || '%') ORDER BY title ASC")
    suspend fun searchComicsByTitle(titleQuery: String): List<ComicBook>

    /**
     * Search for comics by filename (case-insensitive partial match).
     *
     * @param filenameQuery The search query
     * @return List of matching comics
     */
    @Query("SELECT * FROM comics WHERE LOWER(fileName) LIKE LOWER('%' || :filenameQuery || '%') ORDER BY fileName ASC")
    suspend fun searchComicsByFilename(filenameQuery: String): List<ComicBook>

    /**
     * Get all comics of a specific format (cbz, cbr, etc.).
     *
     * @param format The format (e.g., "cbz")
     * @return List of comics with the specified format
     */
    @Query("SELECT * FROM comics WHERE format = :format ORDER BY title ASC")
    suspend fun getComicsByFormat(format: String): List<ComicBook>

    /**
     * Get the total number of comics in a library path.
     *
     * @param libraryPathId The ID of the library path
     * @return Total comic count in the path
     */
    @Query("SELECT COUNT(*) FROM comics WHERE libraryPathId = :libraryPathId")
    suspend fun getComicCountByLibraryPath(libraryPathId: Long): Int

    /**
     * Get all comics with page count == 0 (not yet scanned).
     * Useful for identifying comics that need metadata refresh.
     *
     * @return List of unscanned comics
     */
    @Query("SELECT * FROM comics WHERE pageCount = 0 ORDER BY title ASC")
    suspend fun getUnscannedComics(): List<ComicBook>

    /**
     * Delete all comics in a library path.
     * Useful when rescanning or removing a library path.
     *
     * @param libraryPathId The ID of the library path
     */
    @Query("DELETE FROM comics WHERE libraryPathId = :libraryPathId")
    suspend fun deleteComicsByLibraryPath(libraryPathId: Long)

    /**
     * Delete all comics.
     * Used for testing and cleanup.
     */
    @Query("DELETE FROM comics")
    suspend fun deleteAllComicBooks()

    /**
     * Delete duplicate comics, keeping only the one with the lowest ID for each filePath.
     * This cleans up any duplicates that may have been introduced by bugs.
     *
     * Uses a subquery to find the minimum ID for each unique filePath,
     * then deletes all comics not in that result set.
     */
    @Query("DELETE FROM comics WHERE id NOT IN (SELECT MIN(id) FROM comics GROUP BY filePath)")
    suspend fun deleteDuplicates()

    /**
     * Observe all comics in a library path as a reactive Flow.
     * UI components can collect this to automatically update when comics change.
     *
     * @param libraryPathId The ID of the library path
     * @return Flow that emits the list of comics in the path
     */
    @Query("SELECT * FROM comics WHERE libraryPathId = :libraryPathId ORDER BY title ASC")
    fun observeComicsByLibraryPath(libraryPathId: Long): Flow<List<ComicBook>>

    /**
     * Observe all comics (entire library) as a reactive Flow.
     * Useful for the main library view to display all discovered comics.
     *
     * @return Flow that emits the complete list of all comics
     */
    @Query("SELECT * FROM comics ORDER BY title ASC")
    fun observeAllComics(): Flow<List<ComicBook>>

    /**
     * Observe a single comic by ID as a reactive Flow.
     *
     * @param id The ID of the comic
     * @return Flow that emits the ComicBook (or null if not found)
     */
    @Query("SELECT * FROM comics WHERE id = :id")
    fun observeComicBookById(id: Long): Flow<ComicBook?>

    /**
     * Observe all unread comics (those without progress or not marked as read).
     * Phase B: Library Preferences System
     *
     * @return Flow of unread ComicBooks, ordered by title
     */
    @Query(
        """
        SELECT c.* FROM comics c
        LEFT JOIN read_progress rp ON c.id = rp.comicId
        WHERE rp.id IS NULL OR rp.isRead = 0
        ORDER BY c.title ASC
    """,
    )
    fun observeUnreadComics(): Flow<List<ComicBook>>

    /**
     * Observe all read comics (those marked as complete).
     * Phase B: Library Preferences System
     *
     * @return Flow of read ComicBooks, ordered by most recently read
     */
    @Query(
        """
        SELECT c.* FROM comics c
        JOIN read_progress rp ON c.id = rp.comicId
        WHERE rp.isRead = 1
        ORDER BY rp.lastReadTime DESC
    """,
    )
    fun observeReadComics(): Flow<List<ComicBook>>

    /**
     * Observe currently reading comics (started but not completed).
     * Phase B: Library Preferences System
     * Sorted by most recently read (resume from here).
     *
     * Excludes comics at 90%+ progress (should be marked as read).
     * Uses formula: (currentPage + 1) * 100 / totalPages < 90
     *
     * @return Flow of in-progress ComicBooks
     */
    @Query(
        """
        SELECT c.* FROM comics c
        JOIN read_progress rp ON c.id = rp.comicId
        WHERE rp.isRead = 0
          AND rp.currentPage > 0
          AND (rp.currentPage + 1) * 100 / rp.totalPages < 90
        ORDER BY rp.lastReadTime DESC
    """,
    )
    fun observeReadingComics(): Flow<List<ComicBook>>

    /**
     * Get a comic by its source path (file path or URI).
     * Migration: Replaces legacy ComicRackDatabase.getComicBySource()
     *
     * @param source The source path (SAF URI or file path)
     * @return The ComicBook with matching source, or null if not found
     */
    @Query("SELECT * FROM comics WHERE source = :source LIMIT 1")
    suspend fun getComicBySource(source: String): ComicBook?

    /**
     * Get a comic by source path with modification time check.
     * Migration: Replaces legacy ComicRackDatabase.getComicBySource(String, long)
     * Checks if file was modified since last cached (sourceModified field)
     *
     * @param source The source path (SAF URI or file path)
     * @param sourceModified The expected modification timestamp
     * @return The ComicBook if found and modification time matches, null otherwise
     */
    @Query("SELECT * FROM comics WHERE source = :source AND sourceModified = :sourceModified LIMIT 1")
    suspend fun getComicBySourceAndModified(
        source: String,
        sourceModified: Long,
    ): ComicBook?

    /**
     * Get all comic source paths (file paths/URIs).
     * Migration: Replaces legacy ComicRackDatabase.getAllSources()
     *
     * @return List of all source paths currently cached
     */
    @Query("SELECT source FROM comics ORDER BY source ASC")
    suspend fun getAllSources(): List<String>

    /**
     * Get all comics with minimal fields (for batch processing).
     * Migration: Replaces legacy ComicRackDatabase.getAllComics()
     *
     * @return List of all ComicBooks in database
     */
    @Query("SELECT * FROM comics ORDER BY title ASC")
    suspend fun getAllComics(): List<ComicBook>

    /**
     * Delete all comics whose source is NOT in the provided list.
     * Migration: Replaces legacy ComicRackDatabase.cleanupBooks(Iterable)
     * Used during library rescanning to remove stale entries.
     *
     * @param sources List of valid source paths to keep
     */
    @Query("DELETE FROM comics WHERE source NOT IN (:sources)")
    suspend fun deleteComicsNotInSources(sources: List<String>)

    /**
     * Delete stale comics for a specific library path.
     * Removes all comics associated with [libraryPathId] whose source is NOT in [validPaths].
     *
     * @param libraryPathId The ID of the library path
     * @param validPaths List of valid source/file paths to keep
     */
    @Query("DELETE FROM comics WHERE libraryPathId = :libraryPathId AND source NOT IN (:validPaths)")
    suspend fun deleteStaleComicsInPath(libraryPathId: Long, validPaths: List<String>)

    /**
     * Get all comics matching a search query (case-insensitive) across multiple fields.
     * Used for search suggestions and filtering.
     * Migration: Part of legacy ComicRackDatabase.searchSuggestions()
     *
     * @param query The search query pattern
     * @return List of matching ComicBooks
     */
    @Query(
        """
        SELECT * FROM comics 
        WHERE LOWER(title) LIKE LOWER('%' || :query || '%')
           OR LOWER(series) LIKE LOWER('%' || :query || '%')
           OR LOWER(writer) LIKE LOWER('%' || :query || '%')
           OR LOWER(fileName) LIKE LOWER('%' || :query || '%')
        ORDER BY title ASC
    """,
    )
    suspend fun searchComicsByQuery(query: String): List<ComicBook>

    /**
     * Get comics by a specific field value (for suggestion queries).
     * Used for search suggestions grouped by field (series, writer, etc.)
     * Migration: Part of legacy ComicRackDatabase.searchSuggestions()
     *
     * @param fieldValue The value to match (e.g., series name, writer name)
     * @return List of comics matching that field value
     */
    @Query("SELECT * FROM comics WHERE series LIKE :fieldValue OR writer LIKE :fieldValue ORDER BY title ASC")
    suspend fun searchComicsByFieldValue(fieldValue: String): List<ComicBook>

    /**
     * Iterate through all comics with a callback function.
     * Migration: Replaces legacy ComicRackDatabase.forAllComics(c callback)
     * This loads all comics into memory, so use sparingly.
     *
     * For better performance, use Flow-based methods instead.
     *
     * @return List of all comics (for iteration via forEach or iterator)
     */
    @Query("SELECT * FROM comics ORDER BY id ASC")
    suspend fun getAllComicsForIteration(): List<ComicBook>

    /**
     * Search for comics by caption (series + number + title).
     * Migration: Part of legacy ComicRackDatabase.searchSuggestions()
     *
     * @param query The search query (partial match, case-insensitive)
     * @return List of matching comics
     */
    @Query(
        """
        SELECT * FROM comics
        WHERE LOWER(caption) LIKE LOWER('%' || :query || '%')
        ORDER BY caption ASC
    """,
    )
    suspend fun searchComicsByCaption(query: String): List<ComicBook>

    /**
     * Search for comics by full series label.
     * Migration: Part of legacy ComicRackDatabase.searchSuggestions()
     *
     * @param query The search query (partial match, case-insensitive)
     * @return List of matching comics
     */
    @Query(
        """
        SELECT * FROM comics
        WHERE LOWER(fullSeries) LIKE LOWER('%' || :query || '%')
        ORDER BY fullSeries ASC
    """,
    )
    suspend fun searchComicsByFullSeries(query: String): List<ComicBook>

    /**
     * Get all unique series names in the library.
     * Used for series filtering and search suggestions.
     *
     * @return List of unique series names (non-null, non-empty)
     */
    @Query("SELECT DISTINCT series FROM comics WHERE series IS NOT NULL AND series != '' ORDER BY series ASC")
    suspend fun getAllUniqueSeries(): List<String>

    /**
     * Get all unique writers in the library.
     * Used for writer filtering and search suggestions.
     *
     * @return List of unique writer names
     */
    @Query("SELECT DISTINCT writer FROM comics WHERE writer IS NOT NULL AND writer != '' ORDER BY writer ASC")
    suspend fun getAllUniqueWriters(): List<String>

    /**
     * Get all unique genres in the library.
     * Used for genre filtering and search suggestions.
     *
     * @return List of unique genre values
     */
    @Query("SELECT DISTINCT genre FROM comics WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    suspend fun getAllUniqueGenres(): List<String>

    /**
     * Get all unique publishers in the library.
     * Used for publisher filtering and search suggestions.
     *
     * @return List of unique publisher names
     */
    @Query(
        "SELECT DISTINCT publisher FROM comics WHERE publisher IS NOT NULL AND publisher != '' ORDER BY publisher ASC",
    )
    suspend fun getAllUniquePublishers(): List<String>

    /**
     * Count total number of comics in database.
     *
     * @return Total number of comics
     */
    @Query("SELECT COUNT(*) FROM comics")
    suspend fun getTotalComicCount(): Int

    /**
     * Get comics published in a specific year.
     *
     * @param year The publication year (e.g., 2023)
     * @return List of comics published in that year
     */
    @Query("SELECT * FROM comics WHERE year = :year ORDER BY title ASC")
    suspend fun getComicsByYear(year: Int): List<ComicBook>

    /**
     * Get all comics by a specific writer.
     *
     * @param writer The writer name
     * @return List of comics written by that person
     */
    @Query("SELECT * FROM comics WHERE writer = :writer ORDER BY series ASC, issueNumber ASC")
    suspend fun getComicsByWriter(writer: String): List<ComicBook>

    /**
     * Get all comics by a specific publisher.
     *
     * @param publisher The publisher name
     * @return List of comics from that publisher
     */
    @Query("SELECT * FROM comics WHERE publisher = :publisher ORDER BY series ASC, issueNumber ASC")
    suspend fun getComicsByPublisher(publisher: String): List<ComicBook>

    /**
     * Get all comics in a specific series.
     *
     * @param seriesName The series name
     * @return List of comics in that series (sorted by issue number)
     */
    @Query("SELECT * FROM comics WHERE series = :seriesName ORDER BY CAST(issueNumber AS INTEGER) ASC")
    suspend fun getComicsBySeries(seriesName: String): List<ComicBook>

    /**
     * Get all comics in a story arc.
     *
     * @param storyArc The story arc name
     * @return List of comics in that arc
     */
    @Query("SELECT * FROM comics WHERE storyArc = :storyArc ORDER BY title ASC")
    suspend fun getComicsByStoryArc(storyArc: String): List<ComicBook>

    /**
     * Observe newly added comics (added within the last 7 days).
     * Phase 1: Navigation Rail - "New Comics" collection
     * Sorted by most recently added first.
     *
     * @return Flow of new ComicBooks from the past week
     */
    @Query(
        """
        SELECT * FROM comics
        WHERE added > 0 AND added > (strftime('%s', 'now') * 1000 - 604800000)
        ORDER BY added DESC
    """,
    )
    fun observeNewComics(): Flow<List<ComicBook>>

    // === Remote Storage Queries (Phase SRC-05-007 v2) ===

    /**
     * Get all comics of a specific storage type.
     *
     * @param storageType The storage type value (e.g., "LOCAL", "SAF", "GOOGLE_DRIVE")
     * @return List of comics with that storage type
     */
    @Query("SELECT * FROM comics WHERE storageType = :storageType ORDER BY title ASC")
    suspend fun getComicsByStorageType(storageType: String): List<ComicBook>

    /**
     * Observe comics filtered by storage type as a reactive Flow.
     *
     * @param storageType The storage type value
     * @return Flow that emits filtered comic list
     */
    @Query("SELECT * FROM comics WHERE storageType = :storageType ORDER BY title ASC")
    fun observeComicsByStorageType(storageType: String): Flow<List<ComicBook>>

    /**
     * Find a comic by its remote provider ID (e.g., Google Drive file ID).
     *
     * @param remoteId The backend-specific identifier
     * @return The ComicBook, or null if not found
     */
    @Query("SELECT * FROM comics WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getComicByRemoteId(remoteId: String): ComicBook?

    /**
     * Update the local cache path for a remote comic after download.
     *
     * @param id The comic ID
     * @param cacheLocalPath The local file path, or null to clear cache
     */
    @Query("UPDATE comics SET cacheLocalPath = :cacheLocalPath WHERE id = :id")
    suspend fun updateCachePath(id: Long, cacheLocalPath: String?)

    /**
     * Get all remote comics that have been cached locally.
     * Ordered by syncedAt ascending so the oldest-synced entries can be evicted first.
     * Used by RemoteCacheManager for LRU eviction.
     *
     * @return List of cached remote comics, oldest sync first
     */
    @Query(
        """
        SELECT * FROM comics
        WHERE storageType != 'LOCAL' AND storageType != 'SAF'
          AND cacheLocalPath IS NOT NULL
        ORDER BY syncedAt ASC
    """,
    )
    suspend fun getCachedRemoteComics(): List<ComicBook>
}
