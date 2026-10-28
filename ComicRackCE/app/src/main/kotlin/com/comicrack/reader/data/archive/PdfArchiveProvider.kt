package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.comicrack.reader.cache.BitmapCache
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.data.PageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Archive provider for PDF documents.
 *
 * Uses Android's PdfRenderer API (available since API 21) to render PDF pages
 * as bitmaps. Supports viewing PDF comics and manga.
 *
 * Phase 3: Integrated with BitmapCache for performance improvements.
 * - Pages are cached after first render
 * - Adjacent pages pre-fetched for smooth scrolling
 * - Cache evicts oldest pages when limit exceeded
 *
 * Limitations:
 * - PdfRenderer requires a SeekableFileDescriptor, so content:// URIs must be
 *   copied to a temp file first (handled by ArchiveProviderFactory)
 * - Password-protected PDFs not supported by Android's PdfRenderer
 * - Renders at screen DPI by default, configurable via pageWidth/pageHeight
 */
class PdfArchiveProvider(
    private val context: android.content.Context? = null,
    private val filePath: String,
    private val seriesName: String,
    private val issueNumber: String,
    private val maxPageWidth: Int = 2048, // Max width for rendered pages
    private val maxPageHeight: Int = 3072, // Max height for rendered pages
    private val bitmapCache: BitmapCache = BitmapCache(),
) : ComicPageProvider {
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pageCount: Int = 0

    init {
        openPdf()
    }

    private fun openPdf() {
        try {
            fileDescriptor = if (filePath.startsWith("content://") && context != null) {
                context.contentResolver.openFileDescriptor(android.net.Uri.parse(filePath), "r")
            } else {
                val file = File(filePath)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            fileDescriptor?.let { fd ->
                pdfRenderer = PdfRenderer(fd)
                pageCount = pdfRenderer?.pageCount ?: 0
                Timber.tag("PdfArchiveProvider").d("Opened PDF: $filePath with $pageCount pages")

                // Initialize cache with this archive's path - Phase 3
                bitmapCache.setArchive(filePath)
            }
        } catch (e: Exception) {
            Timber.tag("PdfArchiveProvider").e(e, "Failed to open PDF: $filePath")
            pageCount = 0
        }
    }

    override suspend fun getPageCount(): Int = pageCount

    override suspend fun getPageMetadata(pageIndex: Int): PageMetadata {
        val page = pdfRenderer?.openPage(pageIndex)
        val width = page?.width ?: 1024
        val height = page?.height ?: 1536
        page?.close()

        return PageMetadata(
            title = if (issueNumber.isNotEmpty()) "$seriesName #$issueNumber" else seriesName,
            issueNumber = issueNumber,
            pageNumber = pageIndex + 1,
            totalPages = pageCount,
            estimatedWidth = width,
            estimatedHeight = height,
        )
    }

    override suspend fun getPageUri(pageIndex: Int): String? = null

    override suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        // Check cache first - Phase 3: Cache integration
        val cached = bitmapCache.get(pageIndex)
        if (cached != null) {
            Timber.tag("PdfArchiveProvider").d("Cache HIT for page $pageIndex")
            return cached
        }

        // Cache miss - render from PDF
        Timber.tag("PdfArchiveProvider").d("Cache MISS for page $pageIndex - rendering from PDF")
        val bitmap = renderPage(pageIndex)

        // Store in cache if successfully rendered - Phase 3: Cache integration
        if (bitmap != null) {
            bitmapCache.put(pageIndex, bitmap)
            Timber.tag("PdfArchiveProvider").d("Cached page $pageIndex")
        }

        return bitmap
    }

    override fun getPageInputStream(pageIndex: Int): InputStream? {
        // PDF pages need to be rendered to bitmap first, then encoded to PNG/JPEG
        val bitmap = renderPage(pageIndex) ?: return null

        return try {
            val outputStream = ByteArrayOutputStream()
            // Use PNG for quality (PDF pages may have text/vectors)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            ByteArrayInputStream(outputStream.toByteArray())
        } catch (e: Exception) {
            Timber.tag("PdfArchiveProvider").e(e, "Failed to encode page $pageIndex")
            null
        }
    }

    /**
     * Render a PDF page to a bitmap.
     *
     * Scales the page to fit within maxPageWidth x maxPageHeight while
     * maintaining aspect ratio.
     */
    private fun renderPage(pageIndex: Int): Bitmap? {
        if (pageIndex < 0 || pageIndex >= pageCount) {
            return null
        }

        val renderer = pdfRenderer ?: return null

        return try {
            val page = renderer.openPage(pageIndex)

            // Calculate render size maintaining aspect ratio
            val pdfWidth = page.width
            val pdfHeight = page.height

            val scale =
                minOf(
                    maxPageWidth.toFloat() / pdfWidth,
                    maxPageHeight.toFloat() / pdfHeight,
                    3.0f, // Cap at 3x scale to prevent memory issues
                )

            val renderWidth = (pdfWidth * scale).toInt().coerceAtLeast(1)
            val renderHeight = (pdfHeight * scale).toInt().coerceAtLeast(1)

            // Create bitmap and render
            val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)

            // Fill with white background (PDFs may have transparent backgrounds)
            bitmap.eraseColor(android.graphics.Color.WHITE)

            page.render(
                bitmap,
                null, // Render entire page
                null, // No transformation matrix (use default)
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
            )

            page.close()
            bitmap
        } catch (e: Exception) {
            Timber.tag("PdfArchiveProvider").e(e, "Failed to render page $pageIndex")
            null
        }
    }

    override suspend fun preloadPages(
        pageIndex: Int,
        distance: Int,
    ) {
        // Phase 3: Implemented with BitmapCache integration
        // Preload adjacent pages in background for smooth scrolling
        // PDF rendering involves vector-to-raster conversion, so caching helps
        withContext(Dispatchers.IO) {
            val startIndex = (pageIndex - distance).coerceAtLeast(0)
            val endIndex = (pageIndex + distance).coerceAtMost(pageCount - 1)

            for (i in startIndex..endIndex) {
                // Skip current page and already cached pages
                if (i == pageIndex || bitmapCache.get(i) != null) {
                    continue
                }

                // Preload page into cache
                try {
                    getPageBitmap(i) // This will cache the page
                    Timber.tag("PdfArchiveProvider").d("Preloaded page $i")
                } catch (e: Exception) {
                    Timber.tag("PdfArchiveProvider").w(e, "Failed to preload page $i")
                    // Continue preloading other pages
                }
            }
        }
    }

    override suspend fun clearCache() {
        // Phase 3: Implemented with BitmapCache integration
        // Clear bitmap cache to free memory
        bitmapCache.clear()
        Timber.tag("PdfArchiveProvider").d("Bitmap cache cleared")
    }

    /**
     * Close the PDF and release resources.
     *
     * Note: If the underlying PDF was copied from a content:// URI to a temp file
     * (see [ArchiveProviderFactory.resolveContentUri]) callers should also ensure
     * the temp file is unregistered/removed via `TempFileManager` when appropriate.
     */
    fun close() {
        try {
            // Clear bitmap cache - Phase 3: Cache integration
            bitmapCache.clear()

            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            Timber.tag("PdfArchiveProvider").e(e, "Error closing PDF")
        }
    }
}
