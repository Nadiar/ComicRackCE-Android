package com.comicrack.core.cache

import com.comicrack.data.database.dao.ComicBookDao
import com.comicrack.data.database.entities.ComicBook
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Tests for [CacheEvictionPolicy].
 */
class CacheEvictionPolicyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var comicDao: ComicBookDao
    private lateinit var policy: CacheEvictionPolicy

    @Before
    fun setup() {
        comicDao = mock()
        policy = CacheEvictionPolicy(comicDao)
    }

    // ==========================================================================
    // Constants
    // ==========================================================================

    @Test
    fun `default max cache is 2 GB`() {
        assertEquals(2L * 1024 * 1024 * 1024, CacheEvictionPolicy.DEFAULT_MAX_CACHE_BYTES)
    }

    // ==========================================================================
    // No eviction needed
    // ==========================================================================

    @Test
    fun `evictIfNeeded returns 0 when cache is under limit`() = runTest {
        val cacheDir = tempFolder.newFolder("cache")
        createFileInDir(cacheDir, "small.cbz", 100)

        val freed = policy.evictIfNeeded(cacheDir, maxBytes = 1024)

        assertEquals(0L, freed)
    }

    @Test
    fun `evictIfNeeded returns 0 when cache dir does not exist`() = runTest {
        val nonExistent = File(tempFolder.root, "doesnotexist")

        val freed = policy.evictIfNeeded(nonExistent, maxBytes = 1024)

        assertEquals(0L, freed)
    }

    @Test
    fun `evictIfNeeded returns 0 when cache dir is empty`() = runTest {
        val cacheDir = tempFolder.newFolder("empty_cache")

        val freed = policy.evictIfNeeded(cacheDir, maxBytes = 1024)

        assertEquals(0L, freed)
    }

    // ==========================================================================
    // Eviction
    // ==========================================================================

    @Test
    fun `evictIfNeeded deletes files when over limit`() = runTest {
        val cacheDir = tempFolder.newFolder("cache_over")
        val cachedFile = createFileInDir(cacheDir, "cached_comic.cbz", 500)

        val comic = ComicBook().apply {
            id = 1
            cacheLocalPath = cachedFile.absolutePath
        }
        whenever(comicDao.getCachedRemoteComics()).thenReturn(listOf(comic))

        val freed = policy.evictIfNeeded(cacheDir, maxBytes = 100)

        assertTrue(freed > 0)
        verify(comicDao).updateCachePath(eq(1L), eq(null))
    }

    @Test
    fun `evictIfNeeded clears DB path for non-existent cache files`() = runTest {
        val cacheDir = tempFolder.newFolder("cache_broken")
        // Create a file to make dir non-empty and over limit
        createFileInDir(cacheDir, "dummy.dat", 500)

        val comic = ComicBook().apply {
            id = 2
            cacheLocalPath = "/nonexistent/path/comic.cbz"
        }
        whenever(comicDao.getCachedRemoteComics()).thenReturn(listOf(comic))

        policy.evictIfNeeded(cacheDir, maxBytes = 100)

        // Should still clear the DB reference even though file doesn't exist
        verify(comicDao).updateCachePath(eq(2L), eq(null))
    }

    @Test
    fun `evictIfNeeded skips comics with null cache path`() = runTest {
        val cacheDir = tempFolder.newFolder("cache_null")
        createFileInDir(cacheDir, "dummy.dat", 500)

        val comic = ComicBook().apply {
            id = 3
            cacheLocalPath = null
        }
        whenever(comicDao.getCachedRemoteComics()).thenReturn(listOf(comic))

        policy.evictIfNeeded(cacheDir, maxBytes = 100)

        verify(comicDao, never()).updateCachePath(eq(3L), any())
    }

    @Test
    fun `evictIfNeeded stops once enough space is freed`() = runTest {
        val cacheDir = tempFolder.newFolder("cache_partial")
        val file1 = createFileInDir(cacheDir, "old1.cbz", 300)
        val file2 = createFileInDir(cacheDir, "old2.cbz", 300)

        val comic1 = ComicBook().apply {
            id = 1
            cacheLocalPath = file1.absolutePath
        }
        val comic2 = ComicBook().apply {
            id = 2
            cacheLocalPath = file2.absolutePath
        }
        whenever(comicDao.getCachedRemoteComics()).thenReturn(listOf(comic1, comic2))

        // Over by 100 (600 total, 500 limit) - only need to evict one 300-byte file
        val freed = policy.evictIfNeeded(cacheDir, maxBytes = 500)

        assertTrue(freed >= 100) // At least freed enough
        // Should have evicted at least one file
        verify(comicDao).updateCachePath(eq(1L), eq(null))
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private fun createFileInDir(dir: File, name: String, sizeBytes: Int): File {
        val file = File(dir, name)
        file.writeBytes(ByteArray(sizeBytes))
        return file
    }
}
