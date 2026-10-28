package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.comicrack.data.database.entities.ComicBook

/**
 * Java-compatible DAO for ComicBook (Blocking versions of suspend functions).
 *
 * Provides synchronous access to database operations for Java code.
 * Kotlin code should use the suspend functions from ComicBookDao directly.
 *
 * Phase 1: Java Compatibility Layer
 */
@Dao
interface ComicBookDaoCompat {
    /**
     * Insert a new comic book record (blocking, for Java).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertComicBookSync(comic: ComicBook): Long

    /**
     * Insert multiple comic books (blocking, for Java).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertComicBooksSync(vararg comics: ComicBook)

    /**
     * Update an existing comic book (blocking, for Java).
     */
    @Update
    fun updateComicBookSync(comic: ComicBook)

    /**
     * Upsert a comic book (blocking, for Java).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertComicBookSync(comic: ComicBook): Long

    /**
     * Delete a comic book by ID (blocking, for Java).
     */
    @Query("DELETE FROM comics WHERE id = :id")
    fun deleteComicBookByIdSync(id: Long)

    /**
     * Delete a comic book by source path (blocking, for Java).
     */
    @Query("DELETE FROM comics WHERE source = :source")
    fun deleteBySourcePathSync(source: String)

    /**
     * Delete a comic book (blocking, for Java).
     */
    @Delete
    fun deleteComicBookSync(comic: ComicBook)

    /**
     * Get all comics by library path (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE libraryPathId = :libraryPathId ORDER BY title ASC")
    fun getComicsByLibraryPathSync(libraryPathId: Long): List<ComicBook>

    /**
     * Get a comic by ID (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE id = :id")
    fun getComicBookByIdSync(id: Long): ComicBook?

    /**
     * Get a comic by file path (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE filePath = :filePath")
    fun getComicBookByPathSync(filePath: String): ComicBook?

    /**
     * Get a comic by source path (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE source = :source LIMIT 1")
    fun getComicBySourceSync(source: String): ComicBook?

    /**
     * Get a comic by source path with modification time check (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE source = :source AND sourceModified = :sourceModified LIMIT 1")
    fun getComicBySourceAndModifiedSync(
        source: String,
        sourceModified: Long,
    ): ComicBook?

    /**
     * Get all comic source paths (blocking, for Java).
     */
    @Query("SELECT source FROM comics ORDER BY source ASC")
    fun getAllSourcesSync(): List<String>

    /**
     * Get all comics (blocking, for Java).
     */
    @Query("SELECT * FROM comics ORDER BY title ASC")
    fun getAllComicsSync(): List<ComicBook>

    /**
     * Delete all comics not in provided sources list (blocking, for Java).
     */
    @Query("DELETE FROM comics WHERE source NOT IN (:sources)")
    fun deleteComicsNotInSourcesSync(sources: List<String>)

    /**
     * Search comics by query (blocking, for Java).
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
    fun searchComicsByQuerySync(query: String): List<ComicBook>

    /**
     * Get all unique series (blocking, for Java).
     */
    @Query("SELECT DISTINCT series FROM comics WHERE series IS NOT NULL AND series != '' ORDER BY series ASC")
    fun getAllUniqueSeriesSync(): List<String>

    /**
     * Get all unique writers (blocking, for Java).
     */
    @Query("SELECT DISTINCT writer FROM comics WHERE writer IS NOT NULL AND writer != '' ORDER BY writer ASC")
    fun getAllUniqueWritersSync(): List<String>

    /**
     * Get all unique genres (blocking, for Java).
     */
    @Query("SELECT DISTINCT genre FROM comics WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    fun getAllUniqueGenresSync(): List<String>

    /**
     * Get all unique publishers (blocking, for Java).
     */
    @Query(
        "SELECT DISTINCT publisher FROM comics WHERE publisher IS NOT NULL AND publisher != '' ORDER BY publisher ASC",
    )
    fun getAllUniquePublishersSync(): List<String>

    /**
     * Get total comic count (blocking, for Java).
     */
    @Query("SELECT COUNT(*) FROM comics")
    fun getTotalComicCountSync(): Int

    /**
     * Get comics by year (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE year = :year ORDER BY title ASC")
    fun getComicsByYearSync(year: Int): List<ComicBook>

    /**
     * Get comics by writer (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE writer = :writer ORDER BY series ASC, issueNumber ASC")
    fun getComicsByWriterSync(writer: String): List<ComicBook>

    /**
     * Get comics by publisher (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE publisher = :publisher ORDER BY series ASC, issueNumber ASC")
    fun getComicsByPublisherSync(publisher: String): List<ComicBook>

    /**
     * Get comics by series (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE series = :seriesName ORDER BY CAST(issueNumber AS INTEGER) ASC")
    fun getComicsBySeriesSync(seriesName: String): List<ComicBook>

    /**
     * Get comics by story arc (blocking, for Java).
     */
    @Query("SELECT * FROM comics WHERE storyArc = :storyArc ORDER BY title ASC")
    fun getComicsByStoryArcSync(storyArc: String): List<ComicBook>

    /**
     * Delete all comics (blocking, for Java).
     */
    @Query("DELETE FROM comics")
    fun deleteAllComicBooksSync()
}
