package com.comicrack.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ReadingProgress domain model business logic.
 */
class ReadingProgressTest {
    // ==========================================================================
    // Percent complete calculation tests
    // ==========================================================================

    @Test
    fun `percentComplete returns 0 when totalPages is 0`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 0,
            totalPages = 0,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )
        assertEquals(0, progress.percentComplete())
    }

    @Test
    fun `percentComplete returns 0 when on first page`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 0,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )
        assertEquals(0, progress.percentComplete())
    }

    @Test
    fun `percentComplete returns 50 when halfway through`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 50,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )
        assertEquals(50, progress.percentComplete())
    }

    @Test
    fun `percentComplete returns 100 when on last page`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 99,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )
        assertEquals(99, progress.percentComplete())
    }

    @Test
    fun `percentComplete rounds correctly`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 33,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )
        assertEquals(33, progress.percentComplete())
    }

    // ==========================================================================
    // updatePage tests
    // ==========================================================================

    @Test
    fun `updatePage returns new instance with updated page`() {
        val original = ReadingProgress(
            comicId = 1,
            currentPage = 10,
            totalPages = 100,
            lastReadTime = 1000L,
            isRead = false,
        )

        val updated = original.updatePage(20)

        assertEquals(20, updated.currentPage)
        assertEquals(10, original.currentPage) // Original unchanged
    }

    @Test
    fun `updatePage updates timestamp`() {
        val original = ReadingProgress(
            comicId = 1,
            currentPage = 10,
            totalPages = 100,
            lastReadTime = 1000L,
            isRead = false,
        )

        val updated = original.updatePage(20)

        assertTrue(updated.lastReadTime > original.lastReadTime)
    }

    @Test
    fun `updatePage marks as read when reaching last page`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 10,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )

        val updated = progress.updatePage(99)

        assertTrue(updated.isRead)
    }

    @Test
    fun `updatePage does not mark as read when not on last page`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 10,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )

        val updated = progress.updatePage(50)

        assertFalse(updated.isRead)
    }

    // ==========================================================================
    // markAsRead tests
    // ==========================================================================

    @Test
    fun `markAsRead sets isRead to true`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 50,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )

        val marked = progress.markAsRead()

        assertTrue(marked.isRead)
    }

    @Test
    fun `markAsRead sets currentPage to last page`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 50,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )

        val marked = progress.markAsRead()

        assertEquals(99, marked.currentPage)
    }

    @Test
    fun `markAsRead returns new instance`() {
        val original = ReadingProgress(
            comicId = 1,
            currentPage = 50,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = false,
        )

        val marked = original.markAsRead()

        assertFalse(original.isRead) // Original unchanged
        assertTrue(marked.isRead)
    }

    // ==========================================================================
    // markAsUnread tests
    // ==========================================================================

    @Test
    fun `markAsUnread sets isRead to false`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 99,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = true,
        )

        val marked = progress.markAsUnread()

        assertFalse(marked.isRead)
    }

    @Test
    fun `markAsUnread resets currentPage to 0`() {
        val progress = ReadingProgress(
            comicId = 1,
            currentPage = 99,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = true,
        )

        val marked = progress.markAsUnread()

        assertEquals(0, marked.currentPage)
    }

    @Test
    fun `markAsUnread returns new instance`() {
        val original = ReadingProgress(
            comicId = 1,
            currentPage = 99,
            totalPages = 100,
            lastReadTime = System.currentTimeMillis(),
            isRead = true,
        )

        val marked = original.markAsUnread()

        assertTrue(original.isRead) // Original unchanged
        assertFalse(marked.isRead)
    }
}
