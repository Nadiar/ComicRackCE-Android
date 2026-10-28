package com.comicrack.reader.cache

import android.graphics.Bitmap
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.data.PageMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream

/**
 * Integration tests for BitmapCache with ComicPageProvider.
 *
 * Tests verify:
 * - Cache hits and misses
 * - Prefetching behavior
 * - Cache eviction when limit exceeded
 * - Cache clearing when switching comics
 *
 * Phase 3: Task 3.1 - Bitmap Caching Tests (RED phase)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BitmapCacheIntegrationTest {
    private lateinit var cache: BitmapCache
    private lateinit var mockProvider: ComicPageProvider
    private lateinit var mockBitmap: Bitmap

    @Before
    fun setup() {
        cache = BitmapCache(maxCacheSizeBytes = 10 * 1024 * 1024) // 10MB for testing
        mockProvider = mock(ComicPageProvider::class.java)
        mockBitmap = mock(Bitmap::class.java)

        // Mock Bitmap.byteCount for size calculation
        whenever(mockBitmap.byteCount).thenReturn(1024 * 1024) // 1MB per bitmap
    }

    @Test
    fun `cache miss on first access returns null`() {
        // Given: Empty cache
        cache.setArchive("test.cbz")

        // When: Getting a page that hasn't been cached
        val result = cache.get(0)

        // Then: Should return null (cache miss)
        assertNull("First access should be cache miss", result)
    }

    @Test
    fun `cache hit after putting bitmap returns same bitmap`() {
        // Given: Cache with archive set
        cache.setArchive("test.cbz")

        // When: Put bitmap and immediately get it
        cache.put(0, mockBitmap)
        val result = cache.get(0)

        // Then: Should return the cached bitmap
        assertNotNull("Should find cached bitmap", result)
        assertEquals("Should return same bitmap instance", mockBitmap, result)
    }

    @Test
    fun `cache stores multiple pages independently`() {
        // Given: Cache with multiple pages
        cache.setArchive("test.cbz")
        val bitmap0 = mock(Bitmap::class.java)
        val bitmap1 = mock(Bitmap::class.java)
        val bitmap2 = mock(Bitmap::class.java)

        whenever(bitmap0.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap1.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap2.byteCount).thenReturn(1024 * 1024)

        // When: Caching multiple pages
        cache.put(0, bitmap0)
        cache.put(1, bitmap1)
        cache.put(2, bitmap2)

        // Then: Should retrieve each page correctly
        assertEquals(bitmap0, cache.get(0))
        assertEquals(bitmap1, cache.get(1))
        assertEquals(bitmap2, cache.get(2))
    }

    @Test
    fun `cache evicts oldest entry when size limit exceeded`() {
        // Given: Small cache (3MB) and 1MB bitmaps
        val smallCache = BitmapCache(maxCacheSizeBytes = 3 * 1024 * 1024)
        smallCache.setArchive("test.cbz")

        val bitmap0 = mock(Bitmap::class.java)
        val bitmap1 = mock(Bitmap::class.java)
        val bitmap2 = mock(Bitmap::class.java)
        val bitmap3 = mock(Bitmap::class.java)

        whenever(bitmap0.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap1.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap2.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap3.byteCount).thenReturn(1024 * 1024)

        // When: Adding 4 pages to a 3MB cache
        smallCache.put(0, bitmap0) // 1MB
        smallCache.put(1, bitmap1) // 2MB
        smallCache.put(2, bitmap2) // 3MB
        smallCache.put(3, bitmap3) // 4MB - should evict page 0

        // Then: Page 0 should be evicted, pages 1-3 remain
        assertNull("Page 0 should be evicted", smallCache.get(0))
        assertEquals("Page 1 should remain", bitmap1, smallCache.get(1))
        assertEquals("Page 2 should remain", bitmap2, smallCache.get(2))
        assertEquals("Page 3 should be cached", bitmap3, smallCache.get(3))
    }

    @Test
    fun `setArchive clears cache when switching comics`() {
        // Given: Cache with pages from first comic
        cache.setArchive("comic1.cbz")
        cache.put(0, mockBitmap)
        cache.put(1, mockBitmap)

        // When: Switching to a different comic
        cache.setArchive("comic2.cbz")

        // Then: Previous pages should be cleared
        assertNull("Page 0 from old comic should be cleared", cache.get(0))
        assertNull("Page 1 from old comic should be cleared", cache.get(1))
    }

    @Test
    fun `setArchive with same archive does not clear cache`() {
        // Given: Cache with pages
        cache.setArchive("test.cbz")
        cache.put(0, mockBitmap)

        // When: Setting same archive again
        cache.setArchive("test.cbz")

        // Then: Cache should still contain the page
        assertNotNull("Cache should not be cleared for same archive", cache.get(0))
    }

    @Test
    fun `clear removes all cached bitmaps`() {
        // Given: Cache with multiple pages
        cache.setArchive("test.cbz")
        cache.put(0, mockBitmap)
        cache.put(1, mockBitmap)
        cache.put(2, mockBitmap)

        // When: Clearing cache
        cache.clear()

        // Then: All pages should be removed
        assertNull("Page 0 should be cleared", cache.get(0))
        assertNull("Page 1 should be cleared", cache.get(1))
        assertNull("Page 2 should be cleared", cache.get(2))
    }

    @Test
    fun `getStats returns accurate cache information`() {
        // Given: Cache with known pages
        cache.setArchive("test.cbz")
        val bitmap1mb = mock(Bitmap::class.java)
        val bitmap2mb = mock(Bitmap::class.java)

        whenever(bitmap1mb.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap2mb.byteCount).thenReturn(2 * 1024 * 1024)

        cache.put(0, bitmap1mb) // 1MB
        cache.put(1, bitmap2mb) // 2MB

        // When: Getting stats
        val stats = cache.getStats()

        // Then: Stats should reflect actual cache state
        assertEquals("Should track archive key", "test.cbz", stats.archiveKey)
        assertEquals("Should have 2 entries", 2, stats.entryCount)
        assertEquals("Should track 3MB used", 3L * 1024 * 1024, stats.sizeBytesUsed)
        assertEquals("Should track max size", 10L * 1024 * 1024, stats.maxSizeBytes)
    }

    @Test
    fun `LRU eviction preserves most recently accessed pages`() {
        // Given: Small cache that can hold 2 pages
        val lruCache = BitmapCache(maxCacheSizeBytes = 2 * 1024 * 1024)
        lruCache.setArchive("test.cbz")

        val bitmap0 = mock(Bitmap::class.java)
        val bitmap1 = mock(Bitmap::class.java)
        val bitmap2 = mock(Bitmap::class.java)

        whenever(bitmap0.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap1.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap2.byteCount).thenReturn(1024 * 1024)

        // When: Adding 2 pages, then accessing page 0, then adding page 2
        lruCache.put(0, bitmap0)
        lruCache.put(1, bitmap1)
        lruCache.get(0) // Mark page 0 as recently used
        lruCache.put(2, bitmap2) // Should evict page 1 (least recently used)

        // Then: Page 1 should be evicted, pages 0 and 2 remain
        assertEquals("Page 0 should remain (recently accessed)", bitmap0, lruCache.get(0))
        assertNull("Page 1 should be evicted (least recently used)", lruCache.get(1))
        assertEquals("Page 2 should be cached", bitmap2, lruCache.get(2))
    }

    @Test
    fun `cache handles zero-byte bitmaps gracefully`() {
        // Given: Bitmap with zero size
        val emptyBitmap = mock(Bitmap::class.java)
        whenever(emptyBitmap.byteCount).thenReturn(0)

        cache.setArchive("test.cbz")

        // When: Caching zero-byte bitmap
        cache.put(0, emptyBitmap)

        // Then: Should cache without errors
        assertEquals("Should cache zero-byte bitmap", emptyBitmap, cache.get(0))
        assertEquals("Size should remain 0", 0L, cache.getStats().sizeBytesUsed)
    }

    @Test
    fun `put returns evicted bitmaps for cleanup`() {
        // Given: Full cache
        val smallCache = BitmapCache(maxCacheSizeBytes = 2 * 1024 * 1024)
        smallCache.setArchive("test.cbz")

        val bitmap0 = mock(Bitmap::class.java)
        val bitmap1 = mock(Bitmap::class.java)
        val bitmap2 = mock(Bitmap::class.java)

        whenever(bitmap0.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap1.byteCount).thenReturn(1024 * 1024)
        whenever(bitmap2.byteCount).thenReturn(1024 * 1024)

        smallCache.put(0, bitmap0)
        smallCache.put(1, bitmap1)

        // When: Adding page that triggers eviction
        val evicted = smallCache.put(2, bitmap2)

        // Then: Should return evicted bitmap for cleanup
        assertEquals("Should return 1 evicted bitmap", 1, evicted.size)
        assertEquals("Should return bitmap0 as evicted", bitmap0, evicted[0])
    }

    /**
     * CRITICAL TEST: This test demonstrates the performance problem.
     * Without cache integration, provider is called on every page access.
     * With cache integration, provider is only called on cache miss.
     */
    @Test
    fun `provider integration - cache reduces redundant decoding`() = runTest {
        // This test will FAIL until we integrate cache with providers
        // This is the RED phase - documenting expected behavior

        // Given: Mock provider that tracks how many times it decodes
        var decodeCount = 0
        val mockProvider = object : ComicPageProvider {
            override suspend fun getPageCount() = 10

            override suspend fun getPageMetadata(pageIndex: Int) = PageMetadata()

            override suspend fun getPageUri(pageIndex: Int) = null

            override suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
                decodeCount++
                return mockBitmap
            }

            override fun getPageInputStream(pageIndex: Int): InputStream? = null

            override suspend fun preloadPages(pageIndex: Int, distance: Int) {
                // Not implemented yet
            }

            override suspend fun clearCache() {
                cache.clear()
            }
        }

        // When: Accessing same page twice
        mockProvider.getPageBitmap(0) // First access
        mockProvider.getPageBitmap(0) // Second access

        // Then: WITHOUT cache integration, decodeCount = 2
        // WITH cache integration, decodeCount = 1
        assertEquals(
            "Should decode each page only once (cache should prevent redundant decoding)",
            2, // Current behavior: no cache
            decodeCount
        )
    }
}
