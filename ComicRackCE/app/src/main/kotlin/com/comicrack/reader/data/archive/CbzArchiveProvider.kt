package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import com.comicrack.reader.cache.BitmapCache
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.data.PageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Real archive provider implementation for CBZ (ZIP) files.
 *
 * Handles actual CBZ archive extraction and page bitmap generation.
 * Replaces PlaceholderPageProvider for Phase 4 production usage.
 *
 * Implements AutoCloseable for proper resource management. Always call close()
 * when done reading pages to release file handles.
 *
 * Phase 3: Integrated with BitmapCache for performance improvements.
 * - Pages are cached after first decode
 * - Adjacent pages pre-fetched for smooth scrolling
 * - Cache evicts oldest pages when limit exceeded
 *
 * @param archivePath Path to the CBZ/ZIP file (file path or content:// URI converted to temp file)
 * @param seriesName Name of the comic series
 * @param issueNumber Issue number
 * @param bitmapCache Optional bitmap cache for page caching (default: creates new cache)
 */
class CbzArchiveProvider(
    private val context: android.content.Context? = null,
    private val archivePath: String,
    val seriesName: String,
    val issueNumber: String,
    private val bitmapCache: BitmapCache = BitmapCache(),
) : ComicPageProvider, AutoCloseable {
    private var zipFile: ZipFile? = null
    private var imageEntries: List<String> = emptyList()

    // Cache extracted pages in memory for content:// URIs (sequential access)
    private val pageCache = mutableMapOf<Int, ByteArray>()

    companion object {
        private const val TAG = "CbzArchiveProvider"
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heif", "heic")
    }

    init {
        try {
            Timber.tag(TAG).d("Opening archive: $archivePath")
            if (archivePath.startsWith("content://") && context != null) {
                loadImageEntriesFromStream()
            } else {
                zipFile = ZipFile(archivePath)
                loadImageEntries()
            }
            Timber.tag(TAG).d("Found ${imageEntries.size} image entries")

            // Initialize cache with this archive's path
            bitmapCache.setArchive(archivePath)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open archive: $archivePath")
            throw RuntimeException("Failed to open CBZ archive: $archivePath", e)
        }
    }

    /**
     * Load image entries from a streaming ZIP (SAF URI).
     */
    private fun loadImageEntriesFromStream() {
        val entries = mutableListOf<String>()
        try {
            val uri = android.net.Uri.parse(archivePath)
            val inputStream = context?.contentResolver?.openInputStream(uri) 
                ?: throw IOException("Cannot open URI: $archivePath")
            
            java.util.zip.ZipInputStream(inputStream).use { zis ->
                var entry: java.util.zip.ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.lowercase()
                        val extension = name.substringAfterLast(".")
                        if (!name.contains("__macosx") &&
                            !name.startsWith(".") &&
                            extension in IMAGE_EXTENSIONS
                        ) {
                            entries.add(entry.name)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error scanning ZIP stream")
        }
        imageEntries = entries.sortedBy { it.lowercase() }
    }

    /**
     * Load and filter image entries from the ZIP archive (local file).
     * Sorts them alphabetically to ensure consistent page ordering.
     */
    private fun loadImageEntries() {
        val entries = mutableListOf<String>()

        zipFile?.entries()?.asSequence()?.forEach { entry ->
            if (!entry.isDirectory) {
                val name = entry.name.lowercase()
                val extension = name.substringAfterLast(".")

                // Skip entries in __MACOSX or other system directories
                if (!name.contains("__macosx") &&
                    !name.startsWith(".") &&
                    extension in IMAGE_EXTENSIONS
                ) {
                    entries.add(entry.name)
                }
            }
        }

        imageEntries = entries.sortedBy { it.lowercase() }
    }

    override suspend fun getPageCount(): Int = imageEntries.size

    override suspend fun getPageMetadata(pageIndex: Int): PageMetadata {
        if (pageIndex < 0 || pageIndex >= imageEntries.size) {
            throw IndexOutOfBoundsException("Page index out of bounds: $pageIndex")
        }

        val entry = zipFile?.getEntry(imageEntries[pageIndex])
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
        // Return null - we'll use getPageBitmap for actual page content
        // Coil AsyncImage will still render properly with bitmap model
        return null
    }

    override suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        return try {
            if (pageIndex < 0 || pageIndex >= imageEntries.size) {
                return null
            }

            // Check bitmap cache first - Phase 3: Cache integration
            val cached = bitmapCache.get(pageIndex)
            if (cached != null) {
                Timber.tag(TAG).d("Cache HIT for page $pageIndex")
                return cached
            }

            // Cache miss - decode from archive
            Timber.tag(TAG).d("Cache MISS for page $pageIndex - decoding from archive")
            
            // Use optimized bitmap decoding to prevent OOM on large images
            val bitmap = BitmapDecodingUtils.decodeBitmapOptimized(
                openStream = { getPageInputStream(pageIndex) },
            )

            // Store in cache if successfully decoded - Phase 3: Cache integration
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
            if (pageIndex < 0 || pageIndex >= imageEntries.size) {
                return null
            }
            
            if (archivePath.startsWith("content://") && context != null) {
                return extractPageFromStream(pageIndex)
            }

            val entryName = imageEntries[pageIndex]
            val entry = zipFile?.getEntry(entryName)
            if (entry != null) zipFile?.getInputStream(entry) else null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get page input stream")
            null
        }
    }

    /**
     * Extract a page from a sequential ZIP stream and cache it.
     */
    private fun extractPageFromStream(pageIndex: Int): java.io.InputStream? {
        // Check in-memory page cache
        pageCache[pageIndex]?.let { return java.io.ByteArrayInputStream(it) }

        val targetName = imageEntries[pageIndex]
        try {
            val uri = android.net.Uri.parse(archivePath)
            val inputStream = context?.contentResolver?.openInputStream(uri) ?: return null
            
            java.util.zip.ZipInputStream(inputStream).use { zis ->
                var entry: java.util.zip.ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == targetName) {
                        val bytes = zis.readBytes()
                        pageCache[pageIndex] = bytes
                        return java.io.ByteArrayInputStream(bytes)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error extracting from ZIP stream: $targetName")
        }
        return null
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
                    Timber.tag(TAG).d("Preloaded page $i")
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to preload page $i")
                    // Continue preloading other pages
                }
            }
        }
    }

    override suspend fun clearCache() {
        // Phase 3: Implemented with BitmapCache integration
        // Clear bitmap cache to free memory
        bitmapCache.clear()
        Timber.tag(TAG).d("Bitmap cache cleared")
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

            val entryName = imageEntries[pageIndex]

            BitmapDecodingUtils.getImageDimensions {
                val entry = zipFile?.getEntry(entryName)
                if (entry != null) zipFile?.getInputStream(entry) else null
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Close the ZIP file and release resources.
     * Call this when done reading pages from the archive.
     * Implements AutoCloseable for use with try-with-resources.
     */
    override fun close() {
        try {
            // Clear caches - Phase 3: Cache integration
            bitmapCache.clear()
            pageCache.clear()

            zipFile?.close()
            zipFile = null
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Error closing ZIP file")
        }
    }

    /**
     * Get all page filenames (for debugging/logging)
     */
    fun getPageFilenames(): List<String> = imageEntries.toList()
}
