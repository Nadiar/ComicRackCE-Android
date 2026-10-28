package com.comicrack.core.scanner

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import com.comicrack.core.metadata.ComicMetadataService
import com.comicrack.data.database.entities.ComicBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Service for orchestrating library scanning and metadata loading.
 *
 * Coordinates [LibraryScannerService] (file discovery) and [ComicMetadataService]
 * (metadata parsing) into a unified operation. Provides progress tracking via Flow.
 *
 * **Usage**:
 * ```kotlin
 * val libraryService = LibraryService(scannerService, metadataService)
 *
 * // Observe progress
 * libraryService.scanProgress.collect { progress ->
 *     when (progress) {
 *         is ScanProgress.Scanning -> updateUI("Scanning ${progress.pathName}")
 *         is ScanProgress.LoadingMetadata -> updateUI("Loading metadata for ${progress.comicTitle}")
 *         is ScanProgress.Complete -> handleResults(progress.result)
 *     }
 * }
 *
 * // Start scan
 * val result = libraryService.scanAllLibraries()
 * ```
 *
 * Phase 4: Library Scan Integration
 */
class LibraryService(
    private val scannerService: LibraryScannerService,
    private val metadataService: ComicMetadataService,
) {
    companion object {
        private const val TAG = "LibraryService"
    }

    private val _scanProgress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private var scanCancelled = false

    /**
     * Refresh library: Sync filesystem state and handle metadata for new/changed files.
     *
     * @param forced If true, performs a full "Rescan" (re-parse metadata for ALL files).
     *               If false, performs a "Refresh" (filesystem sync + metadata for changes).
     * @return Result summary
     */
    suspend fun refreshLibrary(forced: Boolean = false): LibraryScanResult {
        val startTime = System.currentTimeMillis()
        scanCancelled = false

        return try {
            _scanProgress.value = ScanProgress.Scanning("Initializing discovery...", 0)

            var metadataLoaded = 0
            
            // Terminology: Refresh = Sync filesystem with DB
            // Interleaved Rescan: Metadata is loaded AS files are discovered if they are new/changed
            val (allComics, changedComics) = scannerService.refreshAllActivePaths { comic, isNewOrChanged ->
                if (forced || isNewOrChanged) {
                    try {
                        _scanProgress.value = ScanProgress.LoadingMetadata(comic.title)
                        val metadata = metadataService.loadMetadataForComic(comic, force = forced)
                        if (metadata != null) {
                            metadataLoaded++
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Failed to load metadata for ${comic.title}")
                    }
                }
            }

            if (scanCancelled) {
                return LibraryScanResult(
                    totalFound = 0,
                    metadataLoaded = 0,
                    errorCount = 0,
                    duration = System.currentTimeMillis() - startTime,
                    errors = listOf("Scan was cancelled by user"),
                )
            }

            val duration = System.currentTimeMillis() - startTime
            val scanResult =
                LibraryScanResult(
                    totalFound = allComics.size,
                    metadataLoaded = metadataLoaded,
                    errorCount = 0,
                    duration = duration,
                    errors = emptyList(),
                )

            _scanProgress.value = ScanProgress.Complete(scanResult)
            Timber.tag(TAG).i(
                "Scan complete: ${allComics.size} comics, " +
                    "$metadataLoaded metadata updates in ${duration}ms"
            )
            scanResult
        } catch (exception: Exception) {
            Timber.tag(TAG).e(exception, "Library rescan failed")
            val duration = System.currentTimeMillis() - startTime

            val scanResult =
                LibraryScanResult(
                    totalFound = 0,
                    metadataLoaded = 0,
                    errorCount = 1,
                    duration = duration,
                    errors = listOf(exception.message ?: "Unknown error during scan"),
                )

            _scanProgress.value = ScanProgress.Complete(scanResult)
            scanResult
        }
    }

    /**
     * Refresh a specific library path: Sync files and metadata.
     *
     * @param pathId The library path ID to refresh
     * @param forced If true, performs a full "Rescan" of this path.
     * @return Result summary
     */
    suspend fun refreshSpecificPath(pathId: Long, forced: Boolean = false): LibraryScanResult {
        val startTime = System.currentTimeMillis()
        scanCancelled = false

        return try {
            _scanProgress.value = ScanProgress.Scanning("Initializing discovery...", 0)

            // Get the specific library path
            val pathDao = scannerService.getDatabase().libraryPathDao()
            val libraryPath =
                pathDao.getLibraryPathById(pathId) ?: return LibraryScanResult(
                    totalFound = 0,
                    metadataLoaded = 0,
                    errorCount = 1,
                    duration = System.currentTimeMillis() - startTime,
                    errors = listOf("Library path with ID $pathId not found"),
                )

            var metadataLoaded = 0
            
            // Terminology: Refresh = Sync filesystem with DB
            // Interleaved Rescan: Metadata is loaded AS files are discovered
            val (allComics, changedComics) = scannerService.refreshLibraryPath(libraryPath) { comic, isNewOrChanged ->
                if (forced || isNewOrChanged) {
                    try {
                        _scanProgress.value = ScanProgress.LoadingMetadata(comic.title)
                        val metadata = metadataService.loadMetadataForComic(comic, force = forced)
                        if (metadata != null) {
                            metadataLoaded++
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Failed to load metadata for ${comic.title}")
                    }
                }
            }

            if (scanCancelled) {
                return LibraryScanResult(
                    totalFound = 0,
                    metadataLoaded = 0,
                    errorCount = 0,
                    duration = System.currentTimeMillis() - startTime,
                    errors = listOf("Scan was cancelled by user"),
                )
            }

            val duration = System.currentTimeMillis() - startTime
            val scanResult =
                LibraryScanResult(
                    totalFound = allComics.size,
                    metadataLoaded = metadataLoaded,
                    errorCount = 0,
                    duration = duration,
                    errors = emptyList(),
                )

            _scanProgress.value = ScanProgress.Complete(scanResult)
            scanResult
        } catch (exception: Exception) {
            Timber.tag(TAG).e(exception, "Library rescan failed for path $pathId")
            val duration = System.currentTimeMillis() - startTime

            val scanResult =
                LibraryScanResult(
                    totalFound = 0,
                    metadataLoaded = 0,
                    errorCount = 1,
                    duration = duration,
                    errors = listOf(exception.message ?: "Unknown error during scan"),
                )

            _scanProgress.value = ScanProgress.Complete(scanResult)
            scanResult
        }
    }

    /**
     * Cancel ongoing scan gracefully.
     */
    fun cancelScan() {
        Timber.tag(TAG).i("User requested scan cancellation")
        scanCancelled = true
    }

    private fun getDatabase() = scannerService.getDatabase()
}

/**
 * Represents scan progress at different stages.
 */
sealed class ScanProgress {
    /**
     * Scan not running or completed.
     */
    object Idle : ScanProgress()

    /**
     * Currently scanning a library path.
     *
     * @param pathName Display name of the path being scanned
     * @param filesFound Number of comic files found so far
     */
    data class Scanning(
        val pathName: String,
        val filesFound: Int,
    ) : ScanProgress()

    /**
     * Loading metadata from a comic's archive.
     *
     * @param comicTitle Title of the comic being processed
     */
    data class LoadingMetadata(
        val comicTitle: String,
    ) : ScanProgress()

    /**
     * Scan completed with results.
     *
     * @param result The final scan result
     */
    data class Complete(
        val result: LibraryScanResult,
    ) : ScanProgress()
}

/**
 * Summary of a library scan operation.
 *
 * @property totalFound Total number of comics discovered
 * @property metadataLoaded Number of comics with successfully parsed metadata
 * @property errorCount Number of errors encountered during scan
 * @property duration Total duration in milliseconds
 * @property errors List of error messages
 */
data class LibraryScanResult(
    val totalFound: Int,
    val metadataLoaded: Int,
    val errorCount: Int,
    val duration: Long,
    val errors: List<String>,
) {
    /**
     * Success percentage (0-100).
     */
    val successPercentage: Int
        get() = if (totalFound == 0) 100 else (metadataLoaded * 100) / totalFound

    /**
     * Human-readable duration string.
     */
    val durationString: String
        get() {
            val seconds = duration / 1000
            val millis = duration % 1000
            return if (seconds > 0) {
                "${seconds}s ${millis}ms"
            } else {
                "${millis}ms"
            }
        }

    /**
     * Indicates if scan was successful (no errors, metadata loaded > 0).
     */
    val isSuccessful: Boolean
        get() = errorCount == 0 && (totalFound == 0 || metadataLoaded > 0)
}
