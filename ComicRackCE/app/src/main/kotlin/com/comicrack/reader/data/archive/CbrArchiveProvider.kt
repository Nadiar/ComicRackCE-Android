package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import com.comicrack.reader.cache.BitmapCache
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.data.PageMetadata
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Real archive provider implementation for CBR (RAR) files.
 *
 * Handles actual CBR archive extraction and page bitmap generation.
 * Uses the junrar library (v7.5.5) supporting both RAR4 and RAR5 formats.
 *
 * Phase 3: Integrated with BitmapCache for performance improvements.
 * - Pages are cached after first decode
 * - Adjacent pages pre-fetched for smooth scrolling
 * - Cache evicts oldest pages when limit exceeded
 *
 * @param archivePath Path to the CBR/RAR file
 * @param seriesName Name of the comic series
 * @param issueNumber Issue number
 * @param bitmapCache Optional bitmap cache for page caching (default: creates new cache)
 */
class CbrArchiveProvider(
    private val archivePath: String,
    val seriesName: String,
    val issueNumber: String,
    private val bitmapCache: BitmapCache = BitmapCache(),
) : ComicPageProvider {
    /**
     * CBR provider uses `junrar` to read RAR archives. Important notes:
     * - Call `close()` when finished to release native file handles.
     * - Decoding and archive access should occur on background threads.
     * - `getPageUri` returns null for local file paths; implementers using SAF
     *   should use `ArchiveProviderFactory.create(context, uri, ...)` which copies
     *   content URIs to temp files.
     */
    private var archive: Archive? = null
    private var imageEntries: List<FileHeader> = emptyList()

    init {
        try {
            archive = Archive(File(archivePath))
            loadImageEntries()

            // Initialize cache with this archive's path - Phase 3
            bitmapCache.setArchive(archivePath)
        } catch (e: IOException) {
            throw RuntimeException("Failed to open CBR archive: $archivePath", e)
        }
    }

    /**
     * Load and filter image entries from the RAR archive.
     * Sorts them alphabetically to ensure consistent page ordering.
     * Uses functional filtering to reduce nesting depth.
     */
    private fun loadImageEntries() {
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heif", "heic")

        try {
            imageEntries = archive?.fileHeaders
                ?.filterNot { it.isDirectory }
                ?.filter { isValidRarImageEntry(it, imageExtensions) }
                ?.sortedBy { it.fileNameString.lowercase() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.tag("CbrArchiveProvider").e(e, "Error loading image entries")
            imageEntries = emptyList()
        }
    }

    /**
     * Check if a RAR entry is a valid image file. Reduces nesting in loadImageEntries.
     */
    private fun isValidRarImageEntry(header: FileHeader, imageExtensions: Set<String>): Boolean {
        val name = header.fileNameString.lowercase()
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
        // For SAF URIs, this would need to be implemented differently
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
                Timber.tag("CbrArchiveProvider").d("Cache HIT for page $pageIndex")
                return cached
            }

            // Cache miss - decode from archive
            Timber.tag("CbrArchiveProvider").d("Cache MISS for page $pageIndex - decoding from archive")
            val header = imageEntries[pageIndex]

            // Use optimized bitmap decoding to prevent OOM on large images
            val bitmap = BitmapDecodingUtils.decodeBitmapOptimized(
                openStream = {
                    archive?.getInputStream(header)
                },
            )

            // Store in cache if successfully decoded - Phase 3: Cache integration
            if (bitmap != null) {
                bitmapCache.put(pageIndex, bitmap)
                Timber.tag("CbrArchiveProvider").d("Cached page $pageIndex")
            }

            bitmap
        } catch (e: Exception) {
            Timber.tag("CbrArchiveProvider").e(e, "Failed to decode page bitmap")
            null
        }
    }

    override fun getPageInputStream(pageIndex: Int): java.io.InputStream? {
        return try {
            if (pageIndex < 0 || pageIndex >= imageEntries.size) {
                return null
            }
            val header = imageEntries[pageIndex]
            archive?.getInputStream(header)
        } catch (e: Exception) {
            Timber.tag("CbrArchiveProvider").e(e, "Failed to get page input stream")
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
                    Timber.tag("CbrArchiveProvider").d("Preloaded page $i")
                } catch (e: Exception) {
                    Timber.tag("CbrArchiveProvider").w(e, "Failed to preload page $i")
                    // Continue preloading other pages
                }
            }
        }
    }

    override suspend fun clearCache() {
        // Phase 3: Implemented with BitmapCache integration
        // Clear bitmap cache to free memory
        bitmapCache.clear()
        Timber.tag("CbrArchiveProvider").d("Bitmap cache cleared")
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

            val header = imageEntries[pageIndex]

            BitmapDecodingUtils.getImageDimensions {
                archive?.getInputStream(header)
            }
        } catch (e: Exception) {
            Timber.tag("CbrArchiveProvider").e(e, "Error getting image dimensions")
            null
        }
    }

    /**
     * Close the RAR archive and release resources.
     * Call this when done reading pages from the archive.
     */
    fun close() {
        try {
            // Clear bitmap cache - Phase 3: Cache integration
            bitmapCache.clear()

            archive?.close()
        } catch (e: IOException) {
            Timber.tag("CbrArchiveProvider").e(e, "Error closing RAR archive")
        }
    }

    /**
     * Get all page filenames (for debugging/logging)
     */
    fun getPageFilenames(): List<String> = imageEntries.map { it.fileNameString }
}
