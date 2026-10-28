package com.comicrack.reader.cache

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import timber.log.Timber

/**
 * LRU bitmap cache for comic pages.
 *
 * Caches recently viewed and adjacent pages to improve scrolling performance.
 * Uses LinkedHashMap for LRU eviction when cache exceeds max size.
 *
 * Typical usage:
 * - Cache current page + pages -1, +1 relative to current
 * - Max 50MB default (configurable for high-quality comics)
 * - Auto-evict oldest page when limit exceeded
 *
 * Phase 3: Task 3.1 â€” Bitmap Caching Implementation
 */
class BitmapCache(
    private val maxCacheSizeBytes: Long = 50 * 1024 * 1024, // 50MB default
) {
    private val TAG = "BitmapCache"

    // LinkedHashMap maintains insertion order for LRU eviction
    private val cache = LinkedHashMap<String, CacheEntry>(16, 0.75f, true) // accessOrder=true for LRU
    private var currentSizeBytes = 0L
    private var archiveKey = "" // Track current archive to clear on change

    data class CacheEntry(
        val bitmap: Bitmap,
        val pageIndex: Int,
        val sizeBytes: Long,
    )

    /**
     * Get bitmap from cache (marks as recently used).
     * Returns null if not cached.
     */
    fun get(pageIndex: Int): Bitmap? {
        val key = "$archiveKey:page_$pageIndex"
        val entry = cache[key]
        if (entry != null) {
            Timber.tag(TAG).d("Cache HIT: page $pageIndex")
            return entry.bitmap
        }
        Timber.tag(TAG).d("Cache MISS: page $pageIndex")
        return null
    }

    /**
     * Put bitmap in cache, evicting old entries if needed.
     * Returns evicted entries for cleanup if any.
     */
    fun put(
        pageIndex: Int,
        bitmap: Bitmap,
    ): List<Bitmap> {
        val key = "$archiveKey:page_$pageIndex"

        // Remove if already cached (will re-add with updated order)
        if (cache.containsKey(key)) {
            val old = cache.remove(key)
            currentSizeBytes -= old?.sizeBytes ?: 0
        }

        val bitmapSizeBytes = (bitmap.byteCount).toLong()
        val entry = CacheEntry(bitmap, pageIndex, bitmapSizeBytes)
        cache[key] = entry
        currentSizeBytes += bitmapSizeBytes

        Timber
            .tag(
                TAG,
            ).d(
                "Cached page $pageIndex (${formatBytes(
                    bitmapSizeBytes,
                )}). Cache: ${formatBytes(currentSizeBytes)}/${formatBytes(maxCacheSizeBytes)}",
            )

        // Evict oldest entries if cache exceeds limit
        val evicted = mutableListOf<Bitmap>()
        while (currentSizeBytes > maxCacheSizeBytes && cache.isNotEmpty()) {
            val oldestKey = cache.keys.first() // LinkedHashMap maintains order
            val oldestEntry = cache.remove(oldestKey)
            if (oldestEntry != null) {
                evicted.add(oldestEntry.bitmap)
                currentSizeBytes -= oldestEntry.sizeBytes
                Timber.tag(TAG).d("Evicted: $oldestKey (${formatBytes(oldestEntry.sizeBytes)})")
            }
        }

        return evicted
    }

    /**
     * Clear cache for new archive.
     * Call this when navigating to a different comic.
     */
    fun setArchive(archivePath: String) {
        if (archivePath != archiveKey) {
            Timber.tag(TAG).d("New archive: clearing cache")
            clear()
            archiveKey = archivePath
        }
    }

    /**
     * Clear all cached bitmaps.
     */
    fun clear() {
        cache.forEach { (_, entry) ->
            // Bitmaps will be garbage collected when removed from map
        }
        cache.clear()
        currentSizeBytes = 0
        Timber.tag(TAG).d("Cache cleared")
    }

    /**
     * Get cache statistics.
     */
    fun getStats(): CacheStats =
        CacheStats(
            sizeBytesUsed = currentSizeBytes,
            maxSizeBytes = maxCacheSizeBytes,
            entryCount = cache.size,
            archiveKey = archiveKey,
        )

    data class CacheStats(
        val sizeBytesUsed: Long,
        val maxSizeBytes: Long,
        val entryCount: Int,
        val archiveKey: String,
    )

    private fun formatBytes(bytes: Long): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
}
