package com.comicrack.data.mappers

import com.comicrack.data.database.entities.LibraryPath
import com.comicrack.domain.models.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for LibraryPath entity-domain mappers.
 */
class LibraryMappersTest {
    // ==========================================================================
    // Entity to Domain mapping tests
    // ==========================================================================

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = LibraryPath(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = 1000L,
            lastScannedAt = 2000L,
            comicCount = 100,
            isActive = true,
        )

        val domain = entity.toDomain()

        assertEquals(1, domain.id)
        assertEquals("/storage/comics", domain.path)
        assertEquals("My Comics", domain.displayName)
        assertEquals(1000L, domain.createdAt)
        assertEquals(2000L, domain.lastScannedAt)
        assertEquals(100, domain.comicCount)
        assertTrue(domain.isActive)
    }

    @Test
    fun `toDomain maps inactive library correctly`() {
        val entity = LibraryPath(
            id = 2,
            path = "/storage/old",
            displayName = "Old Comics",
            createdAt = 500L,
            lastScannedAt = 600L,
            comicCount = 50,
            isActive = false,
        )

        val domain = entity.toDomain()

        assertFalse(domain.isActive)
    }

    @Test
    fun `toDomain maps zero comic count correctly`() {
        val entity = LibraryPath(
            id = 3,
            path = "/storage/empty",
            displayName = "Empty",
            createdAt = 1000L,
            lastScannedAt = 0L,
            comicCount = 0,
            isActive = true,
        )

        val domain = entity.toDomain()

        assertEquals(0, domain.comicCount)
        assertEquals(0L, domain.lastScannedAt)
    }

    // ==========================================================================
    // Domain to Entity mapping tests
    // ==========================================================================

    @Test
    fun `toEntity maps all fields correctly`() {
        val domain = Library(
            id = 1,
            path = "/storage/comics",
            displayName = "My Comics",
            createdAt = 1000L,
            lastScannedAt = 2000L,
            comicCount = 100,
            isActive = true,
        )

        val entity = domain.toEntity()

        assertEquals(1, entity.id)
        assertEquals("/storage/comics", entity.path)
        assertEquals("My Comics", entity.displayName)
        assertEquals(1000L, entity.createdAt)
        assertEquals(2000L, entity.lastScannedAt)
        assertEquals(100, entity.comicCount)
        assertTrue(entity.isActive)
    }

    @Test
    fun `toEntity maps inactive library correctly`() {
        val domain = Library(
            id = 2,
            path = "/storage/archived",
            displayName = "Archived",
            createdAt = 500L,
            lastScannedAt = 600L,
            comicCount = 25,
            isActive = false,
        )

        val entity = domain.toEntity()

        assertFalse(entity.isActive)
    }

    // ==========================================================================
    // Roundtrip mapping tests
    // ==========================================================================

    @Test
    fun `roundtrip mapping preserves all data`() {
        val original = LibraryPath(
            id = 42,
            path = "/storage/manga",
            displayName = "Manga Collection",
            createdAt = 12345L,
            lastScannedAt = 67890L,
            comicCount = 500,
            isActive = true,
        )

        val domain = original.toDomain()
        val entity = domain.toEntity()

        assertEquals(original.id, entity.id)
        assertEquals(original.path, entity.path)
        assertEquals(original.displayName, entity.displayName)
        assertEquals(original.createdAt, entity.createdAt)
        assertEquals(original.lastScannedAt, entity.lastScannedAt)
        assertEquals(original.comicCount, entity.comicCount)
        assertEquals(original.isActive, entity.isActive)
    }

    @Test
    fun `roundtrip with inactive library preserves state`() {
        val original = LibraryPath(
            id = 10,
            path = "/storage/test",
            displayName = "Test",
            createdAt = 1L,
            lastScannedAt = 2L,
            comicCount = 0,
            isActive = false,
        )

        val domain = original.toDomain()
        val entity = domain.toEntity()

        assertFalse(entity.isActive)
        assertEquals(0, entity.comicCount)
    }

    @Test
    fun `roundtrip preserves special characters in path and name`() {
        val original = LibraryPath(
            id = 1,
            path = "/storage/Comics & Manga (2024)",
            displayName = "Comics & Manga Collection - 2024",
            createdAt = 1000L,
            lastScannedAt = 2000L,
            comicCount = 100,
            isActive = true,
        )

        val domain = original.toDomain()
        val entity = domain.toEntity()

        assertEquals("/storage/Comics & Manga (2024)", entity.path)
        assertEquals("Comics & Manga Collection - 2024", entity.displayName)
    }
}
