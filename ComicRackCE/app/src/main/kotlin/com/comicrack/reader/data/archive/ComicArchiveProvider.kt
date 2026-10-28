package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import com.comicrack.reader.data.ComicPageProvider

/**
 * Abstract base class for comic archive providers.
 * Provides common functionality for archive-based comics (CBZ, RAR, etc.)
 */
abstract class ComicArchiveProvider : ComicPageProvider {
    /**
     * Series name for this comic
     */
    abstract val seriesName: String

    /**
     * Issue number for this comic
     */
    abstract val issueNumber: String

    /**
     * Cleanup resources (close archive files, etc.)
     * Must be called when done with the provider.
     */
    abstract fun close()
}
