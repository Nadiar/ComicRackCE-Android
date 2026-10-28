package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import com.comicrack.reader.cache.BitmapCache
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.data.PageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.IOException

/**
 * Real archive provider implementation for CBT (tar) files.
 *
 * Handles tar archive extraction and page bitmap generation.
 * Supports uncompressed, gzip-compressed (.tar.gz), and bzip2-compressed (.tar.bz2) archives.
 * Uses Apache Commons Compress library for tar format support.
 *
 * Phase 3: Integrated with BitmapCache for performance improvements.
 *
 * @param archivePath Path to the CBT/tar file
 * @param seriesName Name of the comic series
 * @param issueNumber Issue number
 * @param bitmapCache Optional bitmap cache for page caching (default: creates new cache)
 */
class CbtArchiveProvider(
    private val archivePath: String,
    val seriesName: String,
    val issueNumber: String,
    private val bitmapCache: BitmapCache = BitmapCache(),
) : ComicPageProvider {
    private var imageEntries: List<TarArchiveEntry> = emptyList()

    init {
        try {
            loadImageEntries()

            // Initialize cache with this archive's path - Phase 3
            bitmapCache.setArchive(archivePath)
        } catch (e: IOException) {
            throw RuntimeException("Failed to open CBT archive: $archivePath", e)
        }
    }

    /**
     * Load and filter image entries from the tar archive.
     * Sorts them alphabetically to ensure consistent page ordering.
     *
     * Detects compression format automatically:
     * - .tar.gz or .tgz: gzip compression
     * - .tar.bz2 or .tbz: bzip2 compression
     * - .tar: uncompressed
     */
    private fun loadImageEntries() {
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heif", "heic")

        try {
            val fileInputStream = FileInputStream(archivePath)
            val bufferedInput = BufferedInputStream(fileInputStream)

            val tarInput = createTarInputStream(bufferedInput)

            tarInput.use { tar ->
                var entry = tar.nextTarEntry
                val entries = mutableListOf<TarArchiveEntry>()
                while (entry != null) {
                    if (isValidTarImageEntry(entry, imageExtensions)) {
                        entries.add(entry)
                    }
                    entry = tar.nextTarEntry
                }
                imageEntries = entries.sortedBy { it.name.lowercase() }
            }
        } catch (e: Exception) {
            Timber.tag("CbtArchiveProvider").e(e, "Error loading image entries")
            imageEntries = emptyList()
        }
    }

    /**
     * Create appropriate TAR input stream based on file extension.
     */
    private fun createTarInputStream(bufferedInput: BufferedInputStream): TarArchiveInputStream {
        return when {
            archivePath.endsWith(".tar.gz") || archivePath.endsWith(".tgz") -> {
                TarArchiveInputStream(GzipCompressorInputStream(bufferedInput))
            }
            archivePath.endsWith(".tar.bz2") || archivePath.endsWith(".tbz") -> {
                TarArchiveInputStream(BZip2CompressorInputStream(bufferedInput))
            }
            else -> {
                TarArchiveInputStream(bufferedInput)
            }
        }
    }

    /**
     * Check if a TAR entry is a valid image file. Reduces nesting in loadImageEntries.
     */
    private fun isValidTarImageEntry(entry: TarArchiveEntry, imageExtensions: Set<String>): Boolean {
        if (entry.isDirectory) return false
        
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
                Timber.tag("CbtArchiveProvider").d("Cache HIT for page $pageIndex")
                return cached
            }

            // Cache miss - decode from archive
            Timber.tag("CbtArchiveProvider").d("Cache MISS for page $pageIndex - decoding from archive")
            val targetEntry = imageEntries[pageIndex]

            // Use optimized bitmap decoding to prevent OOM on large images
            val bitmap = BitmapDecodingUtils.decodeBitmapOptimized(
                openStream = {
                    val inputStream = getTarInputStream() ?: return@decodeBitmapOptimized null

                    // Navigate to the correct entry
                    var currentEntry = inputStream.nextTarEntry
                    while (currentEntry != null && currentEntry.name != targetEntry.name) {
                        currentEntry = inputStream.nextTarEntry
                    }

                    if (currentEntry != null) {
                        inputStream
                    } else {
                        inputStream.close()
                        null
                    }
                },
            )

            // Store in cache if successfully decoded - Phase 3: Cache integration
            if (bitmap != null) {
                bitmapCache.put(pageIndex, bitmap)
                Timber.tag("CbtArchiveProvider").d("Cached page $pageIndex")
            }

            bitmap
        } catch (e: Exception) {
            Timber.tag("CbtArchiveProvider").e(e, "Failed to decode page bitmap")
            null
        }
    }

    override fun getPageInputStream(pageIndex: Int): java.io.InputStream? {
        return try {
            if (pageIndex < 0 || pageIndex >= imageEntries.size) {
                return null
            }
            val targetEntry = imageEntries[pageIndex]
            val inputStream = getTarInputStream() ?: return null

            // Navigate to the correct entry
            var currentEntry = inputStream.nextTarEntry
            while (currentEntry != null && currentEntry.name != targetEntry.name) {
                currentEntry = inputStream.nextTarEntry
            }

            if (currentEntry != null) {
                inputStream
            } else {
                inputStream.close()
                null
            }
        } catch (e: Exception) {
            Timber.tag("CbtArchiveProvider").e(e, "Failed to get page input stream")
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
                    Timber.tag("CbtArchiveProvider").d("Preloaded page $i")
                } catch (e: Exception) {
                    Timber.tag("CbtArchiveProvider").w(e, "Failed to preload page $i")
                    // Continue preloading other pages
                }
            }
        }
    }

    override suspend fun clearCache() {
        // Phase 3: Implemented with BitmapCache integration
        // Clear bitmap cache to free memory
        bitmapCache.clear()
        Timber.tag("CbtArchiveProvider").d("Bitmap cache cleared")
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

            val targetEntry = imageEntries[pageIndex]

            BitmapDecodingUtils.getImageDimensions {
                val inputStream = getTarInputStream() ?: return@getImageDimensions null

                // Navigate to the correct entry
                var currentEntry = inputStream.nextTarEntry
                while (currentEntry != null && currentEntry.name != targetEntry.name) {
                    currentEntry = inputStream.nextTarEntry
                }

                if (currentEntry != null) {
                    inputStream
                } else {
                    inputStream.close()
                    null
                }
            }
        } catch (e: Exception) {
            Timber.tag("CbtArchiveProvider").e(e, "Error getting image dimensions")
            null
        }
    }

    /**
     * Create a new TarArchiveInputStream with appropriate decompression.
     */
    private fun getTarInputStream(): TarArchiveInputStream? =
        try {
            val fileInputStream = FileInputStream(archivePath)
            val bufferedInput = BufferedInputStream(fileInputStream)

            when {
                archivePath.endsWith(".tar.gz") || archivePath.endsWith(".tgz") -> {
                    TarArchiveInputStream(GzipCompressorInputStream(bufferedInput))
                }
                archivePath.endsWith(".tar.bz2") || archivePath.endsWith(".tbz") -> {
                    TarArchiveInputStream(BZip2CompressorInputStream(bufferedInput))
                }
                else -> {
                    TarArchiveInputStream(bufferedInput)
                }
            }
        } catch (e: Exception) {
            Timber.tag("CbtArchiveProvider").e(e, "Error creating TarArchiveInputStream")
            null
        }

    /**
     * Close resources (no-op for tar as streams are auto-closed).
     * Call this when done reading pages from the archive.
     */
    fun close() {
        // Clear bitmap cache - Phase 3: Cache integration
        bitmapCache.clear()

        // Tar streams are closed after each read, no persistent resource
    }

    /**
     * Get all page filenames (for debugging/logging)
     */
    fun getPageFilenames(): List<String> = imageEntries.map { it.name }
}
