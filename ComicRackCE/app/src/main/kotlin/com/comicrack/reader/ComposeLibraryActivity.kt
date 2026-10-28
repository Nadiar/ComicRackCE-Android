package com.comicrack.reader

/**
 * Compose-based library Activity hosting the main library UI.
 *
 * Responsibilities:
 * - Hosts the Compose library screen and coordinates navigation to reader/settings
 * - Provides edge-to-edge and theming integration via `ComicRackTheme`
 * - Uses `LibraryScanner` to trigger scans; avoid performing heavy IO directly in
 *   the Activity â€” prefer ViewModel-scoped coroutines for long-running work.
 *
 * Testing notes:
 * - Add UI tests that assert toolbar actions, search/navigation, and edge cases for
 *   empty library and permission-denied states.
 */

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import com.comicrack.data.preferences.AppPreferencesRepository
import javax.inject.Inject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.navigation.NavigationRail
import com.comicrack.navigation.NavigationRailViewModel
import com.comicrack.reader.data.LibraryScanner
import com.comicrack.settings.SettingsActivity
import com.comicrack.ui.theme.ComicRackMaterial3Theme
import com.comicrack.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * ComposeLibraryActivity â€” Hosts the Compose-based Library UI.
 *
 * Responsibilities:
 * - Initializes the theme and ViewModel
 * - Sets up edge-to-edge immersive UI to reduce flashes on startup
 * - Provides integration points for WiFi sync and settings navigation
 *
 * **Future Work:** App-wide search implementation is pending design decision.
 * See TECHNICAL-DEBT-TODO-TRACKING.md P1.1 for architecture options (Option A vs B).
 */
@AndroidEntryPoint
class ComposeLibraryActivity : ComponentActivity() {
    @Inject
    lateinit var appPreferencesRepository: AppPreferencesRepository

    /**
     * ViewModel that exposes library UI state and actions.
     *
     * - Observed by Compose via flows collected with lifecycle awareness.
     * - Encapsulates import/scan/read toggles and user preferences.
     */
    private val viewModel: ComposLibraryViewModel by viewModels()
    private val railViewModel: NavigationRailViewModel by viewModels()

    /**
     * Lazily-initialized ThemeManager using application preferences.
     *
     * Controls dark/light theme choices for Compose.
     */
    private val themeManager by lazy { ThemeManager(appPreferencesRepository) }

    /**
     * Configure window, edge-to-edge behaviour and set the Compose UI content.
     *
     * Sets a black window background to avoid white flashes on startup and installs
     * the `LibraryActivityContent` composable with the current theme.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set window background to black immediately to prevent white flash
        window.setBackgroundDrawableResource(android.R.color.black)
        enableEdgeToEdge()
        Timber.tag("ComposeLibraryActivity").d("Starting library activity")

        // DEBUG: Test reading comicrack.ini from _sync folder
        debugReadSyncIni()

        setContent {
            val isDarkMode = themeManager.isDarkModeComposable()
            ComicRackMaterial3Theme(darkTheme = isDarkMode) {
                LibraryActivityContent(
                    _activity = this@ComposeLibraryActivity,
                    viewModel = viewModel,
                    railViewModel = railViewModel,
                    onComicClick = { comic -> launchComicReader(comic) },
                    onSettingsClick = { openSettings() },
                )
            }
        }
    }

    /**
     * Open the SettingsActivity.
     */
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    /**
     * Start the application's WiFi synchronization service.
     *
     * Uses a foreground service on Android O+ and falls back to `startService` on older releases.
     * Errors are caught and logged to prevent crashes from service start failures.
     */
    fun startSyncService() {
        try {
            val intent =
                Intent(this, com.comicrack.wifi.service.WiFiSyncService::class.java).apply {
                    action = com.comicrack.wifi.service.WiFiSyncService.ACTION_START_SYNC
                }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Timber.tag("ComposeLibraryActivity").i("WiFiSyncService start requested")
        } catch (e: Exception) {
            Timber.tag("ComposeLibraryActivity").e(e, "Failed to start WiFiSyncService")
        }
    }

    /**
     * Open the `ComicViewerActivity` to view the selected comic.
     *
     * Supplies `seriesName`, `issueNumber` and `archivePath` as intent extras.
     */
    private fun launchComicReader(comic: ComicBook) {
        val intent =
            Intent(this, ComicViewerActivity::class.java).apply {
                putExtra("seriesName", comic.series.ifEmpty { comic.title })
                putExtra("issueNumber", comic.issueNumber.ifEmpty { "1" })
                putExtra("archivePath", comic.filePath)
                putExtra("COMIC_ID", comic.id)
            }
        startActivity(intent)
    }

    /**
     * Diagnostic helper that attempts to read `_sync/comicrack.ini` from the configured library tree.
     *
     * - Logs persisted SAF permissions, lists directory entries and reads a preview of the INI file.
     * - Intended for debugging and not for production flow; it will log exceptions instead of surfacing them.
     */
    private fun debugReadSyncIni() {
        try {
            // First, check persisted permissions
            val persistedPerms = contentResolver.persistedUriPermissions
            Timber.tag("DEBUG_INI").d("Persisted URI permissions: ${persistedPerms.size}")
            persistedPerms.forEach { perm ->
                Timber.tag("DEBUG_INI").d("  URI: ${perm.uri}")
                Timber.tag("DEBUG_INI").d("    read=${perm.isReadPermission}, write=${perm.isWritePermission}")
            }

            val libraryPathStr = appPreferencesRepository.getLibraryPathBlocking()
            if (libraryPathStr.isNullOrBlank()) {
                Timber.tag("DEBUG_INI").w("No library path set")
                return
            }

            val libraryUri = android.net.Uri.parse(libraryPathStr)
            Timber.tag("DEBUG_INI").d("Library URI: $libraryUri")

            // Check if this URI has persisted permission
            val hasPermission = persistedPerms.any { it.uri == libraryUri }
            Timber.tag("DEBUG_INI").d("Library URI has persisted permission: $hasPermission")

            // Use SyncFileManager instead of direct DocumentFile.fromTreeUri()
            val syncFileManager =
                com.comicrack.wifi.file.SyncFileManager
                    .fromTreeUri(this, libraryUri)
            if (syncFileManager == null) {
                Timber.tag("DEBUG_INI").w("SyncFileManager initialization failed")
                return
            }

            Timber.tag("DEBUG_INI").d("SyncFileManager initialized successfully")

            // List sync files using abstraction
            val syncFiles =
                try {
                    kotlinx.coroutines.runBlocking {
                        syncFileManager.listFiles()
                    }
                } catch (e: Exception) {
                    Timber.tag("DEBUG_INI").e(e, "Failed to list sync files")
                    return
                }

            Timber.tag("DEBUG_INI").d("_sync has ${syncFiles.size} files")
            syncFiles.forEach { f ->
                Timber.tag("DEBUG_INI").d("  - $f")
            }

            // Find and read comicrack.ini
            val hasIni = syncFiles.any { it.equals("comicrack.ini", ignoreCase = true) }
            if (!hasIni) {
                Timber.tag("DEBUG_INI").w("comicrack.ini NOT FOUND in _sync")
                return
            }

            val iniBytes =
                try {
                    kotlinx.coroutines.runBlocking {
                        syncFileManager.readFile("comicrack.ini")
                    }
                } catch (e: Exception) {
                    Timber.tag("DEBUG_INI").e(e, "Failed to read comicrack.ini")
                    return
                }

            if (iniBytes == null) {
                Timber.tag("DEBUG_INI").w("Failed to read comicrack.ini (readFile returned null)")
                return
            }

            val iniContent = String(iniBytes, Charsets.UTF_8)
            Timber.tag("DEBUG_INI").i("SUCCESS! comicrack.ini size=${iniBytes.size} bytes")
            Timber.tag("DEBUG_INI").i("Content preview:\n${iniContent.take(500)}")
        } catch (e: Exception) {
            Timber.tag("DEBUG_INI").e(e, "Error reading sync INI")
        }
    }
}

/**
 * Composable that renders the main library screen inside a Scaffold.
 *
 * @param activity Host activity used for navigation and starting activities.
 * @param viewModel ViewModel providing state and actions for the library UI.
 * @param railViewModel ViewModel providing state and actions for the navigation rail.
 * @param onComicClick Callback invoked when a comic is selected; receives the selected [ComicBook].
 * @param onSettingsClick Callback invoked when settings is tapped from the navigation rail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryActivityContent(
    _activity: ComposeLibraryActivity,
    viewModel: ComposLibraryViewModel,
    railViewModel: NavigationRailViewModel,
    onComicClick: (ComicBook) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val railState by railViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(railState.selectedLibraryId) {
        val id = railState.selectedLibraryId
        when {
            id == "all_comics" -> viewModel.setFilterMode(ComposLibraryViewModel.FilterMode.ALL)
            id == "new" -> viewModel.setFilterMode(ComposLibraryViewModel.FilterMode.NEW)
            id == "unread" -> viewModel.setFilterMode(ComposLibraryViewModel.FilterMode.UNREAD)
            id == "reading" -> viewModel.setFilterMode(ComposLibraryViewModel.FilterMode.READING)
            id.startsWith("path_") -> {
                val pathId = id.removePrefix("path_").toLongOrNull()
                viewModel.setLibraryPathId(pathId)
            }
        }
    }

    BackHandler(enabled = railState.isOpen) {
        railViewModel.closeRail()
    }

    // Collect all state before Box (cleaner composition structure)
    val comics by viewModel.comics.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val filterMode by viewModel.filterMode.collectAsStateWithLifecycle()
    val manualReadComics by viewModel.manualReadComics.collectAsStateWithLifecycle()

    // Preferences
    val libraryLayoutColumns by viewModel.libraryLayoutColumns.collectAsStateWithLifecycle()
    val libraryGridSpacing by viewModel.libraryGridSpacing.collectAsStateWithLifecycle()
    val libraryCardElevation by viewModel.libraryCardElevation.collectAsStateWithLifecycle()
    val libraryShowTitles by viewModel.libraryShowTitles.collectAsStateWithLifecycle()
    val libraryAnimateCards by viewModel.libraryAnimateCards.collectAsStateWithLifecycle()

    val viewModeStr = viewMode.name.lowercase()
    val filterModeStr = filterMode.name.lowercase()

    /* Commented out due to unresolved references/hallucinated API in LibraryScanner
    val libraryPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    // val comicsArray = intent.getParcelableArrayExtra("comics", LibraryScanner.ComicFile::class.java)
                    val path = intent.getStringExtra("path")
                    if (path != null) {
                        // viewModel.importComics(path, comicsArray?.toList().orEmpty())
                    }
                }
            }
        }
    */

    // Scanning state for Pull-to-Refresh
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                androidx.compose.material3.CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            railViewModel.toggleRail()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "ComicRack",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        IconButton(onClick = { /* Search feature pending - see TECHNICAL-DEBT-TODO-TRACKING.md */ }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    colors =
                        TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                LibraryScreen(
                    comics = comics,
                    onComicClick = onComicClick,
                    _viewMode = viewModeStr,
                    currentViewMode = viewModeStr,
                    onToggleViewMode = { viewModel.toggleViewMode() },
                    _filterMode = filterModeStr,
                    currentFilterMode = filterModeStr,
                    onToggleFilterMode = { viewModel.toggleFilterMode() },
                    onToggleReadStatus = { filePath -> viewModel.toggleComicReadStatus(filePath) },
                    readComics = manualReadComics,
                    // Pull to Refresh
                    isRefreshing = isScanning,
                    onRefresh = { viewModel.refreshLibrary(forced = false) },
                    // Preferences
                    layoutColumns = libraryLayoutColumns,
                    gridSpacing = libraryGridSpacing,
                    cardElevation = libraryCardElevation,
                    showTitles = libraryShowTitles,
                    animateCards = libraryAnimateCards,
                )
            }
        }

        // Navigation Rail rendered last (on top of Scaffold due to Box composition order)
        NavigationRail(
            viewModel = railViewModel,
            onSettingsClick = onSettingsClick,
        )
    }
}
