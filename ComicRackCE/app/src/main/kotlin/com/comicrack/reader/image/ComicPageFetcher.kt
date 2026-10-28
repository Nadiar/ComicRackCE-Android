package com.comicrack.reader.image

/**
 * Image fetcher for comic page content.
 *
 * Streams image bytes from archives (local files or SAF content URIs) to the image
 * decoder (Coil) to leverage Coil's memory and decoding strategies. Prefer tests
 * that assert streaming behavior for both local and SAF-backed archives.
 */

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.comicrack.reader.data.archive.ArchiveProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import okio.source
import timber.log.Timber
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Coil Fetcher for comic book page images.
 *
 * Unlike [ComicCoverFetcher] (which returns a Bitmap), this fetcher streams raw bytes
 * to Coil so Coil can handle decoding with optimal memory management and format support
 * (JPEG, PNG, WebP, AVIF, HEIF, etc.).
 *
 * Architecture:
 * ```
 * Archive (CBZ/CBR/etc) â†’ ComicPageFetcher â†’ Raw Bytes â†’ Coil Decoder â†’ Display
 * ```
 *
 * Why this approach:
 * 1. Coil handles hardware bitmaps, color spaces, and memory limits.
 * 2. Coil manages memory and disk caches and may store encoded or decoded image data depending on configuration.
 * 3. Consumers (for example, Telephoto's ZoomableAsyncImage) can sub-sample large images efficiently.
 * 4. Single-pass: no double-decoding (dimensions â†’ bitmap).
 */
class ComicPageFetcher(
    private val data: ComicPageRequest,
    private val context: Context,
) : Fetcher {
    /**
     * Open the requested page and provide a Coil-compatible [SourceFetchResult].
     * Runs on [Dispatchers.IO]. Returns null on failure.
     */
    override suspend fun fetch(): FetchResult? {
        return withContext(Dispatchers.IO) {
            try {
                val stream = openPageStream(data) ?: return@withContext null

                // Convert InputStream to Okio BufferedSource for Coil
                val buffer = Buffer()
                stream.source().buffer().use { source ->
                    buffer.writeAll(source)
                }

                SourceFetchResult(
                    source =
                        ImageSource(
                            source = buffer,
                            fileSystem = okio.FileSystem.SYSTEM,
                            metadata = null,
                        ),
                    mimeType = guessMimeType(data.entryName),
                    dataSource = DataSource.DISK,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch comic page: ${data.archivePath}, page ${data.pageIndex}")
                null
            }
        }
    }

    /**
     * Open an InputStream to the requested page within the archive.
     */
    private fun openPageStream(request: ComicPageRequest): InputStream? {
        val path = request.archivePath
        val ext = path.substringAfterLast(".").lowercase()

        return when (ext) {
            // cbp = WiFi sync format
            "cbz", "zip", "cbp" -> openZipPageStream(path, request.pageIndex, request.entryName)
            "cbr", "rar" -> openRarPageStream(path, request.pageIndex)
            "cbt", "tar" -> openTarPageStream(path, request.pageIndex)
            "cb7", "7z" -> open7zPageStream(path, request.pageIndex)
            else -> null
        }
    }

    /**
     * Open page stream from ZIP/CBZ archive.
     * Supports both local files and content:// URIs.
     * If entryName is provided, use it directly. Otherwise, find by index.
     *
     * For content:// URIs: Streams only the requested page, not the entire file
     * For local files: Uses ZipFile for optimal performance
     */
    private fun openZipPageStream(
        archivePath: String,
        pageIndex: Int,
        entryName: String?,
    ): InputStream? =
        try {
            // Check for SAF URI (content://). Streaming via ContentResolver is required for SAF access.
            if (archivePath.startsWith("content://")) {
                openZipPageStreamFromContentUri(archivePath, pageIndex, entryName)
            } else {
                // Local file: use ZipFile (faster for local access)
                openZipPageStreamFromLocalFile(archivePath, pageIndex, entryName)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open ZIP page stream: $archivePath")
            null
        }

    /**
     * Open page stream from local ZIP/CBZ file using ZipFile.
     * Optimized for local filesystem access.
     */
    private fun openZipPageStreamFromLocalFile(
        filePath: String,
        pageIndex: Int,
        entryName: String?,
    ): InputStream? =
        try {
            val zipFile = ZipFile(filePath)

            val targetEntry =
                if (entryName != null) {
                    zipFile.getEntry(entryName)
                } else {
                    // Find entry by index
                    val imageEntries =
                        zipFile
                            .entries()
                            .asSequence()
                            .filter { !it.isDirectory && isImageEntry(it.name) }
                            .sortedBy { it.name.lowercase() }
                            .toList()

                    imageEntries.getOrNull(pageIndex)
                }

            targetEntry?.let { zipFile.getInputStream(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open local ZIP page stream")
            null
        }

    /**
     * Open page stream from content:// URI (SAF access) using ZipInputStream.
     * Streams only the requested page, avoiding copying entire file to disk.
     *
     * Process:
     * 1. Open InputStream from content:// URI via ContentResolver
     * 2. Wrap in ZipInputStream for streaming access
     * 3. Scan entries to find target page (by name or index)
     * 4. Return InputStream positioned at the target entry
     */
    private fun openZipPageStreamFromContentUri(
        contentUri: String,
        pageIndex: Int,
        entryName: String?,
    ): InputStream? {
        return try {
            val uri = android.net.Uri.parse(contentUri)
            val inputStream =
                context.contentResolver.openInputStream(uri)
                    ?: error("Cannot open URI: $contentUri")

            val zipInputStream = ZipInputStream(inputStream)

            // Scan entries sequentially because ZipInputStream does not provide random access.
            // Stop when the matching image entry is found to avoid copying or extracting the entire archive.
            var currentIndex = 0
            var entry = zipInputStream.nextEntry

            while (entry != null) {
                // Check if this entry matches our criteria
                if (!entry.isDirectory && isImageEntry(entry.name)) {
                    // Match by name if provided, otherwise by index
                    if ((entryName != null && entry.name == entryName) ||
                        (entryName == null && currentIndex == pageIndex)
                    ) {
                        // Found it! Return the stream positioned at this entry
                        return zipInputStream
                    }
                    currentIndex++
                }
                entry = zipInputStream.nextEntry
            }

            // No matching image entry found; close stream and return null.
            Timber.w("Requested page not found in archive: index=$pageIndex, entryName=$entryName")
            zipInputStream.close()
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to open content:// ZIP page stream")
            null
        }
    }

    /**
     * Open page stream from RAR/CBR archive.
     *
     * Note: junrar library requires File objects and doesn't support streaming from arbitrary InputStreams.
     * For SAF URIs, we must copy to temporary file. For local files, we use the Archive provider.
     */
    private fun openRarPageStream(
        archivePath: String,
        pageIndex: Int,
    ): InputStream? =
        try {
            // Check if this is a SAF URI - if so, we need to copy to temp file first
            if (archivePath.startsWith("content://")) {
                // For SAF URIs, resolve to temp file and then use provider
                val tempPath = resolveContentUriToTempFile(archivePath)
                val provider = ArchiveProviderFactory.create(tempPath, "", "")
                provider.getPageInputStream(pageIndex)
            } else {
                // Local file: use provider directly
                val provider = ArchiveProviderFactory.create(archivePath, "", "")
                provider.getPageInputStream(pageIndex)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open RAR page stream")
            null
        }

    /**
     * Open page stream from TAR/CBT archive.
     * Supports streaming directly from SAF URIs without copying entire file.
     */
    private fun openTarPageStream(
        archivePath: String,
        pageIndex: Int,
    ): InputStream? =
        try {
            if (archivePath.startsWith("content://")) {
                openTarPageStreamFromContentUri(archivePath, pageIndex)
            } else {
                // Local file: use provider for consistency
                val provider = ArchiveProviderFactory.create(archivePath, "", "")
                provider.getPageInputStream(pageIndex)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open TAR page stream")
            null
        }

    /**
     * Open page stream from 7Z/CB7 archive.
     *
     * Note: Commons Compress SevenZFile requires File objects and doesn't support streaming.
     * For SAF URIs, we must copy to temporary file. For local files, we use the Archive provider.
     */
    private fun open7zPageStream(
        archivePath: String,
        pageIndex: Int,
    ): InputStream? =
        try {
            // Check if this is a SAF URI - if so, we need to copy to temp file first
            if (archivePath.startsWith("content://")) {
                // For SAF URIs, resolve to temp file and then use provider
                val tempPath = resolveContentUriToTempFile(archivePath)
                val provider = ArchiveProviderFactory.create(tempPath, "", "")
                provider.getPageInputStream(pageIndex)
            } else {
                // Local file: use provider directly
                val provider = ArchiveProviderFactory.create(archivePath, "", "")
                provider.getPageInputStream(pageIndex)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open 7Z page stream")
            null
        }

    /**
     * Resolve a content:// URI to a temporary file path.
     * Used when libraries don't support streaming from arbitrary InputStreams.
     * This method handles the copy operation for RAR and 7Z formats.
     */
    private fun resolveContentUriToTempFile(contentUri: String): String {
        val uri = android.net.Uri.parse(contentUri)
        val inputStream =
            context.contentResolver.openInputStream(uri)
                ?: error("Cannot open URI: $contentUri")

        // Extract file extension from URI
        val uriString = uri.toString()
        val extension =
            when {
                uriString.contains(".cbr", ignoreCase = true) -> "cbr"
                uriString.contains(".rar", ignoreCase = true) -> "rar"
                uriString.contains(".cb7", ignoreCase = true) -> "cb7"
                uriString.contains(".7z", ignoreCase = true) -> "7z"
                else -> "cbr" // Default
            }

        // Create temp file in the app cache directory (some libraries require a File object).
        val fileName = "temp_comic_${uriString.hashCode()}.$extension"
        val tempFile = java.io.File(context.cacheDir, fileName)

        // Copy into the cache file if it is not already present to avoid repeated copies for the same URI.
        if (!tempFile.exists()) {
            tempFile.outputStream().use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            Timber.d("Created temp file for RAR/7Z: ${tempFile.absolutePath}")
        }

        return tempFile.absolutePath
    }

    /**
     * Open page stream from TAR/CBT archive via SAF URI using streaming.
     * Streams entries sequentially without copying the entire file.
     *
     * Supports uncompressed, gzip (.tar.gz), and bzip2 (.tar.bz2) formats.
     */
    private fun openTarPageStreamFromContentUri(
        contentUri: String,
        pageIndex: Int,
    ): InputStream? {
        return try {
            val uri = android.net.Uri.parse(contentUri)
            val inputStream =
                context.contentResolver.openInputStream(uri)
                    ?: error("Cannot open URI: $contentUri")

            // Detect compression from URI
            val uriString = uri.toString()
            val compressedInput =
                when {
                    uriString.endsWith(
                        ".tar.gz",
                        ignoreCase = true,
                    ) ||
                        uriString.endsWith(".tgz", ignoreCase = true) -> {
                        org.apache.commons.compress.compressors.gzip
                            .GzipCompressorInputStream(inputStream)
                    }
                    uriString.endsWith(
                        ".tar.bz2",
                        ignoreCase = true,
                    ) ||
                        uriString.endsWith(".tbz", ignoreCase = true) -> {
                        org.apache.commons.compress.compressors.bzip2
                            .BZip2CompressorInputStream(inputStream)
                    }
                    else -> {
                        inputStream
                    }
                }

            val tarInput =
                org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                    java.io.BufferedInputStream(compressedInput),
                )

            // Scan entries sequentially because tar streams are sequential.
            // Stop when found to avoid copying the whole archive.
            var currentIndex = 0
            var entry = tarInput.nextTarEntry

            while (entry != null) {
                // Check if this entry matches our criteria
                if (!entry.isDirectory && isImageEntry(entry.name)) {
                    // Match by index
                    if (currentIndex == pageIndex) {
                        // Found it! Return the stream positioned at this entry
                        return tarInput
                    }
                    currentIndex++
                }
                entry = tarInput.nextTarEntry
            }

            // No matching image entry found; close stream and return null.
            Timber.w("Requested page not found in TAR archive: index=$pageIndex")
            tarInput.close()
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to open TAR page stream from SAF URI")
            null
        }
    }

    /**
     * Check if a file entry is an image based on extension.
     * Supports: JPEG, PNG, WebP, GIF, BMP, AVIF, HEIF
     */
    private fun isImageEntry(name: String): Boolean {
        val lowerName = name.lowercase()
        // Skip system directories and hidden files
        if (lowerName.contains("__macosx") || lowerName.startsWith(".")) {
            return false
        }
        return lowerName.endsWith(".jpg") ||
            lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") ||
            lowerName.endsWith(".webp") ||
            lowerName.endsWith(".gif") ||
            lowerName.endsWith(".bmp") ||
            lowerName.endsWith(".avif") ||
            lowerName.endsWith(".heif") ||
            lowerName.endsWith(".heic")
    }

    /**
     * Guess MIME type from entry name for Coil's decoder selection.
     */
    private fun guessMimeType(entryName: String?): String? {
        if (entryName == null) return null
        val ext = entryName.substringAfterLast(".").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "avif" -> "image/avif"
            "heif", "heic" -> "image/heif"
            else -> null
        }
    }

    /**
     * Factory for creating ComicPageFetcher instances.
     */
    class Factory(
        private val context: Context,
    ) : Fetcher.Factory<ComicPageRequest> {
        /**
         * Create a [ComicPageFetcher] for the supplied [data].
         *
         * @param data Request describing which page to fetch.
         * @param options Coil request options (unused by this fetcher).
         * @param imageLoader Coil [ImageLoader] (unused by this fetcher).
         */
        override fun create(
            data: ComicPageRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ComicPageFetcher(data, context)
    }

    companion object {
        /**
         * Scan a CBZ/ZIP archive to count total pages without extracting.
         * Uses streaming to avoid loading entire archive into memory.
         *
         * @param context Android context for content resolver
         * @param archivePath Path to archive (content:// or file:// URI)
         * @return Total number of image pages in archive
         */
        suspend fun getPageCount(
            context: Context,
            archivePath: String,
        ): Int =
            withContext(Dispatchers.IO) {
                try {
                    when {
                        archivePath.contains(".cbz", ignoreCase = true) ||
                            archivePath.contains(".zip", ignoreCase = true) -> {
                            countZipPages(context, archivePath)
                        }
                        else -> {
                            Timber.w("getPageCount not implemented for: $archivePath")
                            0
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to count pages: $archivePath")
                    0
                }
            }

        /**
         * Count pages in a ZIP/CBZ archive by streaming through entries.
         */
        private fun countZipPages(
            context: Context,
            archivePath: String,
        ): Int {
            val uri = android.net.Uri.parse(archivePath)

            return if (uri.scheme == "content") {
                // Stream from content:// URI
                val inputStream =
                    context.contentResolver.openInputStream(uri)
                        ?: error("Cannot open URI: $archivePath")

                val zipInputStream = ZipInputStream(inputStream)
                var count = 0
                var entry = zipInputStream.nextEntry

                while (entry != null) {
                    if (!entry.isDirectory && isImageEntryCompanion(entry.name)) {
                        count++
                    }
                    entry = zipInputStream.nextEntry
                }

                zipInputStream.close()
                count
            } else {
                // Local file: use ZipFile for faster random access
                val zipFile = ZipFile(archivePath)
                val count =
                    zipFile
                        .entries()
                        .asSequence()
                        .count { !it.isDirectory && isImageEntryCompanion(it.name) }
                zipFile.close()
                count
            }
        }

        /**
         * Check if a file entry is an image (companion version).
         */
        private fun isImageEntryCompanion(name: String): Boolean {
            val lowerName = name.lowercase()
            if (lowerName.contains("__macosx") || lowerName.startsWith(".")) {
                return false
            }
            return lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") ||
                lowerName.endsWith(".webp") ||
                lowerName.endsWith(".gif") ||
                lowerName.endsWith(".bmp") ||
                lowerName.endsWith(".avif") ||
                lowerName.endsWith(".heif") ||
                lowerName.endsWith(".heic")
        }
    }
}

/**
 * Request data for loading a specific page from a comic archive.
 *
 * @param archivePath Path to the archive file (CBZ, CBR, etc.)
 * @param pageIndex Zero-based page index
 * @param entryName Optional: specific entry name within the archive (for direct access)
 */
data class ComicPageRequest(
    val archivePath: String,
    val pageIndex: Int,
    val entryName: String? = null,
) {
    /**
     * Unique cache key for Coil.
     */
    fun cacheKey(): String = "$archivePath#page$pageIndex"
}
