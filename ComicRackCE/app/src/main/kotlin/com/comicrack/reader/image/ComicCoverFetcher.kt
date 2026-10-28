package com.comicrack.reader.image

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Coil Fetcher for comic book cover images from CBZ/ZIP archives.
 *
 * ## Stream Management Strategy
 * Uses Kotlin's `use` extension (try-with-resources) to ensure proper cleanup:
 * - Local files: Opens FileInputStream -> BufferedInputStream -> ZipInputStream
 * - Content URIs: ContentResolver.openInputStream() -> BufferedInputStream -> ZipInputStream
 * - All streams automatically closed in correct order (outer to inner) via `use` blocks
 *
 * ## Memory Behavior
 * - **Small covers (<10MB):** Buffered entirely in memory using Okio Buffer (zero-copy to Coil)
 * - **Large covers (≥10MB):** Skipped with warning to prevent OOM (could extend with temp file fallback)
 * - Size check performed BEFORE reading entire entry to avoid memory pressure
 *
 * ## Error Handling Strategy
 * - **Fatal ZIP errors** (corrupt header, invalid central directory): Return null immediately
 * - **Entry-level errors** (single corrupted image): Skip to next entry, continue extraction
 * - **Oversized entries:** Skip with warning, continue to next entry
 * - Top-level catch distinguishes IOException (file access) from ZipException (corrupt archive)
 *
 * ## Format Support
 * Only ZIP-based archives (CBZ) are supported because ZipInputStream provides
 * sequential extraction without native dependencies. RAR/7z (CBR/CB7) support requires
 * separate extractor libraries and may introduce licensing constraints—track in issue
 * tracker before adding.
 */
class ComicCoverFetcher(
    private val uri: Uri,
    private val context: Context,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        Timber.d("fetch() called for uri: $uri")
        return withContext(Dispatchers.IO) {
            try {
                val path = uri.path
                val scheme = uri.scheme
                Timber.d("scheme=$scheme, path=$path")

                // Heuristic: try to detect archive extension from the URI string. Note that
                // content:// URIs may not include file names and query parameters can confuse
                // naive parsing. Prefer inspecting MIME type or DocumentFile where available.
                val uriString = uri.toString()
                val ext = uriString.substringAfterLast(".").lowercase().take(4)
                Timber.d("detected extension=$ext")

                // Only handle ZIP-based archives here. Support for CBR/CB7/CBT (RAR/7z)
                // requires an alternate extractor and additional testing for platform
                // compatibility and licensing; track as a separate task before implementing.
                if (ext != "cbz" && ext != "zip" && ext != "cbp") {
                    Timber.d("Non-CBZ format ($ext), skipping: $uri")
                    return@withContext null
                }

                // Open stream and ensure cleanup with use() - Kotlin's try-with-resources
                val inputStream: InputStream? =
                    when {
                        scheme == "content" -> {
                            // Content URIs provide streaming-only access (no seek). For large
                            // archives prefer openFileDescriptor + FileInputStream to allow
                            // improved buffering/FD-based operations if needed.
                            Timber.d("Opening content:// stream")
                            context.contentResolver.openInputStream(uri)
                        }
                        scheme == "file" && path != null -> {
                            val file = File(path)
                            Timber.d("Opening file:// stream, exists=${file.exists()}, size=${file.length()}")
                            file.inputStream()
                        }
                        path != null -> {
                            val file = File(path)
                            Timber.d("Opening raw path stream, exists=${file.exists()}, size=${file.length()}")
                            file.inputStream()
                        }
                        else -> null
                    }

                if (inputStream == null) {
                    Timber.w("Could not open stream for $uri")
                    return@withContext null
                }

                Timber.d("Successfully opened input stream")

                // use() ensures inputStream is closed even if extractFirstImageFromZip throws
                inputStream.use { stream ->
                    extractFirstImageFromZip(stream)
                }
            } catch (e: java.io.IOException) {
                Timber.e(e, "I/O error accessing archive: $uri")
                null
            } catch (e: java.util.zip.ZipException) {
                Timber.e(e, "Corrupt ZIP archive: $uri")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching cover from $uri")
                null
            }
        }
    }

    /**
     * Extract the first image from a ZIP/CBZ stream using Okio Buffer.
     *
     * ## Extraction Strategy
     * Scans ZIP entries sequentially and returns the first valid image found.
     * Typical comic archives place the cover as the first image entry.
     *
     * ## Memory Management
     * - Size check enforced: Skips entries >10MB to prevent OOM
     * - Small images: Buffered entirely in memory using Okio Buffer (zero-copy to Coil)
     * - Large images: Skipped with warning (future: could use temp file fallback)
     *
     * ## Error Recovery
     * - **Fatal errors** (corrupt ZIP header): Return null immediately
     * - **Entry-level errors** (single corrupted image): Log warning, continue to next entry
     * - **Oversized entries**: Log warning, skip to next entry
     * - Distinguishes between:
     *   - Permanent failure (corrupt archive structure) → return null
     *   - Transient failure (single bad entry) → continue scan
     *
     * @param inputStream A sequential ZIP input stream (non-seekable). The stream is managed
     *                    by the caller using `use()` and will be automatically closed.
     * @return A [FetchResult] containing the first valid image <10MB, or `null` if:
     *         - No valid images found
     *         - All images exceed size limit
     *         - Unrecoverable ZIP structure corruption detected
     */
    private fun extractFirstImageFromZip(inputStream: InputStream): FetchResult? {
        // Wrap in buffered ZipInputStream - use() ensures proper cleanup of entire stream chain
        return ZipInputStream(BufferedInputStream(inputStream, 16384)).use { zipStream ->
            var entry: ZipEntry?

            // Initial entry read - fatal if this fails (corrupt ZIP header)
            try {
                entry = zipStream.nextEntry
            } catch (e: java.util.zip.ZipException) {
                Timber.tag("ComicCoverFetcher").e(e, "Fatal: Corrupt ZIP header, cannot read entries")
                return null
            } catch (e: java.io.IOException) {
                Timber.tag("ComicCoverFetcher").e(e, "Fatal: I/O error reading ZIP header")
                return null
            }

            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name

                    if (isImageFile(name) && !isSystemFile(name)) {
                        // Check size BEFORE reading to prevent OOM
                        val entrySize = entry.size
                        if (entrySize > MAX_IMAGE_SIZE_BYTES) {
                            val sizeMB = entrySize / 1_048_576
                            val limitMB = MAX_IMAGE_SIZE_BYTES / 1_048_576
                            Timber.tag("ComicCoverFetcher").w(
                                "Skipping oversized image '$name' (${sizeMB}MB > ${limitMB}MB limit)",
                            )
                            // Continue to next entry - may find smaller cover variant
                        } else {
                            try {
                                // Found valid-sized image - buffer in memory for Coil
                                val buffer = Buffer()
                                val byteArray = ByteArray(8192)
                                var bytesRead: Int
                                var totalRead = 0L

                                while (zipStream.read(byteArray).also { bytesRead = it } != -1) {
                                    buffer.write(byteArray, 0, bytesRead)
                                    totalRead += bytesRead

                                    // Safety check: ZIP entry.size can be -1 (unknown) or incorrect
                                    if (totalRead > MAX_IMAGE_SIZE_BYTES) {
                                        Timber.tag("ComicCoverFetcher").w(
                                            "Aborting extraction: '$name' exceeded size limit " +
                                                "during read (claimed ${entrySize}B, read ${totalRead}B)",
                                        )
                                        break
                                    }
                                }

                                // Only return if we successfully read within limits
                                if (totalRead <= MAX_IMAGE_SIZE_BYTES) {
                                    Timber.d("Extracted cover '$name' (${buffer.size} bytes)")
                                    return SourceFetchResult(
                                        source =
                                            ImageSource(
                                                source = buffer,
                                                fileSystem = FileSystem.SYSTEM,
                                                metadata = null,
                                            ),
                                        mimeType = null, // Let Coil detect from bytes
                                        dataSource = DataSource.DISK,
                                    )
                                }
                            } catch (e: java.util.zip.ZipException) {
                                Timber
                                    .tag(
                                        "ComicCoverFetcher",
                                    ).w(e, "Corrupt entry '$name' in ZIP, continuing to next entry")
                                // Entry-level corruption - try next entry
                            } catch (e: java.io.IOException) {
                                Timber
                                    .tag(
                                        "ComicCoverFetcher",
                                    ).w(e, "I/O error reading entry '$name', continuing to next entry")
                                // Entry-level I/O error - try next entry
                            }
                        }
                    }
                }

                // Advance to next entry - if this fails, ZIP structure is corrupt
                try {
                    entry = zipStream.nextEntry
                } catch (e: java.util.zip.ZipException) {
                    Timber.tag("ComicCoverFetcher").e(e, "Fatal: Corrupt ZIP structure, cannot read next entry")
                    return null
                } catch (e: java.io.IOException) {
                    Timber.tag("ComicCoverFetcher").e(e, "Fatal: I/O error reading next ZIP entry")
                    return null
                }
            }

            Timber.w("No valid images found in archive (all entries skipped or no images present)")
            null
        }
    }

    /**
     * Check if entry name is a valid image file.
     *
     * @param name The entry/file name to inspect (may include path segments).
     * @return `true` if the file extension is a known image type (jpg, png, webp, etc.),
     *         `false` otherwise.
     */
    private fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast(".").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    /**
     * Check if entry is a system/hidden file to skip.
     *
     * @param name The entry/file name to inspect
     * @return `true` if the entry is likely a system or hidden file (e.g., MacOSX metadata,
     *         thumbnail DBs, dotfiles), `false` otherwise.
     */
    private fun isSystemFile(name: String): Boolean {
        val lowerName = name.lowercase()
        return lowerName.contains("__macosx") ||
            lowerName.contains(".ds_store") ||
            lowerName.contains("thumbs.db") ||
            lowerName.startsWith(".") ||
            lowerName.contains("/.")
    }

    /**
     * Factory for Uri-based requests.
     *
     * Handles two URI schemes:
     * - "archive://" - Custom scheme for comic archives (bypasses Coil's FileMapper)
     * - "content://" - SAF URIs
     *
     * The archive:// scheme format: archive:///storage/emulated/0/Comics/book.cbz
     * or archive://content://com.android.providers.media/...
     */
    class Factory(
        private val context: Context,
    ) : Fetcher.Factory<ComicCoverRequest> {
        init {
            // Single, tagged initialization log. Previously there were duplicate messages;
            // consolidating keeps logs easier to filter.
            Timber.tag("ComicCoverFetcher").d("Factory initialized")
        }

        /**
         * Create a new [ComicCoverFetcher] for the given [ComicCoverRequest].
         *
         * @param data The [ComicCoverRequest] containing the archive path (URI or file path).
         * @param options Coil [Options] supplied to the fetcher (unused, present for API compatibility).
         * @param imageLoader The Coil [ImageLoader] instance that will use this fetcher.
         * @return A new [ComicCoverFetcher] configured for the requested archive.
         */
        override fun create(
            data: ComicCoverRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            Timber.tag("ComicCoverFetcher").d("Factory.create() archivePath=${data.archivePath}")
            Timber.d("Factory.create() called with archivePath: ${data.archivePath}")

            val uri = Uri.parse(data.archivePath)
            return ComicCoverFetcher(uri, context)
        }
    }

    companion object {
        /**
         * Maximum image size to buffer in memory (10MB).
         * Larger images are skipped to prevent OOM. Future enhancement could use
         * temporary file fallback for oversized covers.
         */
        private const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024L // 10MB

        private val IMAGE_EXTENSIONS =
            setOf(
                "jpg",
                "jpeg",
                "png",
                "gif",
                "webp",
                "bmp",
                "avif",
                "heif",
                "heic",
            )

        // Heuristic list used only for quick path decisions; not all listed formats are
        // handled by this fetcher (e.g., .pdf/.djvu require different extraction).
        private val COMIC_EXTENSIONS =
            setOf(
                ".cbz",
                ".zip",
                ".cbp",
                ".cbr",
                ".rar",
                ".cb7",
                ".7z",
                ".cbt",
                ".tar",
                ".pdf",
                ".djvu",
            )

        /**
         * Simple heuristic to detect comic archive file names by common extensions.
         *
         * @param path File path or URI string to inspect
         * @return `true` if the path ends with a known comic archive extension
         */
        fun isComicArchive(path: String): Boolean {
            val lowerPath = path.lowercase()
            return COMIC_EXTENSIONS.any { lowerPath.endsWith(it) }
        }
    }
}

/**
 * Request data for loading a comic book cover from an archive.
 *
 * This wrapper class ensures Coil routes the request to our ComicCoverFetcher
 * instead of trying to decode the archive file directly as an image.
 *
 * @param archivePath Content URI or file path to the comic archive
 */
data class ComicCoverRequest(
    val archivePath: String,
)
