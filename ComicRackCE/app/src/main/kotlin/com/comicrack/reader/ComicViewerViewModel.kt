package com.comicrack.reader

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comicrack.core.metadata.ComicMetadataService
import com.comicrack.data.database.dao.ComicBookDao
import com.comicrack.data.database.dao.ComicMetadataDao
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.ComicMetadata
import com.comicrack.data.database.repository.ComicRackMetadataRepository
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.FitMode
import com.comicrack.data.preferences.ReadingDirection
import com.comicrack.data.preferences.ReaderMode
import com.comicrack.data.preferences.ZoneSize
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.data.PageMetadata
import com.comicrack.wifi.metadata.SyncMetadataService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * ComicViewerViewModel - Manages page navigation, metadata loading, and reader visual settings.
 *
 * Responsibilities:
 * - Load pages from ComicPageProvider
 * - Track current page index
 * - Expose page metadata (series name, issue number, etc.)
 * - Handle page navigation (next, previous, go-to)
 * - Preload adjacent pages for smooth scrolling
 * - Track read status based on completion percentage
 * - Manage reader visual settings (rotation, fit mode, night mode)
 *
 * Performance & reliability notes:
 * - Preloading adjacent pages improves UX but must be bounded to avoid excessive memory usage.
 * - Provide hooks for canceling preloads when user navigates quickly or when memory pressure is detected.
 * - Add tests for state restoration across process death (save/restore current page and zoom).
 *
 * Phase 2: Works with PlaceholderPageProvider
 * Phase 3: Added rotation, fitMode, and nightMode state management
 * Phase 4: Will work with real archive readers
 * Phase B: Added read status tracking (90%+ = read)
 * Phase 2A: Converted to @HiltViewModel with full dependency injection
 *
 * @param application Application context for lifecycle and resources
 * @param pageProvider The [ComicPageProvider] used to load page bitmaps and metadata
 * @param preferencesRepository Preferences repository for reader settings
 * @param comicBookDao DAO used for comic metadata lookups
 * @param comicMetadataRepository Repository for detailed comic metadata
 * @param metadataService Service for parsing and loading metadata
 * @param syncMetadataService Service for managing WiFi sync metadata
 */
@HiltViewModel
class ComicViewerViewModel
    @Inject
    constructor(
        application: Application,
        private var pageProvider: ComicPageProvider,
        private val preferencesRepository: AppPreferencesRepository,
        private val comicBookDao: ComicBookDao,
        private val comicMetadataDao: ComicMetadataDao,
        private val metadataService: ComicMetadataService,
        private val readProgressDao: com.comicrack.data.database.dao.ReadProgressDao,
        private val libraryPathDao: LibraryPathDao,
        private val syncMetadataService: SyncMetadataService,
    ) : AndroidViewModel(application) {
        /**
         * Backing [MutableStateFlow] containing the total number of pages in the opened comic.
         *
         * Updated by [loadPages] to reflect the value returned by the current [ComicPageProvider].
         */
        private val _totalPages = MutableStateFlow(0)

        /**
         * Read-only [StateFlow] that emits the current total page count.
         */
        val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

        /**
         * Backing [MutableStateFlow] for the current page index (absolute index into the comic).
         */
        private val _currentPageIndex = MutableStateFlow(0)

        /**
         * Read-only [StateFlow] that emits the current absolute page index.
         */
        val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

        /**
         * Backing [MutableStateFlow] containing metadata for the current page (if available).
         */
        private val _currentPageMetadata = MutableStateFlow<PageMetadata?>(null)

        /**
         * Exposes per-page [PageMetadata] for the currently visible page.
         */
        val currentPageMetadata: StateFlow<PageMetadata?> = _currentPageMetadata.asStateFlow()

        /**
         * Backing state for comic-level metadata retrieved from the local database (if available).
         */
        private val _comicMetadata = MutableStateFlow<ComicBook?>(null)

        /**
         * Exposes core comic entity previously loaded via [loadComicMetadata].
         */
        val comicBook: StateFlow<ComicBook?> = _comicMetadata.asStateFlow()

        /**
         * Backing state for supplementary detailed metadata entity.
         */
        private val _comicMetadataDetailed = MutableStateFlow<ComicMetadata?>(null)

        /**
         * Exposes detailed comic metadata (creators, characters, teams, etc.)
         */
        val comicMetadataDetailed: StateFlow<ComicMetadata?> = _comicMetadataDetailed.asStateFlow()

        /**
         * Backing state indicating whether the viewer is currently loading pages or metadata.
         */
        private val _isLoading = MutableStateFlow(false)

        /**
         * Emits `true` while page loading or related I/O is in progress.
         */
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        // Track if comic is 90%+ complete
        /**
         * Backing state indicating whether the comic has been marked as read (>= 90% progress).
         */
        private val _isRead = MutableStateFlow(false)

        /**
         * Emits `true` when the comic is considered read (90% or more consumed).
         */
        val isRead: StateFlow<Boolean> = _isRead.asStateFlow()

        /**
         * ID of the currently viewed comic. Used to save/load read progress.
         */
        private var currentComicId: Long? = null

        // Phase 3: Reader visual settings
        /**
         * Backing state for page rotation in degrees. Allowed values: 0, 90, 180, 270.
         */
        private val _rotation = MutableStateFlow(0) // 0, 90, 180, 270 degrees

        /**
         * Emits the current rotation to be applied to page rendering.
         */
        val rotation: StateFlow<Int> = _rotation.asStateFlow()

        /**
         * Backing state for the current zoom factor applied to page rendering.
         */
        private val _zoomFactor = MutableStateFlow(1.0f)

        /**
         * Emits the current zoom factor. Values are clamped to a sensible range by zoomIn/zoomOut.
         */
        val zoomFactor: StateFlow<Float> = _zoomFactor.asStateFlow()

        // deleted isNightMode

        // Phase 9.1: Reader preferences (will be exposed if preferencesRepository is provided)
        /**
         * Emits the user-configured reader brightness override (0.0 - 1.0). Defaults to `0.5`.
         */
        val readerBrightness: StateFlow<Float> =
            preferencesRepository?.readerBrightness?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                0.5f,
            ) ?: MutableStateFlow(0.5f).asStateFlow()

        /**
         * Emits whether the custom reader brightness override is enabled.
         */
        val readerBrightnessEnabled: StateFlow<Boolean> =
            preferencesRepository?.readerBrightnessEnabled?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                true,
            ) ?: MutableStateFlow(true).asStateFlow()

        /**
         * Emits the current page fit mode (e.g., FIT_PAGE, FIT_WIDTH, FIT_HEIGHT).
         */
        val pageFitMode: StateFlow<FitMode> =
            preferencesRepository?.pageFitMode?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                FitMode.FIT_WIDTH,
            ) ?: MutableStateFlow(FitMode.FIT_WIDTH).asStateFlow()

        val readerKeepScreenOn: StateFlow<Boolean> =
            preferencesRepository?.readerKeepScreenOn?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                true,
            ) ?: MutableStateFlow(true).asStateFlow()

        val pageTransitionsEnabled: StateFlow<Boolean> =
            preferencesRepository?.pageTransitionsEnabled?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                true,
            ) ?: MutableStateFlow(true).asStateFlow()

        // Phase 9.2: Reading direction (RTL/LTR)
        /**
         * Emits the configured reading direction: LTR or RTL.
         */
        val readingDirection: StateFlow<ReadingDirection> =
            preferencesRepository?.readingDirection?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                ReadingDirection.LTR,
            ) ?: MutableStateFlow(ReadingDirection.LTR).asStateFlow()

        /**
         * Emits the configured size for the center tap zone.
         */
        val centerZoneSize: StateFlow<ZoneSize> =
            preferencesRepository?.centerZoneSize?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                ZoneSize.MEDIUM,
            ) ?: MutableStateFlow(ZoneSize.MEDIUM).asStateFlow()

        // Transient UI events (e.g. toasts)
        private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
        val uiEvent = _uiEvent.asSharedFlow()

        // Phase S3-03: Image Adjustments
        /**
         * Emits saturation multiplier for page rendering (default `1.0`).
         */
        val readerSaturation: StateFlow<Float> =
            preferencesRepository?.readerSaturation?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                1.0f,
            ) ?: MutableStateFlow(1.0f).asStateFlow()

        /**
         * Emits contrast multiplier for page rendering (default `1.0`).
         */
        val readerContrast: StateFlow<Float> =
            preferencesRepository?.readerContrast?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                1.0f,
            ) ?: MutableStateFlow(1.0f).asStateFlow()

        /**
         * Emits sharpness amount for page rendering (default `1.0`).
         */
        val readerSharpness: StateFlow<Float> =
            preferencesRepository?.readerSharpness?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                1.0f,
            ) ?: MutableStateFlow(1.0f).asStateFlow()

        // Phase 9.3: Reader Mode (Page/Scroll)
        val readerMode: StateFlow<ReaderMode> =
            preferencesRepository?.readerMode?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                ReaderMode.PAGE,
            ) ?: MutableStateFlow(ReaderMode.PAGE).asStateFlow()

        // Phase 11: Reader Dynamic Background Preference
        val readerDynamicBackground: StateFlow<Boolean> =
            preferencesRepository?.readerDynamicBackground?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                true,
            ) ?: MutableStateFlow(true).asStateFlow()

        val readerOpenWithMenu: StateFlow<Boolean> =
            preferencesRepository?.readerOpenWithMenu?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                true,
            ) ?: MutableStateFlow(true).asStateFlow()

        val readerShowTapToasts: StateFlow<Boolean> =
            preferencesRepository?.readerShowTapToasts?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                true,
            ) ?: MutableStateFlow(true).asStateFlow()

        val readerShowPageNumbers: StateFlow<Boolean> =
            preferencesRepository?.readerShowPageNumbers?.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                true,
            ) ?: MutableStateFlow(true).asStateFlow()

        // Phase 11: Dynamic Background Color (Border Analysis)
        private val _pageBackgroundColor = MutableStateFlow<Int?>(null)
        val pageBackgroundColor: StateFlow<Int?> = _pageBackgroundColor.asStateFlow()

        // Phase 10: Unified Reading State
        // Combines prefs + current index to determine what pages to show
        /**
         * Unified [ReadingState] combining page counts, current index and reader preferences.
         *
         * This is the canonical source used by the UI to determine which pages to request and
         * whether the reader is in Webtoon/Single mode or RTL layout.
         */
        val readingState: StateFlow<com.comicrack.reader.state.ReadingState> =
            combine(
                _totalPages,
                _currentPageIndex,
                MutableStateFlow(false), // doublePageDisplay removed
                readingDirection,
                readerMode,
            ) { total, current, isDouble, direction, mode ->
                calculateReadingState(total, current, isDouble, direction, mode)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                com.comicrack.reader.state
                    .ReadingState(),
            )

        init {
            Timber.tag("ComicViewerViewModel").d("Initialized with provider: ${pageProvider::class.simpleName}")

            // reset background color if preference is disabled
            viewModelScope.launch {
                readerDynamicBackground.collect { enabled ->
                    if (!enabled) {
                        _pageBackgroundColor.value = null
                    }
                }
            }
        }

        /**
         * Capture current page as screenshot for sharing or clipboard
         */
        enum class CaptureMode { SHARE, CLIPBOARD, SAVE }

        /**
         * Captures the current page as a bitmap and performs the requested action (share, copy, or save).
         *
         * @param context Android context for IO and toast display.
         * @param mode The desired [CaptureMode] action.
         */
        fun capturePageScreenshot(
            context: Context,
            mode: CaptureMode = CaptureMode.SHARE,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val index = _currentPageIndex.value
                    val bitmap = pageProvider.getPageBitmap(index)

                    if (bitmap != null) {
                        when (mode) {
                            CaptureMode.CLIPBOARD -> copyBitmapToClipboard(context, bitmap)
                            CaptureMode.SAVE -> saveBitmapToDocuments(context, bitmap)
                            CaptureMode.SHARE -> shareBitmap(context, bitmap)
                        }
                    } else {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Could not capture page image.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("ComicViewerViewModel").e(e, "Error capturing page")
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        private fun saveBitmapToDocuments(
            context: Context,
            bitmap: Bitmap,
        ) {
            try {
                val contentResolver = context.contentResolver
                val displayName = "ComicPage_${System.currentTimeMillis()}.jpg"

                val contentValues =
                    android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(
                                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                                android.os.Environment.DIRECTORY_PICTURES + "/ComicRack",
                            )
                            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                val uri =
                    contentResolver.insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues,
                    )
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                        outputStream.flush()
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(uri, contentValues, null, null)
                    }

                    viewModelScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Saved to Pictures!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Timber.tag("ComicViewerViewModel").e(e, "Failed to save to documents")
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /**
         * Internal helper to write [bitmap] to a temporary cache file and launch a share intent.
         *
         * Uses [FileProvider] to expose a secure content URI for the file and starts a chooser.
         */
        private fun shareBitmap(
            context: Context,
            bitmap: Bitmap,
        ) {
            try {
                val cacheDir = java.io.File(context.cacheDir, "share")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val cacheFile = java.io.File(cacheDir, "page_snapshot_${System.currentTimeMillis()}.jpg")
                val fos = FileOutputStream(cacheFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                fos.close()

                val uri =
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        cacheFile,
                    )

                val intent =
                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                val chooser = android.content.Intent.createChooser(intent, "Share Page")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                Timber.tag("ComicViewerViewModel").e(e, "Failed to share bitmap")
            }
        }

        /**
         * Internal helper to save [bitmap] to disk and place a URI on the system clipboard.
         *
         * Shows a short confirmation Toast on the main thread when complete.
         */
        private fun copyBitmapToClipboard(
            context: Context,
            bitmap: Bitmap,
        ) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

                val cacheDir = java.io.File(context.cacheDir, "share")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val cacheFile = java.io.File(cacheDir, "clip_image.jpg")
                val fos = FileOutputStream(cacheFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                fos.close()

                val uri =
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        cacheFile,
                    )

                val clip = android.content.ClipData.newUri(context.contentResolver, "Comic Page", uri)
                clipboard.setPrimaryClip(clip)

                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Page copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.tag("ComicViewerViewModel").e(e, "Failed to copy to clipboard")
            }
        }

        /**
         * Helper: capture and share the current page.
         */
        fun sharePageScreenshot(context: Context) {
            capturePageScreenshot(context, CaptureMode.SHARE)
        }

        /**
         * Helper: capture and copy to clipboard the current page.
         */
        fun copyPageToClipboard(context: Context) {
            capturePageScreenshot(context, CaptureMode.CLIPBOARD)
        }

        /**
         * Helper: capture and save to documents the current page.
         */
        fun savePageToDocuments(context: Context) {
            capturePageScreenshot(context, CaptureMode.SAVE)
        }

        internal fun calculateReadingState(
            total: Int,
            current: Int,
            _isDouble: Boolean, // Kept for future "Split" logic or Metadata use
            direction: ReadingDirection,
            readerMode: ReaderMode,
        ): com.comicrack.reader.state.ReadingState {
            // User Feedback: "Double Page" is a single wide image, NOT two side-by-side images.
            // Therefore, we do NOT synthesize spreads. We always show 1 page at a time.
            // The "Double Page" preference might later control how we handle these wide images (e.g., Split vs Fit).

            val mode =
                if (readerMode == ReaderMode.SCROLL) {
                    com.comicrack.reader.state.ReadingMode.Webtoon
                } else {
                    com.comicrack.reader.state.ReadingMode.Single
                }

            val isRtl = direction == ReadingDirection.RTL

            val visible = mutableListOf<Int>()
            // In Webtoon mode, we technically "see" many pages, but 'visiblePageIndices'
            // usually tracks what page to request metadata for / current index.
            // For simplicity, we just say the current page is visible.
            if (total > 0 && current < total) {
                visible.add(current)
            }

            return com.comicrack.reader.state.ReadingState(
                totalPages = total,
                currentAbsoluteIndex = current,
                mode = mode,
                isRtl = isRtl,
                visiblePageIndices = visible,
            )
        }

        /**
         * Update the page provider (used when switching from placeholder to real archive).
         * This should be called before loadPages() when the provider changes.
         */
        fun updateProvider(newProvider: ComicPageProvider) {
            pageProvider = newProvider
            // Reload pages will trigger state update via _totalPages
        }

        /**
         * Load pages from the current [ComicPageProvider] and initialize viewer state.
         *
         * This updates [_totalPages] and loads initial page metadata. Errors are logged
         * and the loading flag is cleared on failure.
         */
        fun loadPages() {
            viewModelScope.launch {
                try {
                    _isLoading.value = true

                    // Get total page count
                    val count = pageProvider.getPageCount()
                    _totalPages.value = count

                    Timber.tag("ComicViewerViewModel").d("Loaded $count pages")

                    // Load first page metadata
                    if (count > 0) {
                        val metadata = pageProvider.getPageMetadata(0)
                        _currentPageMetadata.value = metadata

                        // Preload adjacent pages
                        preloadAdjacent(0)
                    }

                    _isLoading.value = false
                } catch (e: Exception) {
                    Timber.tag("ComicViewerViewModel").e(e, "Error loading pages")
                    _isLoading.value = false
                }
            }
        }

        /**
         * Advance to the next page if available.
         */
        fun nextPage() {
            // Always step by 1 since we don't synthesize spreads
            val state = readingState.value
            val step = 1
            val next = state.currentAbsoluteIndex + step

            if (next < state.totalPages) {
                goToPage(next)
            }
        }

        /**
         * Move to the previous page if available.
         */
        fun previousPage() {
            val state = readingState.value
            val step = 1
            val prev = state.currentAbsoluteIndex - step

            if (prev >= 0) {
                goToPage(prev)
            }
        }

        /**
         * Jump to a specific zero-based page index.
         *
         * Loads page metadata and preloads adjacent pages; also triggers read status evaluation.
         *
         * @param pageIndex absolute index into the current comic
         */
        fun goToPage(pageIndex: Int) {
            if (pageIndex in 0 until _totalPages.value) {
                // Revert complex Double Mode seeking logic
                _currentPageIndex.value = pageIndex

                // Check if comic is now 90%+ read
                updateReadStatus(pageIndex)

                // Save read progress to database
                saveReadProgress()

                viewModelScope.launch {
                    try {
                        // Load metadata for this page
                        val metadata = pageProvider.getPageMetadata(pageIndex)
                        _currentPageMetadata.value = metadata

                        // Preload adjacent pages
                        preloadAdjacent(pageIndex)

                        Timber.tag("ComicViewerViewModel").d("Navigated to page $pageIndex")
                    } catch (e: Exception) {
                        Timber.tag("ComicViewerViewModel").e(e, "Error loading page $pageIndex")
                    }
                }
            } else {
                Timber
                    .tag(
                        "ComicViewerViewModel",
                    ).w("Page index out of bounds: $pageIndex (max: ${_totalPages.value - 1})")
            }
        }

        /**
         * Preload pages near [pageIndex] to improve perceived performance.
         *
         * This implementation requests a small bounded neighborhood of pages from the provider.
         */
        private fun preloadAdjacent(pageIndex: Int) {
            viewModelScope.launch {
                try {
                    // Preload simplified: next 2, prev 1
                    pageProvider.preloadPages(pageIndex, distance = 2)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        /**
         * Evaluate whether the comic should be marked as read based on [pageIndex].
         *
         * The comic is marked read when the user reaches 90% or more of the available pages.
         * Uses consistent formula: (pageIndex + 1) * 100 / totalPages
         * This converts 0-indexed page to 1-indexed for percentage calculation.
         */
        private fun updateReadStatus(pageIndex: Int) {
            val total = _totalPages.value
            if (total <= 0) return

            val percentComplete = ((pageIndex + 1).toFloat() / total.toFloat()) * PERCENT_MULTIPLIER
            val isNearlyComplete = percentComplete >= READ_COMPLETION_THRESHOLD

            if (isNearlyComplete && !_isRead.value) {
                _isRead.value = true
                Timber.tag("ComicViewerViewModel").d("Comic marked as read (${percentComplete.toInt()}%)")
            }
        }

        companion object {
            /**
             * Percentage threshold at which a comic is considered "read".
             * When user reaches 90% or more of pages, comic is marked as read.
             */
            private const val READ_COMPLETION_THRESHOLD = 90f

            /**
             * Multiplier to convert decimal to percentage (0.5 → 50%).
             */
            private const val PERCENT_MULTIPLIER = 100f
        }

        /**
         * Set the comic being viewed and load its read progress from database.
         * Should be called from Activity when comic is opened.
         *
         * @param comicId The ID of the comic in the comics table
         */
        fun setComicId(comicId: Long) {
            currentComicId = comicId
            loadReadProgress(comicId)
        }

        /**
         * Load existing read progress from database and restore reading position.
         */
        private fun loadReadProgress(comicId: Long) {
            viewModelScope.launch {
                try {
                    val progress = readProgressDao.getProgress(comicId)
                    if (progress != null) {
                        // Resume from last read position
                        _currentPageIndex.value = progress.currentPage
                        _isRead.value = progress.isRead
                        Timber.tag("ComicViewerViewModel").d(
                            "Loaded progress: page ${progress.currentPage}/" +
                                "${progress.totalPages}, read=${progress.isRead}",
                        )
                    } else {
                        // First time opening this comic
                        _currentPageIndex.value = 0
                        _isRead.value = false
                        Timber.tag("ComicViewerViewModel").d("No existing progress for comic $comicId")
                    }
                } catch (e: Exception) {
                    Timber.tag("ComicViewerViewModel").e(e, "Failed to load read progress for comic $comicId")
                }
            }
        }

        /**
         * Save current read progress to database.
         * Called on page navigation and when closing the comic.
         */
        private fun saveReadProgress() {
            val comicId = currentComicId ?: return
            val comic = _comicMetadata.value

            viewModelScope.launch {
                try {
                    val existing = readProgressDao.getProgress(comicId)

                    val currentPage = _currentPageIndex.value
                    val totalPages = _totalPages.value
                    val isRead = _isRead.value

                    if (existing != null) {
                        // Update existing progress
                        existing.currentPage = currentPage
                        existing.totalPages = totalPages
                        existing.lastReadTime = System.currentTimeMillis()
                        existing.isRead = isRead
                        readProgressDao.update(existing)
                        Timber.tag("ComicViewerViewModel").d(
                            "Updated progress: page ${existing.currentPage}/" +
                                "${existing.totalPages}, read=${existing.isRead}",
                        )
                    } else {
                        // Create new progress entry
                        val progress =
                            com.comicrack.data.database.entities.ReadProgress(
                                comicId = comicId,
                                currentPage = currentPage,
                                totalPages = totalPages,
                                lastReadTime = System.currentTimeMillis(),
                                isRead = isRead,
                            )
                        readProgressDao.insert(progress)
                        Timber.tag("ComicViewerViewModel").d(
                            "Created progress: page ${progress.currentPage}/" +
                                "${progress.totalPages}, read=${progress.isRead}",
                        )
                    }

                    // Update sync metadata if it's a sync book
                    if (comic != null) {
                        libraryPathDao.getLibraryPathById(comic.libraryPathId)?.let { path ->
                            syncMetadataService.updateSyncReadStatus(
                                comic = comic,
                                libraryPath = path,
                                isRead = isRead,
                                currentPage = currentPage,
                                lastPageRead = currentPage // Assuming current is last read for simplicity
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("ComicViewerViewModel").e(e, "Failed to save read progress")
                }
            }
        }

        /**
         * Clear all cached pages from the provider.
         */
        fun clearCache() {
            viewModelScope.launch {
                pageProvider.clearCache()
            }
        }

        /**
         * Rotate the currently visible page 90Â° clockwise. Wraps from 270 -> 0.
         */
        fun rotatePage() {
            val current = _rotation.value
            val next = (current + 90) % 360
            _rotation.value = next
            viewModelScope.launch {
                _uiEvent.emit("Rotated $next\u00b0")
            }
            Timber.tag("ComicViewerViewModel").d("Page rotated to $next\u00b0")
        }

        /**
         * Increase zoom by a fixed step (0.25x), clamped to a maximum.
         */
        fun zoomIn() {
            val current = _zoomFactor.value
            val newZoom = (current + 0.25f).coerceAtMost(3.0f)
            _zoomFactor.value = newZoom
            Timber.tag("ComicViewerViewModel").d("Zoom In to $newZoom")
        }

        /**
         * Decrease zoom by a fixed step (0.25x), clamped to a minimum.
         */
        fun zoomOut() {
            val current = _zoomFactor.value
            val newZoom = (current - 0.25f).coerceAtLeast(0.4f)
            _zoomFactor.value = newZoom
            Timber.tag("ComicViewerViewModel").d("Zoom Out to $newZoom")
        }

        /**
         * Resets the zoom factor to its base 1x state.
         * Called when changing pages or fit modes to ensure consistent initial viewing.
         */
        fun resetZoom() {
            _zoomFactor.value = 1.0f
            Timber.tag("ComicViewerViewModel").d("Zoom reset to 1.0")
        }

        /**
         * Cycle through fit modes: Page -> Width -> Height -> Page
         */
        /**
         * Cycle through fit modes: Page -> Width -> Height -> Page
         */

        /**
         * Persist a specific page fit mode to preferences.
         *
         * @param mode the desired [FitMode]
         */
        fun setFitMode(mode: FitMode) {
            viewModelScope.launch {
                resetZoom() // Ensure zoom is reset when changing fit modes
                preferencesRepository?.setPageFitMode(mode)
                Timber.tag("ComicViewerViewModel").d("Fit mode set to $mode")
            }
        }

        /**
         * Cycle through available fit modes in a deterministic order.
         */
        fun cycleFitMode() {
            // Map local FitMode to Repository FitMode
            val current = pageFitMode.value
            val next =
                when (current) {
                    FitMode.FIT_PAGE -> FitMode.FIT_WIDTH
                    FitMode.FIT_WIDTH -> FitMode.FIT_HEIGHT
                    FitMode.FIT_HEIGHT -> FitMode.FIT_PAGE
                    else -> FitMode.FIT_PAGE
                }

            val label =
                when (next) {
                    FitMode.FIT_PAGE -> "Fit to Page"
                    FitMode.FIT_WIDTH -> "Fit to Width"
                    FitMode.FIT_HEIGHT -> "Fit to Height"
                    else -> "Original Size"
                }

            viewModelScope.launch {
                preferencesRepository.setPageFitMode(next)
                _uiEvent.emit(label)
                Timber.tag("ComicViewerViewModel").d("Fit mode cycling to $next")
            }
        }

        /**
         * Toggle night mode on/off.
         */
        // deleted toggleNightMode

        // ===== Phase 4: Preference Update Functions =====

        /**
         * Update double page display setting in preferencesRepository.
         */

        /**
         * Persist reader saturation preference.
         */
        fun setSaturation(value: Float) {
            viewModelScope.launch { preferencesRepository?.setReaderSaturation(value) }
        }

        /**
         * Persist reader contrast preference.
         */
        fun setContrast(value: Float) {
            viewModelScope.launch { preferencesRepository?.setReaderContrast(value) }
        }

        /**
         * Persist reader sharpness preference.
         */
        fun setSharpness(value: Float) {
            viewModelScope.launch { preferencesRepository?.setReaderSharpness(value) }
        }

        /**
         * Persist reading direction (RTL/LTR) in preferences.
         */
        fun toggleRtl(enabled: Boolean) {
            viewModelScope.launch {
                val direction =
                    if (enabled) {
                        ReadingDirection.RTL
                    } else {
                        ReadingDirection.LTR
                    }
                preferencesRepository.setReadingDirection(direction)
                _uiEvent.emit(if (enabled) "Right-to-Left" else "Left-to-Right")
                Timber.tag("ComicViewerViewModel").d("Reading direction: $direction")
            }
        }

        /**
         * Persist reader brightness value to preferences.
         */
        fun setBrightness(value: Float) {
            viewModelScope.launch {
                preferencesRepository.setReaderBrightness(value)
                Timber.tag("ComicViewerViewModel").d("Reader brightness: $value")
            }
        }

        /**
         * Toggle whether the app uses the custom reader brightness override.
         */
        fun toggleBrightness(enabled: Boolean) {
            viewModelScope.launch {
                preferencesRepository.setReaderBrightnessEnabled(enabled)
                Timber.tag("ComicViewerViewModel").d("Brightness override enabled: $enabled")
            }
        }

        /**
         * Called when the ViewModel is cleared; ensures caches are released and read progress is saved.
         */
        override fun onCleared() {
            super.onCleared()
            // Save final read progress before ViewModel is destroyed
            saveReadProgress()
            clearCache()
        }

        // Phase 3: Load metadata

        /**
         * Load comic-level metadata from the local database by file path.
         *
         * This updates [comicMetadata] when a match is found.
         */
        fun loadComicMetadata(filePath: String) {
            viewModelScope.launch {
                try {
                    // Try finding by exact path match first
                    val comic = comicBookDao.getComicBookByPath(filePath)
                    if (comic != null) {
                        _comicMetadata.value = comic

                        // Also load detailed metadata if it exists
                        val metadata = comicMetadataDao.getMetadataByComicId(comic.id)
                        if (metadata != null) {
                            _comicMetadataDetailed.value = metadata
                        } else {
                            // Metadata missing in DB, try parsing from archive and sync to DB
                            val newMetadata = metadataService.loadMetadataForComic(comic)
                            _comicMetadataDetailed.value = newMetadata
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("ComicViewerViewModel").e(e, "Failed to load comic metadata")
                }
            }
        }

        // Phase 11: Update background color from analysis

        /**
         * Update the dynamic page background color used by the reader.
         *
         * The color is applied only when dynamic backgrounds are enabled and the reader
         * is in page mode (not scroll mode).
         */
        fun updatePageBackgroundColor(color: Int?) {
            // Only allow dynamic color if enabled and NOT in scrolling mode (S3-03 Refinement)
            if (readerDynamicBackground.value && readerMode.value == ReaderMode.PAGE) {
                _pageBackgroundColor.value = color
            } else {
                _pageBackgroundColor.value = null
            }
        }
    }

// Phase 9.1: Reader preferences (will be exposed if preferencesRepository is provided)
