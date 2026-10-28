package com.comicrack.reader

/**
 * Activity that hosts the comic reader and coordinates reader UI and lifecycle.
 *
 * Responsibilities:
 * - Initialize reader ViewModel and provide UI glue for overlays, menus and controls
 * - Ensure any foreground work or wakelocks are released on pause/stop
 * - Delegate long-running IO (preloading, cache management) to background threads
 *   and avoid heavy work on the UI thread
 *
 * Test notes:
 * - Add integration tests that validate activity startup with different Intent data,
 *   orientation changes, and process death/resume behavior for reading position.
 */

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.R
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.FitMode
import com.comicrack.data.preferences.HardwareKeyBindings
import com.comicrack.data.preferences.ReadingDirection
import com.comicrack.data.preferences.ReaderTapAction
import com.comicrack.reader.cache.BitmapCache
import com.comicrack.reader.cache.TempFileManager
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.di.ReaderDependencies
import com.comicrack.reader.data.PlaceholderPageProvider
import com.comicrack.reader.data.archive.ArchiveProviderFactory
import com.comicrack.reader.data.archive.UnsupportedFormatException
import com.comicrack.reader.gesture.ReaderTapActionHandler
import com.comicrack.reader.gesture.TouchZone
import com.comicrack.reader.gesture.TouchZoneDetector
import com.comicrack.reader.gesture.GestureLayer
import com.comicrack.reader.input.ReaderInputController
import com.comicrack.reader.ui.ModernReaderOverlay
import com.comicrack.reader.ui.ReaderPageNumberDisplay
import com.comicrack.ui.theme.ComicRackTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Activity that hosts the comic reader and coordinates reader UI and lifecycle.
 *
 * Responsibilities:
 * - Initialize and expose the reader [ComicViewerViewModel] for UI components.
 * - Manage activity lifecycle, window flags (brightness / keep-screen-on), and
 *   temporary file cleanup.
 * - Provide hooks for hardware key handling and tap/gesture wiring.
 *
 * See also: [ComicViewerScreen], [ReaderTapActionHandler].
 */
@AndroidEntryPoint
class ComicViewerActivity : ComponentActivity() {
    @Inject
    lateinit var readerDependencies: ReaderDependencies

    // Phase 8: State for hardware key handling (public for composable access)
    var currentComicViewModel: ComicViewerViewModel? = null
    var currentKeyBindings: HardwareKeyBindings = HardwareKeyBindings()
    var readerInputController: ReaderInputController? = null
    var currentPageIndex: Int = 0
    var totalPages: Int = 0
    var tapActionHandler: ReaderTapActionHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set window background to black immediately to prevent white flash on transition
        window.setBackgroundDrawableResource(R.color.black)

        // Phase S3-03: Use WindowCompat for immersive mode management
        androidx.core.view.WindowCompat
            .setDecorFitsSystemWindows(window, false)


        val intentData = intent.data

        // Initial parsing from filename (best guess)
        var initialSeries = intent.getStringExtra("seriesName")
        var initialNumber = intent.getStringExtra("issueNumber")
        val fileName = intentData?.lastPathSegment

        if (initialSeries == null && fileName != null) {
            val cleanName = fileName.substringBeforeLast(".")
            val regex = Regex("""(.+?)(?:\s+#?(\d+(?:\.\d+)?))?$""")
            val match = regex.find(cleanName)
            if (match != null && match.groupValues[2].isNotEmpty()) {
                initialSeries = match.groupValues[1].trim()
                initialNumber = match.groupValues[2]
            } else {
                initialSeries = cleanName
                if (initialNumber == null) initialNumber = "1"
            }
        }

        val finalInitialSeries = initialSeries ?: "Unknown Series"
        val finalInitialNumber = initialNumber ?: "1"
        val archivePath = intent.getStringExtra("archivePath") ?: intentData?.toString()
        val comicId = intent.getLongExtra("COMIC_ID", -1L)

        setContent {
            val seriesNameState = remember { mutableStateOf(finalInitialSeries) }
            val issueNumberState = remember { mutableStateOf(finalInitialNumber) }

            // Metadata is now handled by the ViewModel via loadComicMetadata
            // which is triggered in ComicViewerScreen or on startup

            val seriesName by seriesNameState
            val issueNumber by issueNumberState

            ComicRackTheme(isDarkMode = isSystemInDarkTheme()) {
                ComicViewerScreen(
                    seriesName = seriesName,
                    issueNumber = issueNumber,
                    archivePath = archivePath,
                    comicId = comicId,
                    preferencesRepository = readerDependencies.preferencesRepository,
                    tempFileManager = readerDependencies.tempFileManager,
                )
            }
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        // Phase 10: Use ReaderInputController
        if (readerInputController?.onKeyDown(keyCode, event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (readerInputController?.onKeyUp(keyCode, event) == true) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyLongPress(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (readerInputController?.onKeyLongPress(keyCode, event) == true) return true
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        readerDependencies.tempFileManager.cleanup()
    }

    override fun onDestroy() {
        super.onDestroy()
        readerDependencies.tempFileManager.cleanup()
    }
}

@Composable
fun ComicViewerScreen(
    seriesName: String = "Unknown Series",
    issueNumber: String = "1",
    archivePath: String? = null,
    comicId: Long = -1L,
    modifier: Modifier = Modifier,
    preferencesRepository: AppPreferencesRepository,
    tempFileManager: TempFileManager,
) {
    val context = LocalContext.current

    // Create ViewModel - Injected by Hilt
    val viewModelInstance: ComicViewerViewModel = viewModel(key = archivePath)

    // Toast management (Themed overlay instead of native Toast)
    var activeToastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeToastMessage) {
        if (activeToastMessage != null) {
            kotlinx.coroutines.delay(1500)
            activeToastMessage = null
        }
    }

    // Observe Preferences from ViewModel
    val readerBrightness by viewModelInstance.readerBrightness.collectAsState()

    val readerBrightnessEnabled by viewModelInstance.readerBrightnessEnabled.collectAsState()
    val pageFitMode by viewModelInstance.pageFitMode.collectAsState()
    val readerKeepScreenOn by viewModelInstance.readerKeepScreenOn.collectAsState()

    // Other Reader Preferences
    val readingDirection by viewModelInstance.readingDirection.collectAsState()
    val readerOpenWithMenuCollected by viewModelInstance.readerOpenWithMenu.collectAsState()
    val readerShowTapToasts by viewModelInstance.readerShowTapToasts.collectAsState()
    val readerShowPageNumbers by viewModelInstance.readerShowPageNumbers.collectAsState()

    // Control preferences
    val tapNavigation by preferencesRepository.tapNavigation.collectAsState(true)
    val hapticFeedback by preferencesRepository.hapticFeedback.collectAsState(true)
    val showTapAreas by preferencesRepository.showTapAreas.collectAsState(false)
    val rtlNavigationEnabled by preferencesRepository.rtlNavigationEnabled.collectAsState(false)

    // Tap zone action mappings
    val tapZoneTopLeft by preferencesRepository.tapZoneTopLeft.collectAsState(
        ReaderTapAction.PREVIOUS_PAGE,
    )
    val tapZoneTopCenter by preferencesRepository.tapZoneTopCenter.collectAsState(ReaderTapAction.NONE)
    val tapZoneTopRight by preferencesRepository.tapZoneTopRight.collectAsState(
        ReaderTapAction.NEXT_PAGE,
    )
    val tapZoneMiddleLeft by preferencesRepository.tapZoneMiddleLeft.collectAsState(
        ReaderTapAction.PREVIOUS_PAGE,
    )
    val tapZoneCenter by preferencesRepository.tapZoneCenter.collectAsState(
        ReaderTapAction.MENU,
    )
    val tapZoneMiddleRight by preferencesRepository.tapZoneMiddleRight.collectAsState(
        ReaderTapAction.NEXT_PAGE,
    )
    val tapZoneBottomLeft by preferencesRepository.tapZoneBottomLeft.collectAsState(
        ReaderTapAction.PREVIOUS_PAGE,
    )
    val tapZoneBottomCenter by preferencesRepository.tapZoneBottomCenter.collectAsState(
        ReaderTapAction.NEXT_PAGE,
    )
    val tapZoneBottomRight by preferencesRepository.tapZoneBottomRight.collectAsState(
        ReaderTapAction.NEXT_PAGE,
    )

    val longTapZoneTopLeft by preferencesRepository.longTapZoneTopLeft.collectAsState(
        ReaderTapAction.NONE,
    )
    val longTapZoneTopCenter by preferencesRepository.longTapZoneTopCenter.collectAsState(
        ReaderTapAction.GOTO_PAGE,
    )
    val longTapZoneTopRight by preferencesRepository.longTapZoneTopRight.collectAsState(
        ReaderTapAction.NONE,
    )
    val longTapZoneMiddleLeft by preferencesRepository.longTapZoneMiddleLeft.collectAsState(
        ReaderTapAction.NONE,
    )
    val longTapZoneCenter by preferencesRepository.longTapZoneCenter.collectAsState(
        ReaderTapAction.SHOW_ZOOM,
    )
    val longTapZoneMiddleRight by preferencesRepository.longTapZoneMiddleRight.collectAsState(
        ReaderTapAction.NONE,
    )
    val longTapZoneBottomLeft by preferencesRepository.longTapZoneBottomLeft.collectAsState(
        ReaderTapAction.NONE,
    )
    val longTapZoneBottomCenter by preferencesRepository.longTapZoneBottomCenter.collectAsState(
        ReaderTapAction.NONE,
    )
    val longTapZoneBottomRight by preferencesRepository.longTapZoneBottomRight.collectAsState(
        ReaderTapAction.NONE,
    )

    // Double Tap Zones
    val doubleTapZoneTopLeft by preferencesRepository.doubleTapZoneTopLeft.collectAsState(
        ReaderTapAction.NONE,
    )
    val doubleTapZoneTopCenter by preferencesRepository.doubleTapZoneTopCenter.collectAsState(
        ReaderTapAction.NONE,
    )
    val doubleTapZoneTopRight by preferencesRepository.doubleTapZoneTopRight.collectAsState(
        ReaderTapAction.NONE,
    )
    val doubleTapZoneMiddleLeft by preferencesRepository.doubleTapZoneMiddleLeft.collectAsState(
        ReaderTapAction.NONE,
    )
    val doubleTapZoneCenter by preferencesRepository.doubleTapZoneCenter.collectAsState(
        ReaderTapAction.NONE,
    )
    val doubleTapZoneMiddleRight by preferencesRepository.doubleTapZoneMiddleRight.collectAsState(
        ReaderTapAction.NONE,
    )
    val doubleTapZoneBottomLeft by preferencesRepository.doubleTapZoneBottomLeft.collectAsState(
        ReaderTapAction.NONE,
    )
    val doubleTapZoneBottomCenter by preferencesRepository.doubleTapZoneBottomCenter.collectAsState(
        ReaderTapAction.NONE,
    )
    val doubleTapZoneBottomRight by preferencesRepository.doubleTapZoneBottomRight.collectAsState(
        ReaderTapAction.NONE,
    )

    // Hardware Key Bindings
    val allPreferences by preferencesRepository.allPreferences.collectAsState(initial = null)
    val hardwareKeyBindings = allPreferences?.hardwareKeyBindings ?: HardwareKeyBindings()

    // UI Event collection (Toasts with emojis)
    LaunchedEffect(viewModelInstance) {
        viewModelInstance.uiEvent.collect { message ->
            val displayMessage =
                when {
                    message.contains("Right-to-Left", ignoreCase = true) -> "\u2b05\ufe0f $message"
                    message.contains("Left-to-Right", ignoreCase = true) -> "$message \u27a1\ufe0f"
                    message.contains("Rotation", ignoreCase = true) -> "\u21bb $message"
                    message.contains("Fit", ignoreCase = true) -> "\u2195 $message"
                    else -> message
                }
            activeToastMessage = displayMessage
        }
    }

    val bitmapCache = remember { BitmapCache(50 * 1024 * 1024) }
    var isLoading by remember { mutableStateOf(true) }
    var providerInitialized by remember { mutableStateOf(false) }

    // State for unsupported format dialog
    var showUnsupportedDialog by remember { mutableStateOf(false) }
    var unsupportedFormatInfo by remember { mutableStateOf<UnsupportedFormatException?>(null) }

    // Factory function to create provider based on archivePath
    LaunchedEffect(archivePath) {
        if (!archivePath.isNullOrEmpty()) {
            try {
                isLoading = true
                providerInitialized = false
                val uri =
                    try {
                        Uri.parse(archivePath)
                    } catch (e: Exception) {
                        Uri.fromFile(java.io.File(archivePath))
                    }

                val provider =
                    try {
                        ArchiveProviderFactory.create(context, uri, seriesName, issueNumber, tempFileManager)
                    } catch (e: UnsupportedFormatException) {
                        unsupportedFormatInfo = e
                        showUnsupportedDialog = true
                        null
                    } catch (e: Exception) {
                        Timber.tag("ComicViewerActivity").e(e, "Error creating provider")
                        PlaceholderPageProvider(seriesName, issueNumber)
                    }

                if (provider != null) {
                    viewModelInstance.updateProvider(provider)
                    bitmapCache.setArchive(archivePath)
                    providerInitialized = true

                    // Trigger detailed metadata load/sync
                    viewModelInstance.loadComicMetadata(archivePath)

                    // Set comic ID for read progress tracking
                    if (comicId != -1L) {
                        viewModelInstance.setComicId(comicId)
                    }

                    // Load pages after provider is set
                    viewModelInstance.loadPages()
                }
            } catch (e: Exception) {
                Timber.tag("ComicViewerActivity").e(e, "Failed to initialize viewer for $archivePath")
            } finally {
                isLoading = false
            }
        }
    }

    unsupportedFormatInfo?.let { info ->
        if (showUnsupportedDialog) {
            AlertDialog(
                onDismissRequest = {
                    showUnsupportedDialog = false
                    (context as? ComponentActivity)?.finish()
                },
                title = { Text("Format Not Supported") },
                text = { Text("ComicRack cannot read ${info.extension.uppercase()} files.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showUnsupportedDialog = false
                            try {
                                val uri = Uri.parse(archivePath)
                                val intent =
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, info.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                context.startActivity(Intent.createChooser(intent, "Open with..."))
                            } catch (e: Exception) {
                            }
                            (context as? ComponentActivity)?.finish()
                        },
                    ) { Text("Open Externally") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showUnsupportedDialog = false
                        (context as? ComponentActivity)?.finish()
                    }) { Text("Cancel") }
                },
            )
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    // Phase S3-03: Initialize menu state from preferences once
    var showTopBar by remember { mutableStateOf(true) }
    var hasInitializedMenuState by remember { mutableStateOf(false) }

    LaunchedEffect(readerOpenWithMenuCollected) {
        if (!hasInitializedMenuState) {
            showTopBar = readerOpenWithMenuCollected
            hasInitializedMenuState = true
        }
    }

    // Phase S3-03: True Immersive Mode (Hide Nav Bar when menu is closed)
    LaunchedEffect(showTopBar) {
        val activity = (context as? ComponentActivity)
        if (activity != null) {
            val window = activity.window
            val controller =
                androidx.core.view.WindowCompat
                    .getInsetsController(window, window.decorView)
            if (showTopBar) {
                controller.show(
                    androidx.core.view.WindowInsetsCompat.Type
                        .systemBars(),
                )
            } else {
                controller.hide(
                    androidx.core.view.WindowInsetsCompat.Type
                        .systemBars(),
                )
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    val currentPage by viewModelInstance.currentPageMetadata.collectAsState()
    val currentPageIndex by viewModelInstance.currentPageIndex.collectAsState()
    val totalPages by viewModelInstance.totalPages.collectAsState()
    val rotation by viewModelInstance.rotation.collectAsState()

    LaunchedEffect(readerBrightness, readerBrightnessEnabled, showTopBar) {
        val activity = (context as? ComponentActivity)
        if (activity != null) {
            val layoutParams = activity.window.attributes
            // Restore system brightness when menu is open OR if override is disabled
            if (readerBrightnessEnabled && !showTopBar) {
                layoutParams.screenBrightness = readerBrightness.coerceIn(0.01f, 1.0f)
            } else {
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            activity.window.attributes = layoutParams
        }
    }

    DisposableEffect(readerKeepScreenOn) {
        val window = (context as? ComponentActivity)?.window
        if (window != null) {
            if (readerKeepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            // Optional: specifically clear if we want to be certain, though system
            // clears flags when activity is destroyed. Doing it here ensures that
            // if the composition changes (e.g. navigating away), the flag is cleared.
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(viewModelInstance, currentPageIndex, totalPages) {
        val activity = (context as? ComicViewerActivity)
        if (activity != null) {
            activity.currentComicViewModel = viewModelInstance
            activity.currentPageIndex = currentPageIndex
            activity.totalPages = totalPages
        }
    }

    LaunchedEffect(archivePath) {
        if (!archivePath.isNullOrEmpty()) {
            bitmapCache.setArchive(archivePath)
        }
    }

    val actionProcessor =
        remember(context, viewModelInstance) {
            com.comicrack.reader.action.ReaderActionProcessor(
                context = context,
                viewModel = viewModelInstance,
                callbacks =
                    object : com.comicrack.reader.action.ReaderUiCallbacks {
                        override fun onToggleUI() {
                            showTopBar = !showTopBar
                        }

                        override fun onOpenSettings() {
                            context.startActivity(
                                android.content.Intent(context, com.comicrack.settings.SettingsActivity::class.java),
                            )
                        }

                        override fun onOpenMenu() {
                            showTopBar = !showTopBar
                        }

                        override fun onFinishActivity() {
                            (context as? ComponentActivity)?.finish()
                        }

                        override fun onShowBookmarks() {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    "Bookmarks not implemented",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        }

                        override fun onShowGoToPage() {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    "Go to Page not implemented",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        }

                        override fun onShowZoomControls() { /* No op */ }

                        override fun onSnapshot(context: android.content.Context) {
                            viewModelInstance.sharePageScreenshot(context)
                        }
                    },
            )
        }

    val tapActionHandler =
        remember(actionProcessor) {
            ReaderTapActionHandler(actionProcessor)
        }

    LaunchedEffect(tapActionHandler, viewModelInstance, hardwareKeyBindings) {
        val activity = (context as? ComicViewerActivity)
        if (activity != null) {
            activity.tapActionHandler = tapActionHandler
            activity.currentComicViewModel = viewModelInstance
            activity.currentKeyBindings = hardwareKeyBindings
            activity.readerInputController =
                ReaderInputController(
                    actionProcessor,
                    hardwareKeyBindings,
                )
        }
    }

    val zoneActions =
        remember(
            tapZoneTopLeft,
            tapZoneTopCenter,
            tapZoneTopRight,
            tapZoneMiddleLeft,
            tapZoneCenter,
            tapZoneMiddleRight,
            tapZoneBottomLeft,
            tapZoneBottomCenter,
            tapZoneBottomRight,
            longTapZoneTopLeft,
            longTapZoneTopCenter,
            longTapZoneTopRight,
            longTapZoneMiddleLeft,
            longTapZoneCenter,
            longTapZoneMiddleRight,
            longTapZoneBottomLeft,
            longTapZoneBottomCenter,
            longTapZoneBottomRight,
        ) {
            mapOf(
                Pair(TouchZone.TOP_LEFT, false) to tapZoneTopLeft,
                Pair(TouchZone.TOP_CENTER, false) to tapZoneTopCenter,
                Pair(TouchZone.TOP_RIGHT, false) to tapZoneTopRight,
                Pair(TouchZone.MIDDLE_LEFT, false) to tapZoneMiddleLeft,
                Pair(TouchZone.CENTER, false) to tapZoneCenter,
                Pair(TouchZone.MIDDLE_RIGHT, false) to tapZoneMiddleRight,
                Pair(TouchZone.BOTTOM_LEFT, false) to tapZoneBottomLeft,
                Pair(TouchZone.BOTTOM_CENTER, false) to tapZoneBottomCenter,
                Pair(TouchZone.BOTTOM_RIGHT, false) to tapZoneBottomRight,
                Pair(TouchZone.TOP_LEFT, true) to longTapZoneTopLeft,
                Pair(TouchZone.TOP_CENTER, true) to longTapZoneTopCenter,
                Pair(TouchZone.TOP_RIGHT, true) to longTapZoneTopRight,
                Pair(TouchZone.MIDDLE_LEFT, true) to longTapZoneMiddleLeft,
                Pair(TouchZone.CENTER, true) to longTapZoneCenter,
                Pair(TouchZone.MIDDLE_RIGHT, true) to longTapZoneMiddleRight,
                Pair(TouchZone.BOTTOM_LEFT, true) to longTapZoneBottomLeft,
                Pair(TouchZone.BOTTOM_CENTER, true) to longTapZoneBottomCenter,
                Pair(TouchZone.BOTTOM_RIGHT, true) to longTapZoneBottomRight,
            )
        }

    val readingState by viewModelInstance.readingState.collectAsState()
    val centerZoneSize by viewModelInstance.centerZoneSize.collectAsState()
    val horizontalSwipeEnabled by preferencesRepository.horizontalSwipeEnabled.collectAsState(false)

    val handleTap: (Offset) -> Unit =
        remember(
            tapNavigation,
            screenSize,
            rtlNavigationEnabled,
            readingDirection,
            zoneActions,
            tapActionHandler,
            currentPageIndex,
            totalPages,
            showTopBar,
            readerShowTapToasts,
            centerZoneSize,
        ) {
            { position: Offset ->
                if (showTopBar) {
                    showTopBar = false
                    return@remember
                }

                if (tapNavigation && screenSize.width > 0 && screenSize.height > 0) {
                    val zone = TouchZoneDetector.detectZone(position.x, position.y, screenSize, centerZoneSize)
                    var action = zoneActions[Pair(zone, false)] ?: ReaderTapAction.NONE

                    // Apply RTL reversal to logical actions if enabled OR if comic is naturally RTL
                    if (rtlNavigationEnabled || readingDirection == ReadingDirection.RTL) {
                        action = when (action) {
                            ReaderTapAction.NEXT_PAGE -> ReaderTapAction.PREVIOUS_PAGE
                            ReaderTapAction.PREVIOUS_PAGE -> ReaderTapAction.NEXT_PAGE
                            else -> action
                        }
                    }

                    if (readerShowTapToasts && action != ReaderTapAction.NONE) {
                        activeToastMessage = action.humanize()
                    }

                    tapActionHandler?.execute(action, currentPageIndex, totalPages, hapticFeedback)
                }
            }
        }

    val handleLongPress: (Offset) -> Unit =
        remember(
            tapNavigation,
            screenSize,
            rtlNavigationEnabled,
            readingDirection,
            zoneActions,
            tapActionHandler,
            currentPageIndex,
            totalPages,
            readerShowTapToasts,
            centerZoneSize,
        ) {
            { position: Offset ->
                if (tapNavigation && screenSize.width > 0 && screenSize.height > 0) {
                    val zone = TouchZoneDetector.detectZone(position.x, position.y, screenSize, centerZoneSize)
                    var action = zoneActions[Pair(zone, true)] ?: ReaderTapAction.NONE

                    // Apply RTL reversal to logical actions if enabled OR if comic is naturally RTL
                    if (rtlNavigationEnabled || readingDirection == ReadingDirection.RTL) {
                        action = when (action) {
                            ReaderTapAction.NEXT_PAGE -> ReaderTapAction.PREVIOUS_PAGE
                            ReaderTapAction.PREVIOUS_PAGE -> ReaderTapAction.NEXT_PAGE
                            else -> action
                        }
                    }

                    if (readerShowTapToasts && action != ReaderTapAction.NONE) {
                        activeToastMessage = "Long Press: ${action.humanize()}"
                    }

                    tapActionHandler?.execute(action, currentPageIndex, totalPages, hapticFeedback)
                }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { screenSize = it.size },
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = totalPages > 0,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            if (totalPages > 0 && !archivePath.isNullOrEmpty()) {
                GestureLayer(
                    swipeEnabled = horizontalSwipeEnabled,
                    isRtl = readingDirection == ReadingDirection.RTL,
                    onSingleTap = handleTap,
                    onLongPress = handleLongPress,
                    onSwipeLeft = { viewModelInstance.nextPage() },
                    onSwipeRight = { viewModelInstance.previousPage() },
                ) {
                    ComicReaderLayout(
                        readingState = readingState,
                        archivePath = archivePath,
                        viewModel = viewModelInstance, // Pass derived VM instance
                        onTap = handleTap,
                        onLongPress = handleLongPress,
                        onPageChanged = { index -> viewModelInstance.goToPage(index) },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    rotationZ = rotation.toFloat(),
                                ),
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), color = Color.White)
                }
            }
        }

        com.comicrack.reader.ui.TapZoneVisualizer(
            isVisible = showTapAreas,
            modifier = Modifier.fillMaxSize(),
            centerZoneSize = centerZoneSize,
        )

        ModernReaderOverlay(
            seriesName = currentPage?.seriesName ?: seriesName,
            issueNumber = currentPage?.issueNumber ?: issueNumber,
            currentPage = currentPageIndex,
            totalPages = totalPages,
            isVisible = showTopBar,
            _onToggleVisibility = { showTopBar = !showTopBar },
            onPageSelected = { viewModelInstance.goToPage(it) },
            _onSettingsClick = { }, // Handled internally now
            onRotateClick = { viewModelInstance.rotatePage() },
            onFitClick = { viewModelInstance.cycleFitMode() },
            _onShareClick = { viewModelInstance.sharePageScreenshot(context) },
            onBackClick = { (context as? androidx.activity.ComponentActivity)?.finish() },
            viewModel = viewModelInstance,
            modifier = Modifier.fillMaxSize(),
        )

        if (totalPages > 0) {
            ReaderPageNumberDisplay(
                currentPage = currentPageIndex,
                totalPages = totalPages,
                isVisible = readerShowPageNumbers && !showTopBar,
                isRtl = readingDirection == ReadingDirection.RTL,
                modifier =
                    Modifier
                        .align(
                            if (readingDirection ==
                                ReadingDirection.RTL
                            ) {
                                Alignment.BottomStart
                            } else {
                                Alignment.BottomEnd
                            },
                        ).padding(16.dp),
            )
        }

        // Custom Themed Toast Overlay (Phase S3-03: Material3 Premium Styling)
        androidx.compose.animation.AnimatedVisibility(
            visible = activeToastMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape, // Pill style
                tonalElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            ) {
                Text(
                    text = activeToastMessage ?: "",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
