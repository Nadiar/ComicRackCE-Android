package com.comicrack.reader.data

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import java.io.InputStream

/**
 * ComicPageProvider - Production-ready interface for page data.
 *
 * This interface abstracts the source of comic pages, allowing the viewer to work with:
 * - Real comic files (via archive readers: ZIP, RAR, etc.)
 * - Placeholder/mock data (for testing and Phase 2)
 * - Memory-cached bitmaps
 * - Remote URLs
 *
 * Architecture Pattern:
 * ComicViewerActivity â†’ ComicPageProvider â†’ Backend (Archive Reader, Cache, Network, etc.)
 *
 * Phase 2: Uses placeholder data
 * Phase 4: Will be implemented by ComicArchivePageProvider (integrates GallerySafeImageReader, etc.)
 */
interface ComicPageProvider {
    /**
     * Total number of pages in this comic/book.
     */
    suspend fun getPageCount(): Int

    /**
     * Get the metadata for a specific page (title, issue number, etc.).
     */
    suspend fun getPageMetadata(pageIndex: Int): PageMetadata

    /**
     * Load a page as a URI (file://, content://, http://, etc.).
     * Used with Coil's AsyncImage for efficient loading and caching.
     *
     * @param pageIndex 0-based page index
     * @return URI string, or null if page cannot be loaded
     */
    suspend fun getPageUri(pageIndex: Int): String?

    /**
     * Load a page as a Bitmap.
     * Use this for direct memory access (e.g., thumbnail generation).
     *
     * @param pageIndex 0-based page index
     * @return Bitmap, or null if page cannot be loaded
     */
    suspend fun getPageBitmap(pageIndex: Int): Bitmap?

    /**
     * Get raw InputStream for a page.
     *
     * This is the preferred method for Coil integration as it allows Coil
     * to handle decoding with optimal memory management and format support.
     *
     * @param pageIndex 0-based page index
     * @return InputStream to the raw image data, or null if not available
     */
    fun getPageInputStream(pageIndex: Int): InputStream?

    /**
     * Preload adjacent pages to improve perceived performance.
     *
     * @param pageIndex Current page index
     * @param distance Number of pages ahead/behind to preload (typically 1-2)
     */
    suspend fun preloadPages(
        pageIndex: Int,
        distance: Int = 1,
    )

    /**
     * Clear internal caches to free memory.
     */
    suspend fun clearCache()
}

/**
 * Metadata about a single page.
 */
data class PageMetadata(
    /** Comic series name (e.g., "Amazing Spider-Man") */
    val seriesName: String = "Unknown Series",
    /** Issue number (e.g., "1", "42.5", etc.) */
    val issueNumber: String = "0",
    /** Page index within this issue (1-based) */
    val pageNumber: Int = 1,
    /** Total pages in this issue */
    val totalPages: Int = 1,
    /** Optional: page title or scene description */
    val title: String? = null,
    /** Approximate page dimensions (width x height) for aspect ratio */
    val estimatedWidth: Int = 1080,
    val estimatedHeight: Int = 1600,
)

/**
 * PlaceholderPageProvider - Mock implementation for testing/Phase 2.
 *
 * Generates fake pages with placeholder content. Useful for:
 * - Testing UI without archive reader integration
 * - Demo/prototype builds
 * - Phase 2 development
 */
class PlaceholderPageProvider(
    private val seriesName: String = "Test Comic",
    private val issueNumber: String = "1",
    private val pageCount: Int = 10,
) : ComicPageProvider {
    override suspend fun getPageCount(): Int = pageCount

    override suspend fun getPageMetadata(pageIndex: Int): PageMetadata {
        require(pageIndex in 0 until pageCount) { "Page index out of bounds" }
        return PageMetadata(
            seriesName = seriesName,
            issueNumber = issueNumber,
            pageNumber = pageIndex + 1,
            totalPages = pageCount,
            title = "Page ${pageIndex + 1}",
        )
    }

    override suspend fun getPageUri(pageIndex: Int): String? {
        // Placeholder pages have no backing resource
        require(pageIndex in 0 until pageCount) { "Page index out of bounds" }
        return null
    }

    override suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        // Phase 2: Return null (use URI-based loading via Coil)
        // Phase 4: Return actual bitmap from archive reader
        return null
    }

    override fun getPageInputStream(pageIndex: Int): InputStream? {
        // Placeholder provider doesn't have raw streams
        return null
    }

    override suspend fun preloadPages(
        pageIndex: Int,
        distance: Int,
    ) {
        // Phase 2: No-op
        // Phase 4: Preload adjacent pages from archive reader
    }

    override suspend fun clearCache() {
        // Phase 2: No-op
        // Phase 4: Clear in-memory cache
    }
}
