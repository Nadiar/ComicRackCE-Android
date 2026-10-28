package com.comicrack.reader

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comicrack.data.database.dao.ComicBookDao
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.database.dao.ReadProgressDao
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.LibraryPath
import com.comicrack.core.storage.provider.StorageType
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.LibraryViewMode
import com.comicrack.data.preferences.SortOrder
import com.comicrack.wifi.metadata.SyncMetadataService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.comicrack.core.work.LibraryScanWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.work.WorkInfo
import javax.inject.Inject

/**
 * ComposLibraryViewModel – Project Delta Phase 1 / Phase B
 *
 * Manages comic library data and presentation state for the Compose library UI.
 *
 * Features:
 * - Observes all comics from the database as a reactive Flow
 * - Converts Flow<List<ComicBook>> to StateFlow for Compose consumption
 * - Dependency injection via Hilt (@HiltViewModel)
 * - Provides lifecycle-aware coroutine scope via viewModelScope
 * - Phase B: Supports filtering (All/Unread/Read/Reading) and view modes (Cover/Stack/Detail)
 *
 * Implementation notes:
 * - Avoid performing blocking database or file IO on the main thread; use
 *   `viewModelScope.launch(Dispatchers.IO)` for such work and add tests that mock
 *   DAO interactions when possible.
 * - Tests should verify behavior for auto-scan triggers and handling of empty libraries.
 *
 * Usage:
 * ```kotlin
 * val viewModel: ComposLibraryViewModel = viewModel()  // Injected by Hilt
 * val comics by viewModel.comics.collectAsStateWithLifecycle()
 * viewModel.toggleViewMode()  // Cycles Cover → Stack → Detail
 * viewModel.toggleFilterMode() // Cycles All → Unread → Read → Reading
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ComposLibraryViewModel
    @Inject
    constructor(
        application: Application,
        private val comicDao: ComicBookDao,
        private val readProgressDao: ReadProgressDao,
        private val libraryPathDao: LibraryPathDao,
        private val appPreferencesRepository: AppPreferencesRepository,
        private val libraryService: com.comicrack.core.scanner.LibraryService,
        private val syncMetadataService: SyncMetadataService,
    ) : AndroidViewModel(application) {
        // Note: Series preferences are now managed via AppPreferencesRepository (DataStore)

        // Scanning state - combines internal service progress with WorkManager job status
        val isScanning: StateFlow<Boolean> =
            combine(
                libraryService.scanProgress.map { progress ->
                    progress !is com.comicrack.core.scanner.ScanProgress.Idle &&
                        progress !is com.comicrack.core.scanner.ScanProgress.Complete
                },
                WorkManager.getInstance(getApplication()).getWorkInfosByTagFlow("library-scan").map { workInfos ->
                    workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                }
            ) { serviceScanning, workScanning ->
                serviceScanning || workScanning
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

        init {
            // Check if we need to auto-scan on startup
            // This handles the case where storage was cleared but library_path still exists in DataStore
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val libraryPath = appPreferencesRepository.libraryPath.first()
                    val comicCount = comicDao.getTotalComicCount()

                    Timber
                        .tag(
                            "ComposLibraryViewModel",
                        ).d("Init check: libraryPath=$libraryPath, comicCount=$comicCount")

                    val autoScan = appPreferencesRepository.autoScanLibrary.first()

                    Timber
                        .tag(
                            "ComposLibraryViewModel",
                        ).d("Init check: libraryPath=$libraryPath, comicCount=$comicCount, autoScan=$autoScan")

                    if (!libraryPath.isNullOrEmpty() && (comicCount == 0 || autoScan)) {
                        Timber.tag("ComposLibraryViewModel").d("Triggering init scan")
                        scanLibraryPath(libraryPath)

                        // Also scan additional folders
                        val additional = appPreferencesRepository.libraryAdditionalFolders.first()
                        additional.forEach { scanLibraryPath(it) }
                    }

                    if (autoScan) {
                        removeStaleComics()
                    }
                } catch (e: Exception) {
                    Timber.tag("ComposLibraryViewModel").e(e, "Error during init scan check")
                }
            }
        }

        /**
         * Scan the given library path and import discovered comics.
         *
         * @param path The path to scan
         * @param forced If true, re-parse metadata for ALL files.
         */
        fun scanLibraryPath(path: String, forced: Boolean = false) {
            viewModelScope.launch {
                // Ensure path is in DB first
                updateLibraryFolders(path)

                // Mimic targeted scan request
                val libraryPath = libraryPathDao.getLibraryPathByPath(path)
                if (libraryPath != null) {
                    val scanRequest = OneTimeWorkRequestBuilder<LibraryScanWorker>()
                        .setInputData(androidx.work.workDataOf(
                            "pathId" to libraryPath.id,
                            "forced" to forced
                        ))
                        .addTag("library-scan")
                        .build()

                    WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                        "library-scan-${libraryPath.id}",
                        ExistingWorkPolicy.REPLACE,
                        scanRequest
                    )
                } else {
                    // Fallback to full refresh
                    refreshLibrary(forced)
                }
            }
        }

        // ===== Preference Flows (Phase 4 Integration) =====
        // Convert AppPreferencesRepository flows to StateFlow for Compose consumption
        val libraryViewMode: StateFlow<LibraryViewMode> =
            appPreferencesRepository.libraryViewMode
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = LibraryViewMode.GRID,
                )

        val librarySortOrder: StateFlow<SortOrder> =
            appPreferencesRepository.librarySortOrder
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = SortOrder.TITLE,
                )

        // ===== Phase 8 Task 4: Layout Preferences =====
        val libraryLayoutColumns: StateFlow<Int> =
            appPreferencesRepository.libraryLayoutColumns
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 2,
                )

        val libraryGridSpacing: StateFlow<Float> =
            appPreferencesRepository.libraryGridSpacing
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 8.0f,
                )

        val libraryCardElevation: StateFlow<Float> =
            appPreferencesRepository.libraryCardElevation
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 4.0f,
                )

        val libraryShowTitles: StateFlow<Boolean> =
            appPreferencesRepository.libraryShowTitles
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = true,
                )

        val libraryAnimateCards: StateFlow<Boolean> =
            appPreferencesRepository.libraryAnimateCards
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = true,
                )

        // ===== Library Path (Phase B UI Refinement) =====
        val currentLibraryPath: StateFlow<String?> =
            appPreferencesRepository.libraryPath
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )

        // View mode state (Phase B)
        enum class ViewMode {
            COVER, // Show cover only (default)
            STACK, // Stack all issues of same series+volume
            DETAIL, // Show artist/writer/summary info
        }

        // Filter mode state (Phase B)
        enum class FilterMode {
            ALL, // Show all comics
            NEW, // Show newly added comics (last 7 days)
            UNREAD, // Show only unread (no progress or not marked read)
            READ, // Show only fully read
            READING, // Show currently reading (in progress)
        }

        // Mutable state for UI controls
        private val _viewMode = MutableStateFlow(ViewMode.COVER)
        val viewMode: StateFlow<ViewMode> = _viewMode

        private val _filterMode = MutableStateFlow(FilterMode.ALL)
        val filterMode: StateFlow<FilterMode> = _filterMode

        private val _selectedLibraryPathId = MutableStateFlow<Long?>(null)
        val selectedLibraryPathId: StateFlow<Long?> = _selectedLibraryPathId.asStateFlow()

        // Storage type filter for remote library (Phase SRC-05-007 v2)
        private val _storageFilter = MutableStateFlow<StorageType?>(null)
        val storageFilter: StateFlow<StorageType?> = _storageFilter.asStateFlow()

        /**
         * Set the current filter mode (All/Unread/Read/Reading).
         * Clears any physical library path filter.
         */
        fun setFilterMode(mode: FilterMode) {
            _selectedLibraryPathId.value = null
            _filterMode.value = mode
        }

        /**
         * Set the current physical library path to filter by.
         * Clears the virtual filter mode to ALL.
         */
        fun setLibraryPathId(pathId: Long?) {
            _filterMode.value = FilterMode.ALL
            _selectedLibraryPathId.value = pathId
            Timber.tag("ComposLibraryViewModel").d("setLibraryPathId called with pathId=$pathId")
        }

        /**
         * Set the storage type filter (null = all, SAF = local, GOOGLE_DRIVE = remote).
         */
        fun setStorageFilter(type: StorageType?) {
            _storageFilter.value = type
            Timber.tag("ComposLibraryViewModel").d("Storage filter set to: $type")
        }

        /**
         * Reactive state of comics based on current filter or selected library path.
         * Updates automatically when filter, path, or database changes.
         */
        // Track read comics from database (observes read_progress table)
        val manualReadComics: StateFlow<Set<String>> =
            comicDao
                .observeReadComics()
                .combine(comicDao.observeAllComics()) { readComics, _ ->
                    // Create a set of file paths for comics marked as read
                    readComics.map { it.filePath }.toSet()
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptySet(),
                )

        /**
         * Toggle read status for a comic (for testing/manual marking).
         */
        fun toggleComicReadStatus(comicPath: String) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Find comic by path
                    val comic = comicDao.getComicBookByPath(comicPath)
                    if (comic != null) {
                        // Check current read status
                        val progress = readProgressDao.getProgress(comic.id)
                        val isCurrentlyRead = progress?.isRead == true

                        if (isCurrentlyRead) {
                            markComicAsUnread(comic)
                        } else {
                            markComicAsRead(comic)
                        }
                    } else {
                        Timber.tag("ComposLibraryViewModel").w("Comic not found for path: $comicPath")
                    }
                } catch (e: Exception) {
                    Timber.tag("ComposLibraryViewModel").e(e, "Failed to toggle read status")
                }
            }
        }

        val comics: StateFlow<List<ComicBook>> =
            combine(_filterMode, _selectedLibraryPathId, _storageFilter) { mode, pathId, storageType ->
                Triple(mode, pathId, storageType)
            }.flatMapLatest { (mode, pathId, storageType) ->
                val baseFlow = if (pathId != null) {
                    Timber.tag("ComposLibraryViewModel").d("Comics filter: showing library path $pathId")
                    comicDao.observeComicsByLibraryPath(pathId)
                } else {
                    Timber.tag("ComposLibraryViewModel").d("Comics filter: showing $mode")
                    when (mode) {
                        FilterMode.ALL -> comicDao.observeAllComics()
                        FilterMode.NEW -> comicDao.observeNewComics()
                        FilterMode.UNREAD -> comicDao.observeUnreadComics()
                        FilterMode.READ -> comicDao.observeReadComics()
                        FilterMode.READING -> comicDao.observeReadingComics()
                    }
                }
                // Apply storage type filter if set
                if (storageType != null) {
                    baseFlow.map { list ->
                        list.filter { comic ->
                            when (storageType) {
                                StorageType.SAF, StorageType.LOCAL ->
                                    comic.storageType == StorageType.SAF.value ||
                                        comic.storageType == StorageType.LOCAL.value
                                StorageType.GOOGLE_DRIVE ->
                                    comic.storageType == StorageType.GOOGLE_DRIVE.value
                            }
                        }
                    }
                } else {
                    baseFlow
                }
            }.onEach { comicList ->
                Timber.tag("ComposLibraryViewModel").d("Comics state updated: ${comicList.size} comics")
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        /**
         * Toggle view mode: Cover → Stack → Detail → Cover
         */
        fun toggleViewMode() {
            _viewMode.value =
                when (_viewMode.value) {
                    ViewMode.COVER -> ViewMode.STACK
                    ViewMode.STACK -> ViewMode.DETAIL
                    ViewMode.DETAIL -> ViewMode.COVER
                }
        }

        /**
         * Set library view mode and persist to preferences.
         */
        fun setLibraryViewMode(mode: LibraryViewMode) {
            viewModelScope.launch {
                appPreferencesRepository.setLibraryViewMode(mode)
            }
        }

        /**
         * Set library sort order and persist to preferences.
         */
        fun setLibrarySortOrder(order: SortOrder) {
            viewModelScope.launch {
                appPreferencesRepository.setLibrarySortOrder(order)
            }
        }

        /**
         * Toggle filter mode: All â†’ Unread â†’ Read â†’ Reading â†’ All
         */
        fun toggleFilterMode() {
            _filterMode.value =
                when (_filterMode.value) {
                    FilterMode.ALL -> FilterMode.NEW
                    FilterMode.NEW -> FilterMode.UNREAD
                    FilterMode.UNREAD -> FilterMode.READ
                    FilterMode.READ -> FilterMode.READING
                    FilterMode.READING -> FilterMode.ALL
                }
        }

        /**
         * Add a comic to the library.
         * Runs asynchronously in the IO dispatcher.
         */
        fun addComic(comic: ComicBook) {
            viewModelScope.launch(Dispatchers.IO) {
                comicDao.insertComicBook(comic)
            }
        }

        /**
         * Update an existing comic's metadata (e.g., page count).
         * Runs asynchronously in the IO dispatcher.
         */
        fun updateComic(comic: ComicBook) {
            viewModelScope.launch(Dispatchers.IO) {
                comicDao.updateComicBook(comic)
            }
        }

        /**
         * Delete a comic from the library.
         * Runs asynchronously in the IO dispatcher.
         */
        fun deleteComic(comic: ComicBook) {
            viewModelScope.launch(Dispatchers.IO) {
                comicDao.deleteComicBook(comic)
            }
        }

        /**
         * Import comics from a scanned path.
         * Ensures LibraryPath exists before inserting comics to avoid foreign key constraints.
         */
        /* Commented out due to unresolved references/hallucinated API in LibraryScanner
        fun importComics(
            path: String,
            comics: List<LibraryScanner.ComicFile>,
        ) {
            // ... (implementation removed for build stability as it used non-existent APIs)
        }
        */

        /**
         * Trigger a library refresh (filesystem sync + metadata for changes).
         */
        fun refresh() {
            refreshLibrary(forced = false)
        }

        /**
         * Synchronize all configured library folders with the filesystem.
         *
         * @param forced If true, performs a full "Rescan" (re-parse metadata for ALL files).
         */
        fun refreshLibrary(forced: Boolean = false) {
            viewModelScope.launch {
                Timber.tag("ComposLibraryViewModel").d("User requested refreshLibrary (forced=$forced) - enqueuing job")
                val scanRequest = OneTimeWorkRequestBuilder<LibraryScanWorker>()
                    .setInputData(androidx.work.workDataOf("forced" to forced))
                    .addTag("library-scan")
                    .build()

                WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                    "library-scan",
                    ExistingWorkPolicy.KEEP,
                    scanRequest
                )
            }
        }

        /**
         * Remove database entries for files that no longer exist on disk.
         */
        private suspend fun removeStaleComics() {
            Timber.tag("ComposLibraryViewModel").d("Checking for stale comics...")
            val allComics = comicDao.observeAllComics().first()
            var removedCount = 0

            for (comic in allComics) {
                try {
                    val uri = Uri.parse(comic.filePath)
                    val exists =
                        if (uri.scheme == "content") {
                            // Quick check for SAF URIs
                            val doc =
                                androidx.documentfile.provider.DocumentFile
                                    .fromSingleUri(getApplication(), uri)
                            doc != null && doc.exists()
                        } else {
                            java.io.File(comic.filePath).exists()
                        }

                    if (!exists) {
                        comicDao.deleteComicBook(comic)
                        removedCount++
                    }
                } catch (e: Exception) {
                    Timber.tag("ComposLibraryViewModel").w(e, "Failed to check existence of ${comic.filePath}")
                }
            }

            if (removedCount > 0) {
                Timber.tag("ComposLibraryViewModel").i("Removed $removedCount stale comics from database")
            }
        }

        /**
         * Maintains the list of library folders by adding new paths that are NOT already covered
         * by existing tree permissions or parent folders.
         */
        private suspend fun updateLibraryFolders(path: String) {
            val mainPath = appPreferencesRepository.libraryPath.first()
            if (mainPath == path) return

            // Using SAFUtils to check if permission is already inherited
            val uri = Uri.parse(path)
            val hasInheritedPermission =
                com.comicrack.reader.utils.SAFUtils
                    .hasPermissionFor(getApplication(), uri)

            if (hasInheritedPermission) {
                Timber
                    .tag(
                        "ComposLibraryViewModel",
                    ).d("Path $path has inherited permission, no need to add to additional folders")
                return
            }

            val additional = appPreferencesRepository.libraryAdditionalFolders.first()
            if (!additional.contains(path)) {
                val newList = additional + path
                appPreferencesRepository.setLibraryAdditionalFolders(newList)
                Timber.tag("ComposLibraryViewModel").i("Added $path to additional library folders")
            }
        }


        /**
         * Mark a comic as read by creating or updating its read progress.
         * Sets currentPage to last page and isRead = true.
         */
        fun markComicAsRead(comic: ComicBook) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val progress = readProgressDao.getProgress(comic.id)
                    if (progress != null) {
                        // Update existing progress
                        progress.isRead = true
                        progress.currentPage = comic.pageCount - 1
                        progress.totalPages = comic.pageCount
                        progress.lastReadTime = System.currentTimeMillis()
                        readProgressDao.update(progress)
                    } else {
                        // Create new progress entry
                        val newProgress =
                            com.comicrack.data.database.entities.ReadProgress(
                                comicId = comic.id,
                                currentPage = comic.pageCount - 1,
                                totalPages = comic.pageCount,
                                lastReadTime = System.currentTimeMillis(),
                                isRead = true,
                            )
                        readProgressDao.insert(newProgress)
                    }
                    Timber.tag("ComposLibraryViewModel").d("Marked comic ${comic.id} as read")

                    // Update sync metadata if it's a sync book
                    libraryPathDao.getLibraryPathById(comic.libraryPathId)?.let { path ->
                        syncMetadataService.updateSyncReadStatus(
                            comic = comic,
                            libraryPath = path,
                            isRead = true,
                            currentPage = comic.pageCount - 1,
                            lastPageRead = comic.pageCount - 1
                        )
                    }
                } catch (e: Exception) {
                    Timber.tag("ComposLibraryViewModel").e(e, "Failed to mark comic as read")
                }
            }
        }

        /**
         * Mark a comic as unread by updating or removing its read progress.
         */
        fun markComicAsUnread(comic: ComicBook) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    readProgressDao.markAsUnread(comic.id)
                    Timber.tag("ComposLibraryViewModel").d("Marked comic ${comic.id} as unread")

                    // Update sync metadata if it's a sync book
                    libraryPathDao.getLibraryPathById(comic.libraryPathId)?.let { path ->
                        syncMetadataService.updateSyncReadStatus(
                            comic = comic,
                            libraryPath = path,
                            isRead = false,
                            currentPage = 0,
                            lastPageRead = 0
                        )
                    }
                } catch (e: Exception) {
                    Timber.tag("ComposLibraryViewModel").e(e, "Failed to mark comic as unread")
                }
            }
        }
    }
