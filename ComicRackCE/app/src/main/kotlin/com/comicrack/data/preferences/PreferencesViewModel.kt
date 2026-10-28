package com.comicrack.data.preferences

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.comicrack.core.work.LibraryScanWorker
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.preferences.AppTheme
import com.comicrack.data.preferences.ColorTheme
import com.comicrack.data.preferences.FitMode
import com.comicrack.data.preferences.LibraryViewMode
import com.comicrack.data.preferences.ReadingDirection
import com.comicrack.data.preferences.ReaderMode
import com.comicrack.data.preferences.ReaderTapAction
import com.comicrack.data.preferences.SortOrder
import com.comicrack.data.preferences.ZoneSize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * PreferencesViewModel â€” MVVM ViewModel for preferences management
 *
 * **Phase 2: Domain Layer**
 * Manages preferences state and user actions, bridging AppPreferencesRepository (data) with UI.
 *
 * Responsibilities:
 * - Expose `uiState: StateFlow<PreferencesUiState>` for Compose
 * - Implement preference update actions (setTheme, setViewMode, etc.)
 * - Handle loading and error states
 * - Coordinate repository writes asynchronously
 *
 * Dependency Injection:
 * - Uses Hilt @HiltViewModel for automatic injection
 * - Requires AppPreferencesRepository and Context via @Inject
 *
 * Usage:
 * ```kotlin
 * val viewModel: PreferencesViewModel = viewModel()  // Hilt automatically injects
 * val state by viewModel.uiState.collectAsStateWithLifecycle()
 *
 * // Update preference
 * viewModel.setAppTheme(AppTheme.DARK)
 *
 * // Observe state
 * if (state.isReady) { /* render preferences */ }
 * if (state.error != null) { /* show error */ }
 * ```
 *
 * Architecture:
 * - Collects from AppPreferencesRepository Flows
 * - Maps to PreferencesUiState
 * - Exposes single StateFlow for UI binding
 * - Handles all preference writes via suspend actions
 *
 * Phase 4, Task 4.7: Dependency Injection
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        private val repository: AppPreferencesRepository,
        @param:ApplicationContext private val context: Context,
        private val pathDao: LibraryPathDao,
        val catalog: com.comicrack.settings.catalog.SettingsCatalog,
        private val libraryService: com.comicrack.core.scanner.LibraryService,
    ) : ViewModel() {
        private val tag = "PreferencesViewModel"

        // Mutable state for error tracking during writes
        private val _error = MutableStateFlow<String?>(null)

        /**
         * Current error message produced by preference write operations, or null when there is no error.
         *
         * This is a read-only StateFlow intended for UI to display transient error messages.
         */
        val error: StateFlow<String?> = _error.asStateFlow()

        // Scanning State
        private val _isScanning = MutableStateFlow(false)

        /**
         * Flow that emits whether a library scan is in progress.
         *
         * Emits true while a scan is running and false otherwise.
         */
        val isScanning: Flow<Boolean> =
            libraryService.scanProgress
                .map { progress ->
                    progress !is com.comicrack.core.scanner.ScanProgress.Idle &&
                        progress !is com.comicrack.core.scanner.ScanProgress.Complete
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false,
                )

        /**
         * Main UI state â€” combines all repository flows into single StateFlow.
         *
         * Automatically updates when any preference changes and emits
         * snapshots of [UserPreferences] for Compose consumption.
         *
         * @return a read-only StateFlow of the current [UserPreferences].
         */
        val uiState: StateFlow<UserPreferences> =
            combine(
                repository.allPreferences,
                _isScanning,
            ) { prefs, scanning ->
                prefs.copy(isScanning = scanning)
            }.catch { exception ->
                Timber.tag(tag).e(exception, "Error collecting preferences")
                _error.value = exception.message ?: "Unknown error loading preferences"
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue =
                    UserPreferences(
                        libraryViewMode = LibraryViewMode.GRID,
                        librarySortOrder = SortOrder.TITLE,
                        libraryLayoutColumns = 2,
                        libraryGridSpacing = 8.0f,
                        libraryCardElevation = 4.0f,
                        libraryShowTitles = true,
                        libraryAnimateCards = true,
                        autoScanLibrary = true,
                        recursiveScan = true,
                        rememberReadingPosition = true,
                        useDirectFileAccess = false,
                        readerMode = ReaderMode.PAGE,
                        readerFlashCover = true,
                        readerAskRating = true,
                        readerShowPageNumbers = true,
                        readerKeepScreenOn = true,
                        readerDynamicBackground = true, // Default
                        readerBrightness = 0.5f,
                        readerBrightnessEnabled = true,
                        pageFitMode = FitMode.FIT_PAGE,
                        pageTransitionsEnabled = true,
                        readingDirection = ReadingDirection.LTR,
                        hapticFeedback = true,
                        tapNavigation = true,
                        doubleTapEnabled = true,
                        pinchZoomSensitivity = 1.0f,
                        centerZoneSize = ZoneSize.MEDIUM,
                         tapZoneTopLeft = ReaderTapAction.PREVIOUS_PAGE,
                         tapZoneTopCenter = ReaderTapAction.PREVIOUS_PAGE,
                         tapZoneTopRight = ReaderTapAction.NEXT_PAGE,
                         tapZoneMiddleLeft = ReaderTapAction.PREVIOUS_PAGE,
                         tapZoneCenter = ReaderTapAction.MENU,
                         tapZoneMiddleRight = ReaderTapAction.NEXT_PAGE,
                         tapZoneBottomLeft = ReaderTapAction.PREVIOUS_PAGE,
                         tapZoneBottomCenter = ReaderTapAction.NEXT_PAGE,
                         tapZoneBottomRight = ReaderTapAction.NEXT_PAGE,
                         longTapZoneTopLeft = ReaderTapAction.NONE,
                         longTapZoneTopCenter = ReaderTapAction.NONE,
                         longTapZoneTopRight = ReaderTapAction.NONE,
                         longTapZoneMiddleLeft = ReaderTapAction.NONE,
                         longTapZoneCenter = ReaderTapAction.NONE,
                         longTapZoneMiddleRight = ReaderTapAction.NONE,
                         longTapZoneBottomLeft = ReaderTapAction.NONE,
                         longTapZoneBottomCenter = ReaderTapAction.NONE,
                         longTapZoneBottomRight = ReaderTapAction.NONE,
                         doubleTapZoneTopLeft = ReaderTapAction.NONE,
                         doubleTapZoneTopCenter = ReaderTapAction.NONE,
                         doubleTapZoneTopRight = ReaderTapAction.NONE,
                         doubleTapZoneMiddleLeft = ReaderTapAction.NONE,
                         doubleTapZoneCenter = ReaderTapAction.NONE,
                         doubleTapZoneMiddleRight = ReaderTapAction.NONE,
                         doubleTapZoneBottomLeft = ReaderTapAction.NONE,
                         doubleTapZoneBottomCenter = ReaderTapAction.NONE,
                         doubleTapZoneBottomRight = ReaderTapAction.NONE,
                         navPanelTapForward = ReaderTapAction.NEXT_PAGE,
                         navPanelTapBack = ReaderTapAction.PREVIOUS_PAGE,
                         navPanelTapNextBook = ReaderTapAction.NEXT_COMIC,
                         navPanelTapPrevBook = ReaderTapAction.PREVIOUS_COMIC,
                         navPanelLongThumbnail = ReaderTapAction.NONE,
                         navPanelLongForward = ReaderTapAction.NONE,
                         navPanelLongBack = ReaderTapAction.NONE,
                         navPanelLongNextBook = ReaderTapAction.NONE,
                         navPanelLongPrevBook = ReaderTapAction.NONE,
                         hardwareKeyBindings = HardwareKeyBindings(),
                         volumeButtonsEnabled = true,
                         keyboardEnabled = true,
                         gamepadButtonsEnabled = true,
                         bluetoothButtonsEnabled = true,
                         horizontalSwipeEnabled = true,
                         rtlNavigationEnabled = false,
                         showTapAreas = false,
                        animationSpeed = 1.0f,
                        parallaxEffectEnabled = true,
                        wallpaperColorTheme = ColorTheme.AUTO,
                        lowPowerMode = false,
                        appTheme = AppTheme.SYSTEM,
                        readerSaturation = 1.0f,
                        readerContrast = 1.0f,
                        readerSharpness = 1.0f,
                        readerOpenWithMenu = true,
                        readerShowTapToasts = true,
                        isLoading = false,
                        isScanning = false,
                        error = null,
                    ),
            )

        // ===== Library Preference Actions =====

        /**
         * Update library view mode (Grid or List)
         * Writes asynchronously to DataStore
         */
        fun setLibraryViewMode(mode: LibraryViewMode) {
            viewModelScope.launch {
                try {
                    repository.setLibraryViewMode(mode)
                    _error.value = null
                    Timber.tag(tag).d("Updated libraryViewMode to $mode")
                } catch (e: Exception) {
                    val msg = "Failed to update library view mode: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Update library sort order (Title, Date Added, Last Read)
         * Writes asynchronously to DataStore
         */
        fun setLibrarySortOrder(order: SortOrder) {
            viewModelScope.launch {
                try {
                    repository.setLibrarySortOrder(order)
                    _error.value = null
                    Timber.tag(tag).d("Updated librarySortOrder to $order")
                } catch (e: Exception) {
                    val msg = "Failed to update library sort order: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Phase 3.2: Set library folder path via SAF picker
         * Persists the main library folder URI for comic scanning
         */
        fun setLibraryPath(path: String) {
            viewModelScope.launch {
                try {
                    repository.setLibraryPath(path)

                    // Sync with Room database to ensure scanner finds it
                    ensurePathInDatabase(path)

                    // Ensure new main path is removed from additional folders if present
                    val currentAdditional = uiState.value.libraryAdditionalFolders
                    if (currentAdditional.contains(path)) {
                        val newAdditional = currentAdditional - path
                        repository.setLibraryAdditionalFolders(newAdditional)
                        Timber.tag(tag).d("Removed $path from additional folders as it is now Main")
                    }

                    _error.value = null
                    Timber.tag(tag).d("Updated libraryPath to $path - initiating scan")

                    // Automatically trigger scan after path change
                    initiateLibraryScan()
                } catch (e: Exception) {
                    val msg = "Failed to update library path: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Ensure a path from preferences exists as an active entry in the LibraryPath database.
         */
        private suspend fun ensurePathInDatabase(path: String) {
            val existing = pathDao.getLibraryPathByPath(path)
            if (existing == null) {
                val displayName =
                    com.comicrack.reader.utils.SAFUtils.extractDisplayNameFromPath(path)
                val newLibraryPath = com.comicrack.data.database.entities.LibraryPath(path, displayName)
                pathDao.insertLibraryPath(newLibraryPath)
                Timber.tag(tag).d("Sync: Inserted new library path into DB: $displayName")
            } else if (!existing.isActive) {
                pathDao.reactivateLibraryPath(existing.id)
                Timber.tag(tag).d("Sync: Reactivated existing library path in DB: ${existing.displayName}")
            }
        }

        // ===== Library Scanning Actions =====

        /**
         * Initiate a manual library scan via WorkManager
         *
         * Enqueues a LibraryScanWorker job to run in the background.
         * Uses ExistingWorkPolicy.KEEP to prevent duplicate scans if one is already running.
         *
         * Called when user taps "Scan Library Now" button in settings
         */
        fun initiateLibraryScan() {
            try {
                Timber.tag(tag).d("User initiated library scan - enqueueing WorkManager job")
                val scanRequest =
                    OneTimeWorkRequestBuilder<LibraryScanWorker>()
                        .addTag("library-scan")
                        .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "library-scan",
                    ExistingWorkPolicy.KEEP,
                    scanRequest,
                )

                _error.value = null
                Timber.tag(tag).d("Library scan job enqueued successfully")
            } catch (e: Exception) {
                val msg = "Failed to start library scan: ${e.message}"
                Timber.tag(tag).e(e, msg)
                _error.value = msg
            }
        }

        /**
         * Initiate a targeted library scan for a specific folder path
         */
        fun initiateTargetedLibraryScan(path: String) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val libraryPath = pathDao.getLibraryPathByPath(path)
                    if (libraryPath != null) {
                        try {
                            Timber.tag(tag).d("User initiated targeted scan for $path (ID: ${libraryPath.id})")
                            val scanRequest =
                                OneTimeWorkRequestBuilder<LibraryScanWorker>()
                                    .setInputData(workDataOf("pathId" to libraryPath.id))
                                    .addTag("library-scan")
                                    .build()

                            WorkManager.getInstance(context).enqueueUniqueWork(
                                "library-scan-${libraryPath.id}",
                                ExistingWorkPolicy.REPLACE, // Allow restart for targeted scan
                                scanRequest,
                            )
                        } catch (e: Exception) {
                            throw e
                        }
                    } else {
                        Timber.tag(tag).w("Targeted scan requested for unknown path $path - falling back to full scan")
                        initiateLibraryScan()
                    }
                } catch (e: Exception) {
                    val msg = "Failed to start targeted scan: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Set additional library folders
         */
        fun setLibraryAdditionalFolders(folders: Set<String>) {
            viewModelScope.launch {
                try {
                    // Ensure main library path is not in additional folders
                    val mainPath = uiState.value.libraryPath
                    val filteredFolders =
                        if (mainPath != null) {
                            folders - mainPath
                        } else {
                            folders
                        }

                    repository.setLibraryAdditionalFolders(filteredFolders)

                    // Sync with Room database
                    filteredFolders.forEach { ensurePathInDatabase(it) }

                    _error.value = null
                    Timber.tag(tag).d("Updated additional folders - initiating scan")

                    // Automatically trigger scan after additional folders change
                    initiateLibraryScan()
                } catch (e: Exception) {
                    _error.value = "Failed to update additional folders: ${e.message}"
                }
            }
        }

        // ===== Phase 8 Task 4: Layout Preferences =====

        /**
         * Update library show titles preference
         * Writes asynchronously to DataStore
         */
        fun setLibraryShowTitles(show: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setLibraryShowTitles(show)
                    _error.value = null
                    Timber.tag(tag).d("Updated libraryShowTitles to $show")
                } catch (e: Exception) {
                    val msg = "Failed to update library show titles: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Update library animate cards preference
         * Writes asynchronously to DataStore
         */
        fun setLibraryAnimateCards(animate: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setLibraryAnimateCards(animate)
                    _error.value = null
                    Timber.tag(tag).d("Updated libraryAnimateCards to $animate")
                } catch (e: Exception) {
                    val msg = "Failed to update library animate cards: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        // ===== Reader Preference Actions =====

        /**
         * Update reader mode (Scroll or Page)
         * Writes asynchronously to DataStore
         */
        fun setReaderMode(mode: ReaderMode) {
            viewModelScope.launch {
                try {
                    repository.setReaderMode(mode)
                    _error.value = null
                    Timber.tag(tag).d("Updated readerMode to $mode")
                } catch (e: Exception) {
                    val msg = "Failed to update reader mode: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Toggle flash cover page on reader open
         * Writes asynchronously to DataStore
         */
        fun setReaderFlashCover(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setReaderFlashCover(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated readerFlashCover to $enabled")
                } catch (e: Exception) {
                    val msg = "Failed to update reader flash cover: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Toggle ask for rating after reading
         * Writes asynchronously to DataStore
         */
        fun setReaderAskRating(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setReaderAskRating(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated readerAskRating to $enabled")
                } catch (e: Exception) {
                    val msg = "Failed to update reader ask rating: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Toggle show page numbers in reader
         * Writes asynchronously to DataStore
         */
        fun setReaderShowPageNumbers(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setReaderShowPageNumbers(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated readerShowPageNumbers to $enabled")
                } catch (e: Exception) {
                    val msg = "Failed to update reader show page numbers: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Toggle keep screen on during reading
         * Writes asynchronously to DataStore
         */
        fun setReaderKeepScreenOn(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setReaderKeepScreenOn(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated readerKeepScreenOn to $enabled")
                } catch (e: Exception) {
                    val msg = "Failed to update reader keep screen on: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Toggle dynamic background color analysis
         * Writes asynchronously to DataStore
         */
        fun setReaderDynamicBackground(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setReaderDynamicBackground(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated readerDynamicBackground to $enabled")
                } catch (e: Exception) {
                    val msg = "Failed to update reader dynamic background: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Update reader brightness (0.0 to 1.0)
         * Writes asynchronously to DataStore
         * Auto-clamps to valid range
         */
        fun setReaderBrightness(brightness: Float) {
            viewModelScope.launch {
                try {
                    repository.setReaderBrightness(brightness)
                    _error.value = null
                    Timber.tag(tag).d("Updated readerBrightness to $brightness")
                } catch (e: Exception) {
                    val msg = "Failed to update reader brightness: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Toggle reader brightness override enabled
         * Writes asynchronously to DataStore
         */
        fun setReaderBrightnessEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setReaderBrightnessEnabled(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated readerBrightnessEnabled to $enabled")
                } catch (e: Exception) {
                    val msg = "Failed to update reader brightness enabled: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        // ===== Theme Preference Actions =====

        /**
         * Update app theme (System, Light, Dark)
         * Writes asynchronously to DataStore
         */
        fun setAppTheme(theme: AppTheme) {
            viewModelScope.launch {
                try {
                    repository.setAppTheme(theme)
                    _error.value = null
                    Timber.tag(tag).d("Updated appTheme to $theme")
                } catch (e: Exception) {
                    val msg = "Failed to update app theme: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Toggle Dynamic Color (Material You)
         */
        fun setDynamicColorEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setDynamicColorEnabled(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update dynamic color: ${e.message}"
                }
            }
        }

        // ===== General Preference Actions =====

        fun setAutoScanLibrary(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setAutoScanLibrary(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update auto-scan: ${e.message}"
                }
            }
        }

        fun setRecursiveScan(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setRecursiveScan(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update recursive scan: ${e.message}"
                }
            }
        }

        fun setRememberReadingPosition(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setRememberReadingPosition(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update remember reading position: ${e.message}"
                }
            }
        }

        fun setUseDirectFileAccess(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setUseDirectFileAccess(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update file access mode: ${e.message}"
                }
            }
        }

        // ===== Reader Display Preference Actions =====

        fun setPageFitMode(mode: FitMode) {
            viewModelScope.launch {
                try {
                    repository.setPageFitMode(mode)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update page fit mode: ${e.message}"
                }
            }
        }

        fun setPageTransitionsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setPageTransitionsEnabled(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update page transitions: ${e.message}"
                }
            }
        }

        fun setReadingDirection(direction: ReadingDirection) {
            viewModelScope.launch {
                try {
                    repository.setReadingDirection(direction)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update reading direction: ${e.message}"
                }
            }
        }

        fun setReaderSaturation(value: Float) {
            viewModelScope.launch { repository.setReaderSaturation(value) }
        }

        fun setReaderContrast(value: Float) {
            viewModelScope.launch { repository.setReaderContrast(value) }
        }

        fun setReaderSharpness(value: Float) {
            viewModelScope.launch { repository.setReaderSharpness(value) }
        }

        fun setReaderOpenWithMenu(enabled: Boolean) {
            viewModelScope.launch { repository.setReaderOpenWithMenu(enabled) }
        }

        fun setReaderShowTapToasts(enabled: Boolean) {
            viewModelScope.launch { repository.setReaderShowTapToasts(enabled) }
        }

        // ===== Controls Preference Actions =====

        fun setHapticFeedback(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setHapticFeedback(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update haptic feedback: ${e.message}"
                }
            }
        }

        fun setTapNavigation(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setTapNavigation(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap navigation: ${e.message}"
                }
            }
        }

        fun setDoubleTapEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapEnabled(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double-tap: ${e.message}"
                }
            }
        }

        fun setPinchZoomSensitivity(sensitivity: Float) {
            viewModelScope.launch {
                try {
                    repository.setPinchZoomSensitivity(sensitivity)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update pinch zoom: ${e.message}"
                }
            }
        }

        fun setCenterZoneSize(size: ZoneSize) {
            viewModelScope.launch {
                try {
                    repository.setCenterZoneSize(size)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update center zone size: ${e.message}"
                }
            }
        }

        // ===== Tap Zone Actions (A1-A9) =====

        fun setTapZoneTopLeft(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setTapZoneTopLeft(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap zone Top Left: ${e.message}"
                }
            }
        }

        fun setTapZoneTopCenter(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setTapZoneTopCenter(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap zone Top Center: ${e.message}"
                }
            }
        }

        fun setTapZoneTopRight(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setTapZoneTopRight(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap zone Top Right: ${e.message}"
                }
            }
        }

        fun setTapZoneMiddleLeft(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setTapZoneMiddleLeft(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap zone Middle Left: ${e.message}"
                }
            }
        }

        fun setTapZoneCenter(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setTapZoneCenter(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap zone Center: ${e.message}"
                }
            }
        }

        fun setTapZoneMiddleRight(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setTapZoneMiddleRight(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap zone Middle Right: ${e.message}"
                }
            }
        }

        fun setTapZoneBottomLeft(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setTapZoneBottomLeft(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap zone Bottom Left: ${e.message}"
                }
            }
        }

        fun setTapZoneBottomCenter(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setTapZoneBottomCenter(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap zone Bottom Center: ${e.message}"
                }
            }
        }

        fun setTapZoneBottomRight(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setTapZoneBottomRight(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update tap zone Bottom Right: ${e.message}"
                }
            }
        }

        // ===== Long Tap Zone Actions (L1-L9 / A1-A9) =====

        fun setLongTapZoneTopLeft(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setLongTapZoneTopLeft(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update long tap zone Top Left: ${e.message}"
                }
            }
        }

        fun setLongTapZoneTopCenter(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setLongTapZoneTopCenter(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update long tap zone Top Center: ${e.message}"
                }
            }
        }

        fun setLongTapZoneTopRight(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setLongTapZoneTopRight(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update long tap zone Top Right: ${e.message}"
                }
            }
        }

        fun setLongTapZoneMiddleLeft(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setLongTapZoneMiddleLeft(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update long tap zone Middle Left: ${e.message}"
                }
            }
        }

        fun setLongTapZoneCenter(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setLongTapZoneCenter(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update long tap zone Center: ${e.message}"
                }
            }
        }

        fun setLongTapZoneMiddleRight(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setLongTapZoneMiddleRight(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update long tap zone Middle Right: ${e.message}"
                }
            }
        }

        fun setLongTapZoneBottomLeft(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setLongTapZoneBottomLeft(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update long tap zone Bottom Left: ${e.message}"
                }
            }
        }

        fun setLongTapZoneBottomCenter(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setLongTapZoneBottomCenter(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update long tap zone Bottom Center: ${e.message}"
                }
            }
        }

        fun setLongTapZoneBottomRight(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setLongTapZoneBottomRight(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update long tap zone Bottom Right: ${e.message}"
                }
            }
        }


        // ===== Double Tap Zone Actions (D1-D9 / A1-A9) =====

        fun setDoubleTapZoneTopLeft(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapZoneTopLeft(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double tap zone Top Left: ${e.message}"
                }
            }
        }

        fun setDoubleTapZoneTopCenter(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapZoneTopCenter(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double tap zone Top Center: ${e.message}"
                }
            }
        }

        fun setDoubleTapZoneTopRight(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapZoneTopRight(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double tap zone Top Right: ${e.message}"
                }
            }
        }

        fun setDoubleTapZoneMiddleLeft(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapZoneMiddleLeft(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double tap zone Middle Left: ${e.message}"
                }
            }
        }

        fun setDoubleTapZoneCenter(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapZoneCenter(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double tap zone Center: ${e.message}"
                }
            }
        }

        fun setDoubleTapZoneMiddleRight(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapZoneMiddleRight(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double tap zone Middle Right: ${e.message}"
                }
            }
        }

        fun setDoubleTapZoneBottomLeft(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapZoneBottomLeft(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double tap zone Bottom Left: ${e.message}"
                }
            }
        }

        fun setDoubleTapZoneBottomCenter(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapZoneBottomCenter(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double tap zone Bottom Center: ${e.message}"
                }
            }
        }

        fun setDoubleTapZoneBottomRight(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setDoubleTapZoneBottomRight(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update double tap zone Bottom Right: ${e.message}"
                }
            }
        }

        // ===== Wallpaper Preference Actions =====

        fun setAnimationSpeed(speed: Float) {
            viewModelScope.launch {
                try {
                    repository.setAnimationSpeed(speed)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update animation speed: ${e.message}"
                }
            }
        }

        fun setParallaxEffectEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setParallaxEffectEnabled(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update parallax effect: ${e.message}"
                }
            }
        }

        fun setWallpaperColorTheme(theme: ColorTheme) {
            viewModelScope.launch {
                try {
                    repository.setWallpaperColorTheme(theme)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update wallpaper color theme: ${e.message}"
                }
            }
        }

        fun setLowPowerMode(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setLowPowerMode(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update low power mode: ${e.message}"
                }
            }
        }

        // ===== Navigation Panel Actions =====

        fun setNavPanelTapForward(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setNavPanelTapForward(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update nav panel forward tap: ${e.message}"
                }
            }
        }

        fun setNavPanelLongForward(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setNavPanelLongForward(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update nav panel forward long press: ${e.message}"
                }
            }
        }

        fun setNavPanelTapBack(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setNavPanelTapBack(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update nav panel back tap: ${e.message}"
                }
            }
        }

        fun setNavPanelLongBack(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setNavPanelLongBack(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update nav panel back long press: ${e.message}"
                }
            }
        }

        fun setNavPanelTapNextBook(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setNavPanelTapNextBook(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update nav panel next book tap: ${e.message}"
                }
            }
        }

        fun setNavPanelLongNextBook(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setNavPanelLongNextBook(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update nav panel next book long press: ${e.message}"
                }
            }
        }

        fun setNavPanelTapPrevBook(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setNavPanelTapPrevBook(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update nav panel prev book tap: ${e.message}"
                }
            }
        }

        fun setNavPanelLongPrevBook(action: ReaderTapAction) {
            viewModelScope.launch {
                try {
                    repository.setNavPanelLongPrevBook(action)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update nav panel prev book long press: ${e.message}"
                }
            }
        }

        // ===== Hardware Key Actions =====

        private fun updateHardwareBinding(update: (HardwareKeyBindings) -> HardwareKeyBindings) {
            viewModelScope.launch {
                try {
                    val current = uiState.value.hardwareKeyBindings
                    val newBindings = update(current)
                    repository.setHardwareKeyBindings(newBindings)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update hardware binding: ${e.message}"
                }
            }
        }

        // Standard Keyboard Keys
        fun setKeyboardUp(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(keyboardBindings = it.keyboardBindings + (android.view.KeyEvent.KEYCODE_DPAD_UP to action))
            }

        fun setKeyboardDown(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(keyboardBindings = it.keyboardBindings + (android.view.KeyEvent.KEYCODE_DPAD_DOWN to action))
            }

        fun setKeyboardLeft(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(keyboardBindings = it.keyboardBindings + (android.view.KeyEvent.KEYCODE_DPAD_LEFT to action))
            }

        fun setKeyboardRight(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(keyboardBindings = it.keyboardBindings + (android.view.KeyEvent.KEYCODE_DPAD_RIGHT to action))
            }

        fun setKeyboardSpace(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(keyboardBindings = it.keyboardBindings + (android.view.KeyEvent.KEYCODE_SPACE to action))
            }

        fun setKeyboardPageUp(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(keyboardBindings = it.keyboardBindings + (android.view.KeyEvent.KEYCODE_PAGE_UP to action))
            }

        fun setKeyboardPageDown(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(keyboardBindings = it.keyboardBindings + (android.view.KeyEvent.KEYCODE_PAGE_DOWN to action))
            }

        fun setKeyboardEscape(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(keyboardBindings = it.keyboardBindings + (android.view.KeyEvent.KEYCODE_ESCAPE to action))
            }

        // Gamepad Keys
        fun setGamepadUp(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(gamepadBindings = it.gamepadBindings + (android.view.KeyEvent.KEYCODE_DPAD_UP to action))
            }

        fun setGamepadDown(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(gamepadBindings = it.gamepadBindings + (android.view.KeyEvent.KEYCODE_DPAD_DOWN to action))
            }

        fun setGamepadLeft(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(gamepadBindings = it.gamepadBindings + (android.view.KeyEvent.KEYCODE_DPAD_LEFT to action))
            }

        fun setGamepadRight(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(gamepadBindings = it.gamepadBindings + (android.view.KeyEvent.KEYCODE_DPAD_RIGHT to action))
            }

        fun setGamepadA(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(gamepadBindings = it.gamepadBindings + (android.view.KeyEvent.KEYCODE_BUTTON_A to action))
            }

        fun setGamepadB(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(gamepadBindings = it.gamepadBindings + (android.view.KeyEvent.KEYCODE_BUTTON_B to action))
            }

        // Volume Keys (Special property in data class)
        fun setKeyboardVolumeUp(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(volumeUpAction = action)
            }

        fun setKeyboardVolumeUpLongPress(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(volumeUpLongPressAction = action)
            }

        fun setKeyboardVolumeDown(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(volumeDownAction = action)
            }

        fun setKeyboardVolumeDownLongPress(action: ReaderTapAction) =
            updateHardwareBinding {
                it.copy(volumeDownLongPressAction = action)
            }

        fun setGenericKeyBinding(
            keyCode: Int,
            action: ReaderTapAction,
            isGamepad: Boolean,
        ) = updateHardwareBinding {
            if (isGamepad) {
                it.copy(gamepadBindings = it.gamepadBindings + (keyCode to action))
            } else {
                it.copy(keyboardBindings = it.keyboardBindings + (keyCode to action))
            }
        }

        // ===== Helper Options Actions =====

        fun setShowTapAreas(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setShowTapAreas(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update show tap areas: ${e.message}"
                }
            }
        }

        fun setRtlNavigationEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setRtlNavigationEnabled(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update rtl navigation enabled: ${e.message}"
                }
            }
        }

        fun setHorizontalSwipeEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setHorizontalSwipeEnabled(enabled)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to update horizontal swipe: ${e.message}"
                }
            }
        }

        // ===== Error Management =====

        /**
         * Clear error state (call after displaying error to user)
         */
        fun clearError() {
            _error.value = null
        }

        // ===== Generic Setting Updates (Phase 2.1) =====

        /**
         * Generic method to update any setting from the new SettingsCatalog.
         *
         * Maps setting IDs (from SettingsCatalog) to the appropriate repository update method.
         * Handles type validation and conversion for Slider, Toggle, Choice types.
         *
         * **Phase 2.1**: Basic type support (Toggle, Slider, Choice)
         * **Phase 2.2**: Extended support for Complex types (TapZones, SAF paths, etc.)
         *
         * @param settingId The setting ID from SettingsCatalog (e.g., "rtl_mode", "brightness_level")
         * @param value The new value to set
         * @throws IllegalArgumentException if value type doesn't match expected type for this setting
         */
        fun updateSetting(
            settingId: String,
            value: Any,
        ) {
            viewModelScope.launch {
                try {
                    when (settingId) {
                        // General Settings
                        "rtl_mode" -> {
                            require(value is Boolean) { "rtl_mode requires Boolean value" }
                            repository.setReadingDirection(
                                if (value) {
                                    ReadingDirection.RTL
                                } else {
                                    ReadingDirection.LTR
                                },
                            )
                        }

                        "language_selection" -> {
                            // Phase 2.0: Just log, Phase 3+ will implement in-app language override
                            Timber.tag(tag).d("Language selection: $value (Phase 3+ for in-app override)")
                        }

                        "dark_mode" -> {
                            require(value is Boolean) { "dark_mode requires Boolean value" }
                            repository.setAppTheme(
                                if (value) {
                                    AppTheme.DARK
                                } else {
                                    AppTheme.LIGHT
                                },
                            )
                        }

                        // Reading Settings
                        "page_transition_effect" -> {
                            require(value is String) { "page_transition_effect requires String value" }
                            Timber.tag(tag).d("Page transition effect: $value (custom component Phase 2.2)")
                        }

                        "zoom_mode" -> {
                            require(value is String) { "zoom_mode requires String value" }
                            when (value) {
                                "Fit Page" -> repository.setPageFitMode(FitMode.FIT_PAGE)
                                "Fit Width" -> repository.setPageFitMode(FitMode.FIT_WIDTH)
                                // Other zoom modes mapped to appropriate fit mode
                            }
                        }

                        "double_tap_zoom" -> {
                            require(value is Boolean) { "double_tap_zoom requires Boolean value" }
                            repository.setDoubleTapEnabled(value)
                        }

                        "pinch_zoom" -> {
                            require(value is Boolean) { "pinch_zoom requires Boolean value" }
                            // Map to existing preference if available
                            Timber.tag(tag).d("Pinch zoom: $value")
                        }

                        "swipe_navigation" -> {
                            require(value is Boolean) { "swipe_navigation requires Boolean value" }
                            repository.setTapNavigation(value)
                        }

                        "swipe_direction" -> {
                            require(value is String) { "swipe_direction requires String value" }
                            when (value) {
                                "Left-to-Right" ->
                                    repository.setReadingDirection(
                                        ReadingDirection.LTR,
                                    )
                                "Right-to-Left" ->
                                    repository.setReadingDirection(
                                        ReadingDirection.RTL,
                                    )
                                "Auto" -> Timber.tag(tag).d("Auto swipe direction: $value")
                            }
                        }

                        "brightness_level" -> {
                            val brightness =
                                when (value) {
                                    is Number -> value.toFloat()
                                    is String ->
                                        value.toFloatOrNull()
                                            ?: throw IllegalArgumentException(
                                                "brightness_level requires valid numeric string",
                                            )
                                    else -> throw IllegalArgumentException("brightness_level requires numeric value")
                                }
                            repository.setReaderBrightness(brightness / 100f) // Normalize 0-100 to 0-1
                        }

                        "contrast_level" -> {
                            require(value is Number) { "contrast_level requires numeric value" }
                            Timber.tag(tag).d("Contrast level: $value (custom component Phase 2.2)")
                        }

                        "reader_saturation" -> {
                            require(value is Number) { "reader_saturation requires numeric value" }
                            repository.setReaderSaturation(value.toFloat() / 100f)
                        }

                        "reader_sharpness" -> {
                            require(value is Number) { "reader_sharpness requires numeric value" }
                            repository.setReaderSharpness(value.toFloat() / 100f)
                        }

                        "reader_open_with_menu" -> {
                            require(value is Boolean) { "reader_open_with_menu requires Boolean value" }
                            repository.setReaderOpenWithMenu(value)
                        }

                        "reader_show_tap_toasts" -> {
                            require(value is Boolean) { "reader_show_tap_toasts requires Boolean value" }
                            repository.setReaderShowTapToasts(value)
                        }

                        "margin_size" -> {
                            require(value is Number) { "margin_size requires numeric value" }
                            Timber.tag(tag).d("Margin size: $value (custom component Phase 2.2)")
                        }

                        "animate_transitions" -> {
                            require(value is Boolean) { "animate_transitions requires Boolean value" }
                            repository.setPageTransitionsEnabled(value)
                        }

                        "auto_scroll" -> {
                            require(value is Boolean) { "auto_scroll requires Boolean value" }
                            Timber.tag(tag).d("Auto scroll: $value (custom component Phase 2.2)")
                        }

                        "auto_scroll_speed" -> {
                            require(value is Number) { "auto_scroll_speed requires numeric value" }
                            Timber.tag(tag).d("Auto scroll speed: $value (custom component Phase 2.2)")
                        }

                        // invert_colors removed

                        // Controls Settings
                        "tap_zone_left_action" -> {
                            require(value is String) { "tap_zone_left_action requires String value" }
                            Timber.tag(tag).d("Tap zone left: $value (custom component Phase 2.2)")
                        }

                        "haptic_feedback_enabled" -> {
                            require(value is Boolean) { "haptic_feedback_enabled requires Boolean value" }
                            Timber.tag(tag).d("Haptic feedback: $value")
                        }

                        "volume_buttons_navigate" -> {
                            require(value is Boolean) { "volume_buttons_navigate requires Boolean value" }
                            Timber.tag(tag).d("Volume buttons navigate: $value")
                        }

                        "orientation_lock" -> {
                            require(value is String) { "orientation_lock requires String value" }
                            Timber.tag(tag).d("Orientation lock: $value (custom component Phase 2.2)")
                        }

                        // Wallpaper Settings
                        "wallpaper_enabled" -> {
                            require(value is Boolean) { "wallpaper_enabled requires Boolean value" }
                            Timber.tag(tag).d("Wallpaper enabled: $value")
                        }

                        "wallpaper_opacity" -> {
                            require(value is Number) { "wallpaper_opacity requires numeric value" }
                            Timber.tag(tag).d("Wallpaper opacity: $value (custom component Phase 2.2)")
                        }

                        else -> {
                            Timber.tag(tag).w("Unknown setting ID: $settingId")
                            _error.value = "Unknown setting: $settingId"
                        }
                    }

                    // Clear error on success
                    _error.value = null
                    Timber.tag(tag).d("Updated setting $settingId to $value")
                } catch (e: IllegalArgumentException) {
                    val msg = "Invalid value for $settingId: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                } catch (e: Exception) {
                    val msg = "Failed to update $settingId: ${e.message}"
                    Timber.tag(tag).e(e, msg)
                    _error.value = msg
                }
            }
        }

        /**
         * Helper to get the current value of a setting from the UserPreferences snapshot.
         *
         * Maps setting IDs from the catalog to properties in UserPreferences.
         *
         * @param setting The Setting object to get the value for
         * @param preferences The current UserPreferences snapshot
         * @return The value of the setting, or the default value if no mapping exists
         */
        fun getValueForSetting(
            setting: com.comicrack.settings.catalog.Setting,
            preferences: UserPreferences,
        ): Any? =
            when (setting.id) {
                "rtl_mode" -> preferences.readingDirection == ReadingDirection.RTL
                "dark_mode" -> preferences.appTheme == AppTheme.DARK
                "zoom_mode" ->
                    when (preferences.pageFitMode) {
                        FitMode.FIT_PAGE -> "Fit Page"
                        FitMode.FIT_WIDTH -> "Fit Width"
                        else -> "Fit Page"
                    }
                "double_tap_zoom" -> preferences.doubleTapEnabled
                "swipe_navigation" -> preferences.tapNavigation
                "brightness_level" -> preferences.readerBrightness * 100f
                "reader_saturation" -> preferences.readerSaturation * 100f
                "reader_sharpness" -> preferences.readerSharpness * 100f
                "reader_open_with_menu" -> preferences.readerOpenWithMenu
                "reader_show_tap_toasts" -> preferences.readerShowTapToasts
                // Fall back to catalog default if not explicitly mapped
                else -> setting.defaultValue
            }

        // ===== Hardware Button Enable/Disable =====

        fun setVolumeButtonsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setVolumeButtonsEnabled(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated volumeButtonsEnabled to $enabled")
                } catch (e: Exception) {
                    _error.value = "Failed to update volume buttons: ${e.message}"
                }
            }
        }

        fun setKeyboardEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setKeyboardEnabled(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated keyboardEnabled to $enabled")
                } catch (e: Exception) {
                    _error.value = "Failed to update keyboard: ${e.message}"
                }
            }
        }

        fun setGamepadButtonsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setGamepadButtonsEnabled(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated gamepadButtonsEnabled to $enabled")
                } catch (e: Exception) {
                    _error.value = "Failed to update gamepad buttons: ${e.message}"
                }
            }
        }

        fun setBluetoothButtonsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    repository.setBluetoothButtonsEnabled(enabled)
                    _error.value = null
                    Timber.tag(tag).d("Updated bluetoothButtonsEnabled to $enabled")
                } catch (e: Exception) {
                    _error.value = "Failed to update bluetooth buttons: ${e.message}"
                }
            }
        }

        // ===== Tap Zone Helper Logic =====

        /**
         * Action type for a tap zone event.
         *
         * - [SINGLE_TAP]: Single tap event
         * - [DOUBLE_TAP]: Double tap event
         * - [LONG_TAP]: Long press event
         */
        enum class ActionType { SINGLE_TAP, DOUBLE_TAP, LONG_TAP }

        /**
         * Returns the configured action for a reader tap zone.
         *
         * @param state the current [UserPreferences] snapshot to read from
         * @param zoneIndex index of the zone (0..8)
         * @param type the [ActionType] (single/double/long)
         * @return the configured [ReaderTapAction] for the given zone and type
         */
        fun getActionForZone(
            state: UserPreferences,
            zoneIndex: Int,
            type: ActionType,
        ): ReaderTapAction =
            when (type) {
                ActionType.SINGLE_TAP ->
                    when (zoneIndex) {
                        0 -> state.tapZoneTopLeft
                        1 -> state.tapZoneTopCenter
                        2 -> state.tapZoneTopRight
                        3 -> state.tapZoneMiddleLeft
                        4 -> state.tapZoneCenter
                        5 -> state.tapZoneMiddleRight
                        6 -> state.tapZoneBottomLeft
                        7 -> state.tapZoneBottomCenter
                        8 -> state.tapZoneBottomRight
                        else -> ReaderTapAction.NONE
                    }
                ActionType.DOUBLE_TAP ->
                    when (zoneIndex) {
                        0 -> state.doubleTapZoneTopLeft
                        1 -> state.doubleTapZoneTopCenter
                        2 -> state.doubleTapZoneTopRight
                        3 -> state.doubleTapZoneMiddleLeft
                        4 -> state.doubleTapZoneCenter
                        5 -> state.doubleTapZoneMiddleRight
                        6 -> state.doubleTapZoneBottomLeft
                        7 -> state.doubleTapZoneBottomCenter
                        8 -> state.doubleTapZoneBottomRight
                        else -> ReaderTapAction.NONE
                    }
                ActionType.LONG_TAP ->
                    when (zoneIndex) {
                        0 -> state.longTapZoneTopLeft
                        1 -> state.longTapZoneTopCenter
                        2 -> state.longTapZoneTopRight
                        3 -> state.longTapZoneMiddleLeft
                        4 -> state.longTapZoneCenter
                        5 -> state.longTapZoneMiddleRight
                        6 -> state.longTapZoneBottomLeft
                        7 -> state.longTapZoneBottomCenter
                        8 -> state.longTapZoneBottomRight
                        else -> ReaderTapAction.NONE
                    }
            }

        /**
         * Updates the action configured for a reader tap zone.
         *
         * Maps the (zoneIndex, type) pair to the appropriate setter.
         *
         * @param zoneIndex index of the zone (0..8)
         * @param type the [ActionType] indicating single/double/long tap
         * @param action new action to set
         */
        fun updateAction(
            zoneIndex: Int,
            type: ActionType,
            action: ReaderTapAction,
        ) {
            when (type) {
                ActionType.SINGLE_TAP ->
                    when (zoneIndex) {
                        0 -> setTapZoneTopLeft(action)
                        1 -> setTapZoneTopCenter(action)
                        2 -> setTapZoneTopRight(action)
                        3 -> setTapZoneMiddleLeft(action)
                        4 -> setTapZoneCenter(action)
                        5 -> setTapZoneMiddleRight(action)
                        6 -> setTapZoneBottomLeft(action)
                        7 -> setTapZoneBottomCenter(action)
                        8 -> setTapZoneBottomRight(action)
                    }
                ActionType.DOUBLE_TAP ->
                    when (zoneIndex) {
                        0 -> setDoubleTapZoneTopLeft(action)
                        1 -> setDoubleTapZoneTopCenter(action)
                        2 -> setDoubleTapZoneTopRight(action)
                        3 -> setDoubleTapZoneMiddleLeft(action)
                        4 -> setDoubleTapZoneCenter(action)
                        5 -> setDoubleTapZoneMiddleRight(action)
                        6 -> setDoubleTapZoneBottomLeft(action)
                        7 -> setDoubleTapZoneBottomCenter(action)
                        8 -> setDoubleTapZoneBottomRight(action)
                    }
                ActionType.LONG_TAP ->
                    when (zoneIndex) {
                        0 -> setLongTapZoneTopLeft(action)
                        1 -> setLongTapZoneTopCenter(action)
                        2 -> setLongTapZoneTopRight(action)
                        3 -> setLongTapZoneMiddleLeft(action)
                        4 -> setLongTapZoneCenter(action)
                        5 -> setLongTapZoneMiddleRight(action)
                        6 -> setLongTapZoneBottomLeft(action)
                        7 -> setLongTapZoneBottomCenter(action)
                        8 -> setLongTapZoneBottomRight(action)
                    }
            }
        }

        /**
         * Add a query to recent searches.
         */
        fun addRecentSearch(query: String) {
            viewModelScope.launch {
                repository.addRecentSearch(query)
            }
        }

        /**
         * Remove a query from recent searches.
         */
        fun removeRecentSearch(query: String) {
            viewModelScope.launch {
                repository.removeRecentSearch(query)
            }
        }

        /**
         * Clear all recent searches.
         */
        fun clearRecentSearches() {
            viewModelScope.launch {
                repository.clearRecentSearches()
            }
        }
    }
