package com.comicrack.reader

/**
 * Archive readers and helpers for comic archive (CBZ/ZIP) access.
 *
 * Implementation notes:
 * - Primary support: CBZ (ZIP) archives containing image files.
 * - RAR/CBR archives are not supported by this implementation (ZipFile cannot read RAR).
 * - Public suspend methods perform IO on Dispatchers.IO; callers should not invoke them on the main thread.
 * - Prefer streaming access for SAF `content://` URIs to avoid copying large archives into app storage.
 */

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Holds parsed metadata from a ComicInfo.xml file contained in an archive.
 *
 * @property series Series name if present
 * @property number Issue number (may contain non-numeric values)
 * @property volume Volume identifier
 * @property year Year of publication
 * @property month Month of publication
 * @property day Day of publication
 * @property title Issue title
 * @property summary Issue summary or synopsis
 * @property writer Writer(s)
 * @property penciller Penciller(s)
 * @property inker Inker(s)
 * @property colorist Colorist(s)
 * @property letterer Letterer(s)
 * @property coverArtist Cover artist
 * @property editor Editor
 * @property publisher Publisher
 * @property pageCount Page count as reported in XML
 * @property characters Characters field
 * @property web Web/online reference
 * @property notes Additional notes
 */
data class ComicMetadata(
    val series: String? = null,
    val number: String? = null,
    val volume: String? = null,
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val title: String? = null,
    val summary: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val inker: String? = null,
    val colorist: String? = null,
    val letterer: String? = null,
    val coverArtist: String? = null,
    val editor: String? = null,
    val publisher: String? = null,
    val pageCount: Int? = null,
    val characters: String? = null,
    val web: String? = null,
    val notes: String? = null,
)

/**
 * Abstraction over a comic archive reader.
 *
 * Implementations should perform IO in suspend functions and avoid blocking the calling thread.
 */
interface ArchiveReader {
    /**
     * Initialize and scan the archive.
     *
     * Implementations should open and scan the archive for image pages. This method performs IO
     * and therefore is a suspend function; callers should not call it on the main thread.
     *
     * @return true if the archive was opened and at least one image page was detected
     */
    suspend fun initArchive(): Boolean

    /**
     * Returns the number of pages detected in the archive.
     *
     * @return Number of pages (zero if not initialized or empty)
     */
    fun getPageCount(): Int

    /**
     * Extract the page at [index] as a Bitmap, or return null on failure.
     *
     * Implementations should perform decoding on an IO dispatcher and avoid blocking the main thread.
     *
     * @param index Zero-based page index to extract
     * @return Decoded [Bitmap] for the requested page, or null on failure
     */
    suspend fun extractPage(index: Int): Bitmap?

    /**
     * Parse and return ComicInfo metadata if present.
     *
     * @return [ComicMetadata] populated from ComicInfo.xml if present, otherwise an empty [ComicMetadata]
     */
    suspend fun getMetadata(): ComicMetadata

    /**
     * Close the underlying archive and release resources.
     *
     * Implementations should release any open file handles and clear cached state.
     */
    fun close()
}

/**
 * GallerySafeImageReaderImpl - Implementation focused on CBZ (ZIP) archives.
 *
 * Supports:
 * - CBZ files (ZIP archives with images)
 * - RAR/CBR archives are not supported by this implementation (ZipFile cannot read RAR)
 * - Image formats: JPEG, PNG, WebP, GIF
 *
 * Notes:
 * - Suspended methods perform IO on Dispatchers.IO; callers should not invoke them on the main thread.
 *
 * Features:
 * - Scans archive for sorted list of images (proper page order)
 * - Extracts images on demand as bitmaps
 * - Handles errors gracefully with logging
 *
 * @constructor Creates a reader for an archive located at [archivePath]
 * @param context Android [Context] used for resource access or caching if required
 * @param archivePath Filesystem path to the archive (CBZ/ZIP)
 */
class GallerySafeImageReaderImpl(
    private val context: Context,
    private val archivePath: String,
) : ArchiveReader {
    private val tag = "GallerySafeImageReader"
    private var zipFile: ZipFile? = null
    private var imageEntries: List<String> = emptyList()

    override suspend fun initArchive(): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                if (archivePath.isEmpty()) {
                    Timber.tag(tag).w("No archive path provided")
                    return@withContext false
                }

                val file = File(archivePath)
                if (!file.exists()) {
                    Timber.tag(tag).e("Archive not found: $archivePath")
                    return@withContext false
                }

                Timber.tag(tag).d("Opening archive: $archivePath (${file.length()} bytes)")
                zipFile = ZipFile(file)

                // Scan for image files (sort by name for proper page order)
                val zip = zipFile ?: return@withContext false
                imageEntries =
                    zip
                        .entries()
                        .asSequence()
                        .filter { !it.isDirectory }
                        .map { it.name }
                        .filter { isImageFile(it) }
                        .sorted()
                        .toList()

                Timber
                    .tag(
                        tag,
                    ).d("Found ${imageEntries.size} images in archive: ${imageEntries.take(3).joinToString(", ")}")
                imageEntries.isNotEmpty()
            } catch (e: Exception) {
                Timber.tag(tag).e(e, "Failed to initialize archive: ${e.message}")
                false
            }
        }

    override fun getPageCount(): Int = imageEntries.size

    override suspend fun extractPage(index: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                if (index < 0 || index >= imageEntries.size) {
                    Timber.tag(tag).w("Page index out of bounds: $index (max: ${imageEntries.size})")
                    return@withContext null
                }

                val zip = zipFile
                if (zip == null) {
                    Timber.tag(tag).e("Archive not initialized")
                    return@withContext null
                }

                val entryName = imageEntries[index]
                Timber.tag(tag).d("Extracting page $index: $entryName")

                val entry =
                    zip.getEntry(entryName) ?: run {
                        Timber.tag(tag).e("Entry not found: $entryName")
                        return@withContext null
                    }

                val bitmap =
                    zip.getInputStream(entry).use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }

                if (bitmap == null) {
                    Timber.tag(tag).w("Failed to decode bitmap for: $entryName")
                } else {
                    Timber.tag(tag).d("Decoded bitmap: ${bitmap.width}x${bitmap.height}")
                }

                bitmap
            } catch (e: Exception) {
                Timber.tag(tag).e(e, "Error extracting page $index: ${e.message}")
                null
            }
        }

    override fun close() {
        try {
            zipFile?.close()
            zipFile = null
            Timber.tag(tag).d("Archive closed")
        } catch (e: Exception) {
            Timber.tag(tag).e(e, "Error closing archive: ${e.message}")
        }
    }

    override suspend fun getMetadata(): ComicMetadata =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val zip = zipFile
                if (zip == null) {
                    Timber.tag(tag).w("Archive not initialized for metadata extraction")
                    return@withContext ComicMetadata()
                }

                val entry =
                    zip.getEntry("ComicInfo.xml") ?: run {
                        Timber.tag(tag).d("No ComicInfo.xml found, returning empty metadata")
                        return@withContext ComicMetadata()
                    }

                Timber.tag(tag).d("Found ComicInfo.xml, parsing metadata")
                val doc =
                    zip.getInputStream(entry).use { inputStream ->
                        val dbFactory = DocumentBuilderFactory.newInstance()
                        val dBuilder = dbFactory.newDocumentBuilder()
                        dBuilder.parse(inputStream)
                    }

                doc.documentElement.normalize()

                // Extract metadata fields
                val series = doc.getElementsByTagName("Series").item(0)?.textContent
                val number = doc.getElementsByTagName("Number").item(0)?.textContent
                val volume = doc.getElementsByTagName("Volume").item(0)?.textContent
                val year =
                    doc
                        .getElementsByTagName("Year")
                        .item(0)
                        ?.textContent
                        ?.toIntOrNull()
                val month =
                    doc
                        .getElementsByTagName("Month")
                        .item(0)
                        ?.textContent
                        ?.toIntOrNull()
                val day =
                    doc
                        .getElementsByTagName("Day")
                        .item(0)
                        ?.textContent
                        ?.toIntOrNull()
                val title = doc.getElementsByTagName("Title").item(0)?.textContent
                val summary = doc.getElementsByTagName("Summary").item(0)?.textContent
                val writer = doc.getElementsByTagName("Writer").item(0)?.textContent
                val penciller = doc.getElementsByTagName("Penciller").item(0)?.textContent
                val inker = doc.getElementsByTagName("Inker").item(0)?.textContent
                val colorist = doc.getElementsByTagName("Colorist").item(0)?.textContent
                val letterer = doc.getElementsByTagName("Letterer").item(0)?.textContent
                val coverArtist = doc.getElementsByTagName("CoverArtist").item(0)?.textContent
                val editor = doc.getElementsByTagName("Editor").item(0)?.textContent
                val publisher = doc.getElementsByTagName("Publisher").item(0)?.textContent
                val pageCount =
                    doc
                        .getElementsByTagName("PageCount")
                        .item(0)
                        ?.textContent
                        ?.toIntOrNull()
                val characters = doc.getElementsByTagName("Characters").item(0)?.textContent
                val web = doc.getElementsByTagName("Web").item(0)?.textContent
                val notes = doc.getElementsByTagName("Notes").item(0)?.textContent

                Timber.tag(tag).d("Extracted metadata: Series=$series, Number=$number, Title=$title")

                ComicMetadata(
                    series = series,
                    number = number,
                    volume = volume,
                    year = year,
                    month = month,
                    day = day,
                    title = title,
                    summary = summary,
                    writer = writer,
                    penciller = penciller,
                    inker = inker,
                    colorist = colorist,
                    letterer = letterer,
                    coverArtist = coverArtist,
                    editor = editor,
                    publisher = publisher,
                    pageCount = pageCount,
                    characters = characters,
                    web = web,
                    notes = notes,
                )
            } catch (e: Exception) {
                Timber.tag(tag).e(e, "Error extracting metadata: ${e.message}")
                ComicMetadata()
            }
        }

    /** Returns true when [fileName] ends with an image extension supported by this reader. */
    private fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "webp", "gif")
    }
}

/**
 * PageLoaderHelper - Bridges archive reading to Compose/Coil
 *
 * Manages:
 * - Archive reader lifecycle
 * - Page extraction from ZIP/CBZ files
 * - Bitmap caching to disk for Coil
 * - Resource cleanup
 *
 * Architecture:
 * 1. ComicPagerActivity creates PageLoaderHelper with comic path
 * 2. ComicPagerScreen calls initArchive() to scan and get page count
 * 3. On page navigation: LaunchedEffect â†’ extractAndCachePageBitmap()
 * 4. Bitmap extracted from ZIP and saved to cache directory
 * 5. Uri returned to Coil's AsyncImage (loads cached file)
 * 6. On unmount: cleanup() closes archive and removes temp files
 *
 * @constructor Creates a loader for a comic archive
 * @param context Android [Context] used for cache and file operations
 * @param comicUri Path to the comic archive (file path or content URI)
 */
class PageLoaderHelper(
    private val context: Context,
    private val comicUri: String,
) {
    private val tag = "PageLoaderHelper"
    private var reader: ArchiveReader? = null
    private var pageCount = 0
    private val cacheDir = File(context.cacheDir, "comic_pages")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Timber.tag(tag).d("Created cache directory: ${cacheDir.absolutePath}")
        }
    }

    /**
     * Initialize archive reader and scan for pages
     * @return true if successful
     */
    suspend fun initArchive(): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                Timber.tag(tag).d("Initializing archive: $comicUri")

                reader = GallerySafeImageReaderImpl(context, comicUri)
                val r =
                    reader ?: run {
                        Timber.tag(tag).w("Reader not initialized")
                        return@withContext false
                    }
                val success = r.initArchive()

                if (success) {
                    pageCount = r.getPageCount()
                    Timber.tag(tag).d("Archive initialized with $pageCount pages")
                } else {
                    Timber.tag(tag).w("Archive initialization failed")
                    reader = null
                }

                success
            } catch (e: Exception) {
                Timber.tag(tag).e(e, "Failed to initialize archive: ${e.message}")
                false
            }
        }

    /**
     * Extract page from archive and cache as JPEG
     * @param pageIndex Zero-based page number
     * @return File URI for cached bitmap, or null on error
     */
    suspend fun extractAndCachePageBitmap(pageIndex: Int): Uri? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                if (reader == null) {
                    Timber.tag(tag).e("Reader not initialized")
                    return@withContext null
                }

                if (pageIndex < 0 || pageIndex >= pageCount) {
                    Timber.tag(tag).e("Page index $pageIndex out of bounds")
                    return@withContext null
                }

                Timber.tag(tag).d("Extracting page $pageIndex/$pageCount...")

                val r =
                    reader ?: run {
                        Timber.tag(tag).e("Reader not initialized")
                        return@withContext null
                    }
                val bitmap =
                    r.extractPage(pageIndex) ?: run {
                        Timber.tag(tag).w("Failed to extract bitmap from archive")
                        return@withContext null
                    }

                // Save to cache
                val cacheFile = File(cacheDir, "page_$pageIndex.jpg")
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                }
                bitmap.recycle()

                Timber.tag(tag).d("Cached page: ${cacheFile.absolutePath} (${cacheFile.length()} bytes)")
                Uri.fromFile(cacheFile)
            } catch (e: Exception) {
                Timber.tag(tag).e(e, "Error extracting page $pageIndex: ${e.message}")
                null
            }
        }

    /**
     * Get total page count
     *
     * @return Total number of pages detected in the archive
     */
    fun getPageCount(): Int = pageCount

    /**
     * Get comic metadata from ComicInfo.xml.
     * Falls back to filename if no XML found.
     *
     * @return [ComicMetadata] parsed from ComicInfo.xml or a fallback using filename
     */
    suspend fun getComicMetadata(): ComicMetadata =
        withContext(Dispatchers.IO) {
            return@withContext try {
                if (reader == null) {
                    Timber.tag(tag).w("Reader not initialized, using filename")
                    return@withContext ComicMetadata(title = File(comicUri).nameWithoutExtension)
                }

                val r = reader ?: return@withContext ComicMetadata(title = File(comicUri).nameWithoutExtension)
                val metadata = r.getMetadata()

                // If no metadata found, use filename as title
                if (metadata.title == null && metadata.series == null && metadata.number == null) {
                    val filename = File(comicUri).nameWithoutExtension
                    Timber.tag(tag).d("No metadata in XML, using filename: $filename")
                    return@withContext metadata.copy(title = filename)
                }

                metadata
            } catch (e: Exception) {
                Timber.tag(tag).e(e, "Error getting metadata: ${e.message}")
                ComicMetadata(title = File(comicUri).nameWithoutExtension)
            }
        }

    /**
     * Cleanup: close archive and remove cache files
     */
    fun cleanup() {
        try {
            reader?.close()
            reader = null

            (cacheDir.listFiles() ?: emptyArray()).forEach { file ->
                if (file.delete()) {
                    Timber.tag(tag).d("Deleted cache: ${file.name}")
                }
            }
            Timber.tag(tag).d("Cleanup complete")
        } catch (e: Exception) {
            Timber.tag(tag).e(e, "Error during cleanup: ${e.message}")
        }
    }
}
