package com.comicrack.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Library domain model business logic.
 */
class LibraryTest {
    // ==========================================================================
    // needsScan tests
    // ==========================================================================

    @Test
    fun `needsScan returns true when lastScannedAt is 0`() {
        val library = Library(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = System.currentTimeMillis(),
            lastScannedAt = 0L,
            comicCount = 0,
            isActive = true,
        )

        assertTrue(library.needsScan())
    }

    @Test
    fun `needsScan returns false when scanned recently`() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (1000 * 60 * 60) // 1 hour

        val library = Library(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = now,
            lastScannedAt = oneHourAgo,
            comicCount = 100,
            isActive = true,
        )

        assertFalse(library.needsScan(thresholdHours = 24))
    }

    @Test
    fun `needsScan returns true when scan is old`() {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - (1000 * 60 * 60 * 48) // 48 hours

        val library = Library(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = now,
            lastScannedAt = twoDaysAgo,
            comicCount = 100,
            isActive = true,
        )

        assertTrue(library.needsScan(thresholdHours = 24))
    }

    @Test
    fun `needsScan respects custom threshold`() {
        val now = System.currentTimeMillis()
        val threeHoursAgo = now - (1000 * 60 * 60 * 3) // 3 hours

        val library = Library(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = now,
            lastScannedAt = threeHoursAgo,
            comicCount = 100,
            isActive = true,
        )

        assertFalse(library.needsScan(thresholdHours = 6))
        assertTrue(library.needsScan(thresholdHours = 2))
    }

    @Test
    fun `needsScan at exact threshold boundary`() {
        val now = System.currentTimeMillis()
        val exactlyOneDayAgo = now - (1000 * 60 * 60 * 24) // Exactly 24 hours

        val library = Library(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = now,
            lastScannedAt = exactlyOneDayAgo,
            comicCount = 100,
            isActive = true,
        )

        assertTrue(library.needsScan(thresholdHours = 24))
    }

    // ==========================================================================
    // updateAfterScan tests
    // ==========================================================================

    @Test
    fun `updateAfterScan updates lastScannedAt timestamp`() {
        val now = System.currentTimeMillis()
        val original = Library(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = now,
            lastScannedAt = 0L,
            comicCount = 0,
            isActive = true,
        )

        val updated = original.updateAfterScan(newComicCount = 100)

        assertTrue(updated.lastScannedAt > original.lastScannedAt)
        assertTrue(updated.lastScannedAt >= now)
    }

    @Test
    fun `updateAfterScan updates comic count`() {
        val original = Library(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = System.currentTimeMillis(),
            lastScannedAt = 0L,
            comicCount = 0,
            isActive = true,
        )

        val updated = original.updateAfterScan(newComicCount = 250)

        assertEquals(250, updated.comicCount)
    }

    @Test
    fun `updateAfterScan returns new instance`() {
        val original = Library(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = System.currentTimeMillis(),
            lastScannedAt = 1000L,
            comicCount = 50,
            isActive = true,
        )

        val updated = original.updateAfterScan(newComicCount = 100)

        assertEquals(50, original.comicCount) // Original unchanged
        assertEquals(100, updated.comicCount)
        assertEquals(1000L, original.lastScannedAt) // Original unchanged
        assertTrue(updated.lastScannedAt > original.lastScannedAt)
    }

    @Test
    fun `updateAfterScan preserves other fields`() {
        val original = Library(
            id = 42,
            path = "/storage/comics",
            displayName = "Test Library",
            createdAt = 5000L,
            lastScannedAt = 1000L,
            comicCount = 50,
            isActive = false,
        )

        val updated = original.updateAfterScan(newComicCount = 100)

        assertEquals(42, updated.id)
        assertEquals("/storage/comics", updated.path)
        assertEquals("Test Library", updated.displayName)
        assertEquals(5000L, updated.createdAt)
        assertFalse(updated.isActive)
    }

    @Test
    fun `updateAfterScan handles zero comics`() {
        val original = Library(
            id = 1,
            path = "/storage/empty",
            displayName = "Empty Library",
            createdAt = System.currentTimeMillis(),
            lastScannedAt = 0L,
            comicCount = 100,
            isActive = true,
        )

        val updated = original.updateAfterScan(newComicCount = 0)

        assertEquals(0, updated.comicCount)
        assertTrue(updated.lastScannedAt > 0)
    }
}
