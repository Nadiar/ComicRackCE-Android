package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import com.comicrack.reader.cache.BitmapCache
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.data.PageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Real archive provider implementation for CB7 (7zip) files.
 *
 * Handles 7zip archive extraction and page bitmap generation.
 * Uses Apache Commons Compress library for 7z format support.
 *
 * Phase 3: Integrated with BitmapCache for performance improvements.
 *
 * @param archivePath Path to the CB7/7z file
 * @param seriesName Name of the comic series
 * @param issueNumber Issue number
 * @param bitmapCache Optional bitmap cache for page caching (default: creates new cache)
 */
class Cb7ArchiveProvider(
    private val archivePath: String,
    val seriesName: String,
    val issueNumber: String,
    private val bitmapCache: BitmapCache = BitmapCache(),
) : ComicPageProvider {
    private var sevenZFile: SevenZFile? = null
    private var imageEntries: List<SevenZArchiveEntry> = emptyList()

    init {
        try {
            sevenZFile = SevenZFile(File(archivePath))
            loadImageEntries()
            bitmapCache.setArchive(archivePath) // Phase 3
        } catch (e: IOException) {
            throw RuntimeException("Failed to open CB7 archive: $archivePath", e)
        }
    }

    /**
     * Load and filter image entries from the 7zip archive.
     * Sorts them alphabetically to ensure consistent page ordering.
     * Uses functional filtering to reduce nesting depth.
     */
    private fun loadImageEntries() {
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heif", "heic")

        try {
            imageEntries = sevenZFile?.entries
                ?.filterNot { it.isDirectory }
                ?.filter { isValid7ZImageEntry(it, imageExtensions) }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.tag("Cb7ArchiveProvider").e(e, "Error loading image entries")
            imageEntries = emptyList()
        }
    }

    /**
     * Check if a 7Z entry is a valid image file. Reduces nesting in loadImageEntries.
     */
    private fun isValid7ZImageEntry(entry: SevenZArchiveEntry, imageExtensions: Set<String>): Boolean {
        val name = entry.name.lowercase()
        val extension = name.substringAfterLast(".")
        
        // Skip system directories
        if (name.contains("__macosx") || name.startsWith(".")) return false
        
        // Check image extension
        return extension in imageExtensions
    }

    override suspend fun getPageCount(): Int = imageEntries.size

    override suspend fun getPageMetadata(pageIndex: Int): PageMetadata {
        if (pageIndex < 0 || pageIndex >= imageEntries.size) {
            throw IndexOutOfBoundsException("Page index out of bounds: $pageIndex")
        }

        val dimensions = getImageDimensions(pageIndex)

        return PageMetadata(
            seriesName = seriesName,
            issueNumber = issueNumber,
            pageNumber = pageIndex + 1,
            totalPages = imageEntries.size,
            estimatedWidth = dimensions?.first ?: 1024,
            estimatedHeight = dimensions?.second ?: 1536,
        )
    }

    override suspend fun getPageUri(pageIndex: Int): String? {
        // For local files, return null as we'll use getPageBitmap instead
        return null
    }

    override suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        return try {
            if (pageIndex < 0 || pageIndex >= imageEntries.size) {
                return null
            }

            // Check cache first - Phase 3: Cache integration
            val cached = bitmapCache.get(pageIndex)
            if (cached != null) {
                Timber.tag("Cb7ArchiveProvider").d("Cache HIT for page $pageIndex")
                return cached
            }

            // Cache miss - decode from archive
            Timber.tag("Cb7ArchiveProvider").d("Cache MISS for page $pageIndex - decoding from archive")
            val entry = imageEntries[pageIndex]

            // Use optimized bitmap decoding to prevent OOM on large images
            val bitmap = BitmapDecodingUtils.decodeBitmapOptimized(
                openStream = {
                    sevenZFile?.getInputStream(entry)
                },
            )

            // Store in cache if successfully decoded - Phase 3: Cache integration
            if (bitmap != null) {
                bitmapCache.put(pageIndex, bitmap)
                Timber.tag("Cb7ArchiveProvider").d("Cached page $pageIndex")
            }

            bitmap
        } catch (e: Exception) {
            Timber.tag("Cb7ArchiveProvider").e(e, "Failed to decode page bitmap")
            null
        }
    }

    override fun getPageInputStream(pageIndex: Int): java.io.InputStream? {
        return try {
            if (pageIndex < 0 || pageIndex >= imageEntries.size) {
                return null
            }
            val entry = imageEntries[pageIndex]
            sevenZFile?.getInputStream(entry)
        } catch (e: Exception) {
            Timber.tag("Cb7ArchiveProvider").e(e, "Failed to get page input stream")
            null
        }
    }

    override suspend fun preloadPages(
        pageIndex: Int,
        distance: Int,
    ) {
        // Phase 3: Implemented with BitmapCache integration
        // Preload adjacent pages in background for smooth scrolling
        withContext(Dispatchers.IO) {
            val startIndex = (pageIndex - distance).coerceAtLeast(0)
            val endIndex = (pageIndex + distance).coerceAtMost(imageEntries.size - 1)

            for (i in startIndex..endIndex) {
                // Skip current page and already cached pages
                if (i == pageIndex || bitmapCache.get(i) != null) {
                    continue
                }

                // Preload page into cache
                try {
                    getPageBitmap(i) // This will cache the page
                    Timber.tag("Cb7ArchiveProvider").d("Preloaded page $i")
                } catch (e: Exception) {
                    Timber.tag("Cb7ArchiveProvider").w(e, "Failed to preload page $i")
                    // Continue preloading other pages
                }
            }
        }
    }

    override suspend fun clearCache() {
        // Phase 3: Implemented with BitmapCache integration
        // Clear bitmap cache to free memory
        bitmapCache.clear()
        Timber.tag("Cb7ArchiveProvider").d("Bitmap cache cleared")
    }

    /**
     * Get image dimensions without decoding the full bitmap.
     * More efficient than loading the full bitmap for thumbnail calculation.
     */
    private fun getImageDimensions(pageIndex: Int): Pair<Int, Int>? {
        return try {
            if (pageIndex < 0 || pageIndex >= imageEntries.size) {
                return null
            }

            val entry = imageEntries[pageIndex]

            BitmapDecodingUtils.getImageDimensions {
                sevenZFile?.getInputStream(entry)
            }
        } catch (e: Exception) {
            Timber.tag("Cb7ArchiveProvider").e(e, "Error getting image dimensions")
            null
        }
    }

    /**
     * Close the 7zip archive and release resources.
     * Call this when done reading pages from the archive.
     */
    fun close() {
        try {
            // Clear bitmap cache - Phase 3: Cache integration
            bitmapCache.clear()

            sevenZFile?.close()
        } catch (e: IOException) {
            Timber.tag("Cb7ArchiveProvider").e(e, "Error closing 7z archive")
        }
    }

    /**
     * Get all page filenames (for debugging/logging)
     */
    fun getPageFilenames(): List<String> = imageEntries.map { it.name }
}
