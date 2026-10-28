package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import com.comicrack.reader.cache.BitmapCache
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.data.PageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Archive provider for CBP (ComicRack Portable) WiFi sync format.
 *
 * CBP files are ZIP archives created in streaming mode (no Central Directory).
 * Standard ZipFile class requires the Central Directory, so we use ZipInputStream
 * which reads from local file headers sequentially.
 *
 * This format is used by ComicRack for WiFi sync to Android devices.
 *
 * Phase 3: Integrated with BitmapCache for performance improvements.
 * Uses two-level caching: pageCache for raw bytes (needed for streaming ZIP),
 * bitmapCache for decoded bitmaps (performance optimization).
 *
 * @param archivePath Path to the CBP file
 * @param seriesName Name of the comic series
 * @param issueNumber Issue number
 * @param bitmapCache Optional bitmap cache for page caching (default: creates new cache)
 */
class CbpArchiveProvider(
    private val context: android.content.Context? = null,
    private val archivePath: String,
    val seriesName: String,
    val issueNumber: String,
    private val bitmapCache: BitmapCache = BitmapCache(),
) : ComicPageProvider {
    companion object {
        private const val TAG = "CbpArchiveProvider"
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heif", "heic")
    }

    // Cache extracted pages in memory since we can't seek in streaming ZIP
    private val pageCache = mutableMapOf<Int, ByteArray>()
    private var pageEntries: List<PageEntry> = emptyList()
    private var initialized = false

    private data class PageEntry(
        val name: String,
        val index: Int,
    )

    init {
        try {
            Timber.tag(TAG).d("Opening CBP archive (streaming format): $archivePath")
            scanArchiveEntries()
            Timber.tag(TAG).d("Found ${pageEntries.size} image entries in CBP")

            // Initialize bitmap cache with this archive's path - Phase 3
            bitmapCache.setArchive(archivePath)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to scan CBP archive: $archivePath")
            // Don't throw - allow graceful degradation with 0 pages
        }
    }

    /**
     * Open an InputStream for the archive path, handling both file:// and content:// URIs.
     */
    private fun openArchiveStream(): java.io.InputStream? {
        return try {
            if (archivePath.startsWith("content://") && context != null) {
                context.contentResolver.openInputStream(android.net.Uri.parse(archivePath))
            } else {
                java.io.FileInputStream(archivePath)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open archive stream for: $archivePath")
            null
        }
    }

    /**
     * Scan the archive using ZipInputStream to find all image entries.
     * ZipInputStream works by reading local file headers sequentially,
     * which is necessary for CBP files that lack a Central Directory.
     */
    private fun scanArchiveEntries() {
        val entries = mutableListOf<PageEntry>()
        var index = 0

        try {
            val stream = openArchiveStream() ?: return
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry

                while (entry != null) {
                    if (isValidPageEntry(entry)) {
                        entries.add(PageEntry(entry.name, index))
                        index++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Error scanning CBP entries - archive may be truncated")
            // Continue with whatever entries we found
        }

        // Sort by name for consistent page ordering
        pageEntries = entries.sortedBy { it.name.lowercase() }
        initialized = true

        Timber.tag(TAG).d("CBP scan complete: ${pageEntries.size} pages found")
    }

    /**
     * Check if a ZIP entry is a valid image page. Reduces nesting in scanArchiveEntries.
     */
    private fun isValidPageEntry(entry: ZipEntry): Boolean {
        if (entry.isDirectory) return false
        
        val name = entry.name.lowercase()
        val extension = name.substringAfterLast(".")
        
        // Skip system directories
        if (name.contains("__macosx") || name.startsWith(".")) return false
        
        // Check image extension
        return extension in IMAGE_EXTENSIONS
    }

    /**
     * Extract a specific page from the archive.
     * Since ZipInputStream is sequential, we must scan from the beginning
     * each time unless the page is already cached.
     */
    private fun extractPage(pageIndex: Int): ByteArray? {
        // Check cache first
        pageCache[pageIndex]?.let { return it }

        if (pageIndex < 0 || pageIndex >= pageEntries.size) {
            return null
        }

        val targetEntry = pageEntries[pageIndex]

        try {
            val stream = openArchiveStream() ?: return null
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry

                while (entry != null) {
                    if (entry.name == targetEntry.name) {
                        return readAndCachePageBytes(zis, pageIndex)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Error extracting page $pageIndex from CBP")
        }

        return null
    }

    /**
     * Read page bytes from stream and cache them. Extracted to reduce nesting in extractPage.
     */
    private fun readAndCachePageBytes(zis: ZipInputStream, pageIndex: Int): ByteArray {
        val pageBytes = zis.readBytes()
        pageCache[pageIndex] = pageBytes
        return pageBytes
    }

    override suspend fun getPageCount(): Int = pageEntries.size

    override suspend fun getPageMetadata(pageIndex: Int): PageMetadata {
        if (pageIndex < 0 || pageIndex >= pageEntries.size) {
            throw IndexOutOfBoundsException("Page index out of bounds: $pageIndex")
        }

        val dimensions = getImageDimensions(pageIndex)

        return PageMetadata(
            seriesName = seriesName,
            issueNumber = issueNumber,
            pageNumber = pageIndex + 1,
            totalPages = pageEntries.size,
            estimatedWidth = dimensions?.first ?: 1024,
            estimatedHeight = dimensions?.second ?: 1536,
        )
    }

    override suspend fun getPageUri(pageIndex: Int): String? {
        return null // Use getPageBitmap for content
    }

    override suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        return try {
            // Check bitmap cache first - Phase 3: Cache integration
            val cached = bitmapCache.get(pageIndex)
            if (cached != null) {
                Timber.tag(TAG).d("Cache HIT for page $pageIndex")
                return cached
            }

            // Cache miss - extract and decode
            Timber.tag(TAG).d("Cache MISS for page $pageIndex - extracting from archive")
            val pageBytes = extractPage(pageIndex) ?: return null

            val bitmap = BitmapDecodingUtils.decodeBitmapOptimized(
                openStream = { ByteArrayInputStream(pageBytes) },
            )

            // Store in bitmap cache if successfully decoded - Phase 3: Cache integration
            if (bitmap != null) {
                bitmapCache.put(pageIndex, bitmap)
                Timber.tag(TAG).d("Cached page $pageIndex")
            }

            bitmap
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to decode page bitmap")
            null
        }
    }

    override fun getPageInputStream(pageIndex: Int): java.io.InputStream? {
        return try {
            val pageBytes = extractPage(pageIndex) ?: return null
            ByteArrayInputStream(pageBytes)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get page input stream")
            null
        }
    }

    override suspend fun preloadPages(
        pageIndex: Int,
        distance: Int,
    ) {
        // Phase 3: Implemented with BitmapCache integration
        // Preload adjacent pages in background for smooth scrolling
        // Note: CBP uses two-level caching (raw bytes + decoded bitmaps)
        withContext(Dispatchers.IO) {
            val startIndex = (pageIndex - distance).coerceAtLeast(0)
            val endIndex = (pageIndex + distance).coerceAtMost(pageEntries.size - 1)

            for (i in startIndex..endIndex) {
                // Skip current page and already cached pages
                if (i == pageIndex || bitmapCache.get(i) != null) {
                    continue
                }

                // Preload page into cache
                try {
                    getPageBitmap(i) // This will cache the bitmap
                    Timber.tag(TAG).d("Preloaded page $i")
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to preload page $i")
                    // Continue preloading other pages
                }
            }
        }
    }

    override suspend fun clearCache() {
        // Phase 3: Clear both bitmap cache and raw byte cache
        bitmapCache.clear()
        pageCache.clear()
        Timber.tag(TAG).d("Bitmap cache and page cache cleared")
    }

    /**
     * Get image dimensions without fully decoding the bitmap.
     */
    private fun getImageDimensions(pageIndex: Int): Pair<Int, Int>? {
        return try {
            val pageBytes = extractPage(pageIndex) ?: return null

            BitmapDecodingUtils.getImageDimensions {
                ByteArrayInputStream(pageBytes)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Close and release resources.
     */
    fun close() {
        // Clear both caches - Phase 3: Cache integration
        bitmapCache.clear()
        pageCache.clear()
    }

    /**
     * Get all page filenames (for debugging).
     */
    fun getPageFilenames(): List<String> = pageEntries.map { it.name }
}
