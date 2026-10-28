package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.net.Uri
import com.comicrack.reader.cache.TempFileManager
import com.comicrack.reader.data.ComicPageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Factory for creating comic archive providers.
 *
 * Phase 2: Supports SAF URIs by falling back to PlaceholderPageProvider
 * Phase 4: Will integrate GallerySafeImageReader for real archive extraction from SAF URIs
 *
 * Streaming & SAF note:
 * - Current fallback copies content:// URIs into a temp file; prefer implementing
 *   stream-capable archive readers that can operate over `InputStream` to avoid
 *   copying large archives into the app cache. This reduces IO and improves startup.
 * - Add tests that compare temp-file behavior vs streaming readers and measure
 *   memory and time overhead.
 *
 * Supported formats:
 * - CBZ/ZIP: Standard zip-based comic archives
 * - CBR/RAR: RAR archives (RAR4 and RAR5 formats)
 * - CB7: 7zip archives
 * - CBT: Tar archives (uncompressed, gzip, bzip2)
 */
object ArchiveProviderFactory {
    /**
     * Create an appropriate archive provider based on file format.
     *
     * @param filePath Path to the archive file (can be file path or SAF URI)
     * @param seriesName Name of the comic series
     * @param issueNumber Issue number
     * @return ComicPageProvider implementation for the detected format
     * @throws IllegalArgumentException if format is not supported
     */
    fun create(
        filePath: String,
        seriesName: String,
        issueNumber: String,
    ): ComicPageProvider {
        // Detect format from file path (works for both file:// and content:// URIs)
        val extension =
            when {
                filePath.contains(".cbp", ignoreCase = true) -> "cbp" // WiFi sync format (must check before .cbz)
                filePath.contains(".cbz", ignoreCase = true) -> "cbz"
                filePath.contains(".zip", ignoreCase = true) -> "zip"
                filePath.contains(".cbr", ignoreCase = true) -> "cbr"
                filePath.contains(".rar", ignoreCase = true) -> "rar"
                filePath.contains(".cb7", ignoreCase = true) -> "cb7"
                filePath.contains(".7z", ignoreCase = true) -> "7z"
                filePath.contains(".cbt", ignoreCase = true) -> "cbt"
                filePath.contains(".tar", ignoreCase = true) -> "tar"
                filePath.contains(".pdf", ignoreCase = true) -> "pdf"
                filePath.contains(".djvu", ignoreCase = true) -> "djvu"
                filePath.contains(".djv", ignoreCase = true) -> "djvu"
                else -> {
                    val lastDot = filePath.lastIndexOf('.')
                    if (lastDot >= 0) filePath.substring(lastDot + 1).lowercase() else ""
                }
            }

        return when (extension) {
            "cbz", "zip" -> CbzArchiveProvider(null, filePath, seriesName, issueNumber)
            "cbp" -> CbpArchiveProvider(null, filePath, seriesName, issueNumber) // WiFi sync format (streaming ZIP)
            "cbr", "rar" -> CbrArchiveProvider(filePath, seriesName, issueNumber)
            "cb7", "7z" -> Cb7ArchiveProvider(filePath, seriesName, issueNumber)
            "cbt", "tar" -> CbtArchiveProvider(filePath, seriesName, issueNumber)
            "pdf" -> PdfArchiveProvider(null, filePath, seriesName, issueNumber)
            // DJVU requires external libraries with incompatible licenses (AGPL)
            // Throw specific exception so UI can offer to open in external app
            "djvu", "djv" -> throw UnsupportedFormatException.forDjvu(filePath)
            else -> throw IllegalArgumentException("Unsupported archive format: $extension for path: $filePath")
        }
    }

    /**
     * Get supported file extensions.
     */
    fun getSupportedExtensions(): List<String> =
        listOf(
            "cbz",
            "zip",
            "cbp", // ZIP-based archives (cbp = WiFi sync format)
            "cbr",
            "rar", // RAR archives (RAR4 and RAR5)
            "cb7",
            "7z", // 7zip archives
            "cbt",
            "tar",
            "tar.gz",
            "tgz",
            "tar.bz2",
            "tbz", // Tar archives
            "pdf", // PDF documents
        )

    /**
     * Get file extensions recognized but not natively supported.
     * These will prompt user to open in external app.
     */
    fun getExternalOnlyExtensions(): List<String> =
        listOf(
            "djvu",
            "djv", // DJVU - requires AGPL libraries
        )

    /**
     * Create an appropriate archive provider from a URI.
     * Handles both file:// and content:// URIs directly.
     */
    suspend fun create(
        context: Context,
        uri: Uri,
        seriesName: String,
        issueNumber: String,
        _tempFileManager: TempFileManager,
    ): ComicPageProvider {
        // Direct pass-through of URI string. Individual providers will handle
        // content:// vs file:// schemes appropriately with the provided context.
        return create(context, uri.toString(), seriesName, issueNumber)
    }

    /**
     * Create an appropriate archive provider based on file format.
     *
     * @param context Android context for content resolution (required for SAF)
     * @param filePath Path to the archive file (can be file path or SAF URI)
     * @param seriesName Name of the comic series
     * @param issueNumber Issue number
     * @return ComicPageProvider implementation for the detected format
     * @throws IllegalArgumentException if format is not supported
     */
    fun create(
        context: Context,
        filePath: String,
        seriesName: String,
        issueNumber: String,
    ): ComicPageProvider {
        // Detect format from file path (works for both file:// and content:// URIs)
        val extension =
            when {
                filePath.contains(".cbp", ignoreCase = true) -> "cbp" // WiFi sync format (must check before .cbz)
                filePath.contains(".cbz", ignoreCase = true) -> "cbz"
                filePath.contains(".zip", ignoreCase = true) -> "zip"
                filePath.contains(".cbr", ignoreCase = true) -> "cbr"
                filePath.contains(".rar", ignoreCase = true) -> "rar"
                filePath.contains(".cb7", ignoreCase = true) -> "cb7"
                filePath.contains(".7z", ignoreCase = true) -> "7z"
                filePath.contains(".cbt", ignoreCase = true) -> "cbt"
                filePath.contains(".tar", ignoreCase = true) -> "tar"
                filePath.contains(".pdf", ignoreCase = true) -> "pdf"
                filePath.contains(".djvu", ignoreCase = true) -> "djvu"
                filePath.contains(".djv", ignoreCase = true) -> "djvu"
                else -> {
                    val lastDot = filePath.lastIndexOf('.')
                    if (lastDot >= 0) filePath.substring(lastDot + 1).lowercase() else ""
                }
            }

        return when (extension) {
            "cbz", "zip" -> CbzArchiveProvider(context, filePath, seriesName, issueNumber)
            "cbp" -> CbpArchiveProvider(context, filePath, seriesName, issueNumber) // WiFi sync format (streaming ZIP)
            "cbr", "rar" -> CbrArchiveProvider(filePath, seriesName, issueNumber)
            "cb7", "7z" -> Cb7ArchiveProvider(filePath, seriesName, issueNumber)
            "cbt", "tar" -> CbtArchiveProvider(filePath, seriesName, issueNumber)
            "pdf" -> PdfArchiveProvider(context, filePath, seriesName, issueNumber)
            // DJVU requires external libraries with incompatible licenses (AGPL)
            // Throw specific exception so UI can offer to open in external app
            "djvu", "djv" -> throw UnsupportedFormatException.forDjvu(filePath)
            else -> throw IllegalArgumentException("Unsupported archive format: $extension for path: $filePath")
        }
    }

    /**
     * Check if a file extension is supported.
     */
    fun isSupported(extension: String): Boolean {
        val lowerExt = extension.lowercase()
        return getSupportedExtensions().contains(lowerExt) ||
            lowerExt.endsWith(".tar.gz") ||
            lowerExt.endsWith(".tar.bz2")
    }
}
