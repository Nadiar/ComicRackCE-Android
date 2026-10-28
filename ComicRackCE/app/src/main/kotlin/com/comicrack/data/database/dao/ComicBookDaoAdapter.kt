package com.comicrack.data.database.dao

// DOCFIX: doc-only pass - 2026-01-15 (docfix/pilot-comments)

import com.comicrack.data.database.entities.ComicBook
import kotlinx.coroutines.runBlocking

/**
 * Java-compatible adapter for ComicBookDao (Kotlin suspend functions).
 *
 * Provides blocking methods that wrap the suspend functions for Java code.
 * Usage: call from Java with blocking = true
 *
 * TECHNICAL DEBT NOTE: This class uses runBlocking intentionally for Java interop.
 * All methods in this adapter are designed to provide synchronous access from Java code.
 * Do not call these methods from Kotlin coroutines or UI threads - instead, use the suspend
 * functions directly via proper async/await patterns.
 *
 * Phase 1: Java Interop Layer
 */
class ComicBookDaoAdapter(
    private val dao: ComicBookDao,
) {
    fun insertComicBook(comic: ComicBook): Long =
        runBlocking {
            dao.insertComicBook(comic)
        }

    fun insertComicBooks(vararg comics: ComicBook) =
        runBlocking {
            dao.insertComicBooks(*comics)
        }

    fun updateComicBook(comic: ComicBook) =
        runBlocking {
            dao.updateComicBook(comic)
        }

    fun upsertComicBook(comic: ComicBook): Long =
        runBlocking {
            dao.upsertComicBook(comic)
        }

    fun deleteComicBookById(id: Long) =
        runBlocking {
            dao.deleteComicBookById(id)
        }

    fun deleteBySourcePath(source: String) =
        runBlocking {
            dao.deleteBySourcePath(source)
        }

    fun deleteComicBook(comic: ComicBook) =
        runBlocking {
            dao.deleteComicBook(comic)
        }

    fun getComicsByLibraryPath(libraryPathId: Long): List<ComicBook> =
        runBlocking {
            dao.getComicsByLibraryPath(libraryPathId)
        }

    fun getComicBookById(id: Long): ComicBook? =
        runBlocking {
            dao.getComicBookById(id)
        }

    fun getComicBookByPath(filePath: String): ComicBook? =
        runBlocking {
            dao.getComicBookByPath(filePath)
        }

    fun searchComicsByTitle(titleQuery: String): List<ComicBook> =
        runBlocking {
            dao.searchComicsByTitle(titleQuery)
        }

    fun searchComicsByFilename(filenameQuery: String): List<ComicBook> =
        runBlocking {
            dao.searchComicsByFilename(filenameQuery)
        }

    fun getComicsByFormat(format: String): List<ComicBook> =
        runBlocking {
            dao.getComicsByFormat(format)
        }

    fun getComicCountByLibraryPath(libraryPathId: Long): Int =
        runBlocking {
            dao.getComicCountByLibraryPath(libraryPathId)
        }

    fun getUnscannedComics(): List<ComicBook> =
        runBlocking {
            dao.getUnscannedComics()
        }

    fun deleteComicsByLibraryPath(libraryPathId: Long) =
        runBlocking {
            dao.deleteComicsByLibraryPath(libraryPathId)
        }

    fun deleteAllComicBooks() =
        runBlocking {
            dao.deleteAllComicBooks()
        }

    fun getComicBySource(source: String): ComicBook? =
        runBlocking {
            dao.getComicBySource(source)
        }

    fun getComicBySourceAndModified(
        source: String,
        sourceModified: Long,
    ): ComicBook? =
        runBlocking {
            dao.getComicBySourceAndModified(source, sourceModified)
        }

    fun getAllSources(): List<String> =
        runBlocking {
            dao.getAllSources()
        }

    fun getAllComics(): List<ComicBook> =
        runBlocking {
            dao.getAllComics()
        }

    fun deleteComicsNotInSources(sources: List<String>) =
        runBlocking {
            dao.deleteComicsNotInSources(sources)
        }

    fun searchComicsByQuery(query: String): List<ComicBook> =
        runBlocking {
            dao.searchComicsByQuery(query)
        }

    fun searchComicsByFieldValue(fieldValue: String): List<ComicBook> =
        runBlocking {
            dao.searchComicsByFieldValue(fieldValue)
        }

    fun getAllComicsForIteration(): List<ComicBook> =
        runBlocking {
            dao.getAllComicsForIteration()
        }

    fun searchComicsByCaption(query: String): List<ComicBook> =
        runBlocking {
            dao.searchComicsByCaption(query)
        }

    fun searchComicsByFullSeries(query: String): List<ComicBook> =
        runBlocking {
            dao.searchComicsByFullSeries(query)
        }

    fun getAllUniqueSeries(): List<String> =
        runBlocking {
            dao.getAllUniqueSeries()
        }

    fun getAllUniqueWriters(): List<String> =
        runBlocking {
            dao.getAllUniqueWriters()
        }

    fun getAllUniqueGenres(): List<String> =
        runBlocking {
            dao.getAllUniqueGenres()
        }

    fun getAllUniquePublishers(): List<String> =
        runBlocking {
            dao.getAllUniquePublishers()
        }

    fun getTotalComicCount(): Int =
        runBlocking {
            dao.getTotalComicCount()
        }

    fun getComicsByYear(year: Int): List<ComicBook> =
        runBlocking {
            dao.getComicsByYear(year)
        }

    fun getComicsByWriter(writer: String): List<ComicBook> =
        runBlocking {
            dao.getComicsByWriter(writer)
        }

    fun getComicsByPublisher(publisher: String): List<ComicBook> =
        runBlocking {
            dao.getComicsByPublisher(publisher)
        }

    fun getComicsBySeries(seriesName: String): List<ComicBook> =
        runBlocking {
            dao.getComicsBySeries(seriesName)
        }

    fun getComicsByStoryArc(storyArc: String): List<ComicBook> =
        runBlocking {
            dao.getComicsByStoryArc(storyArc)
        }
}
