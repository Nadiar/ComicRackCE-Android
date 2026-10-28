package com.comicrack.data.mappers

import com.comicrack.data.database.entities.ReadProgress
import com.comicrack.domain.models.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for ReadProgress entity-domain mappers.
 */
class ReadProgressMappersTest {
    // ==========================================================================
    // Entity to Domain mapping tests
    // ==========================================================================

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = ReadProgress(
            id = 1,
            comicId = 42,
            currentPage = 50,
            totalPages = 100,
            lastReadTime = 123456789L,
            isRead = false,
        )

        val domain = entity.toDomain()

        assertEquals(42, domain.comicId)
        assertEquals(50, domain.currentPage)
        assertEquals(100, domain.totalPages)
        assertEquals(123456789L, domain.lastReadTime)
        assertEquals(false, domain.isRead)
    }

    @Test
    fun `toDomain maps isRead true correctly`() {
        val entity = ReadProgress(
            id = 1,
            comicId = 42,
            currentPage = 99,
            totalPages = 100,
            lastReadTime = 123456789L,
            isRead = true,
        )

        val domain = entity.toDomain()

        assertEquals(true, domain.isRead)
    }

    // ==========================================================================
    // Domain to Entity mapping tests
    // ==========================================================================

    @Test
    fun `toEntity maps all fields correctly`() {
        val domain = ReadingProgress(
            comicId = 42,
            currentPage = 50,
            totalPages = 100,
            lastReadTime = 123456789L,
            isRead = false,
        )

        val entity = domain.toEntity()

        assertEquals(0, entity.id) // ID auto-generated
        assertEquals(42, entity.comicId)
        assertEquals(50, entity.currentPage)
        assertEquals(100, entity.totalPages)
        assertEquals(123456789L, entity.lastReadTime)
        assertEquals(false, entity.isRead)
    }

    @Test
    fun `toEntity always sets id to 0 for Room auto-generation`() {
        val domain = ReadingProgress(
            comicId = 999,
            currentPage = 1,
            totalPages = 1,
            lastReadTime = 0L,
            isRead = false,
        )

        val entity = domain.toEntity()

        assertEquals(0, entity.id)
    }

    // ==========================================================================
    // Roundtrip mapping tests
    // ==========================================================================

    @Test
    fun `roundtrip mapping preserves data`() {
        val original = ReadProgress(
            id = 5,
            comicId = 42,
            currentPage = 75,
            totalPages = 150,
            lastReadTime = 987654321L,
            isRead = false,
        )

        val domain = original.toDomain()
        val entity = domain.toEntity()

        // ID won't match (auto-generated), but all other fields should
        assertEquals(original.comicId, entity.comicId)
        assertEquals(original.currentPage, entity.currentPage)
        assertEquals(original.totalPages, entity.totalPages)
        assertEquals(original.lastReadTime, entity.lastReadTime)
        assertEquals(original.isRead, entity.isRead)
    }

    @Test
    fun `roundtrip with isRead true preserves state`() {
        val original = ReadProgress(
            id = 1,
            comicId = 10,
            currentPage = 99,
            totalPages = 100,
            lastReadTime = 1000L,
            isRead = true,
        )

        val domain = original.toDomain()
        val entity = domain.toEntity()

        assertEquals(true, entity.isRead)
        assertEquals(99, entity.currentPage)
    }
}
