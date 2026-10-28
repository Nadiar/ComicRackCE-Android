package com.comicrack.reader
// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.comicrack.core.scanner.LibraryPathManager
import com.comicrack.core.scanner.LibraryScannerService
import com.comicrack.data.database.entities.LibraryPath
import com.comicrack.reader.data.LibraryScanner
import com.comicrack.ui.theme.ComicRackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SAF Directory Picker Activity for Phase 4.2.
 *
 * Allows users to select a directory containing comic archives and scan it for comics.
 *
 * Operational notes:
 * - Uses SAF (`OpenDocumentTree`) to let users pick folders. Persist the returned
 *   Uri and validate access by attempting a lightweight listing before scheduling
 *   long-running scans.
 * - Scans should run off the UI thread and report progress via `LibraryScanner`.
 * - Add tests that simulate a user picking a folder, missing permissions, and
 *   scanning folders with deep nesting to detect performance issues.
 *
 * Features:
 * - SAF (Storage Access Framework) directory picker
 * - Recursive scanning for supported comic formats
 * - Display of found comics with metadata extraction
 * - Option to scan selected directory or go back
 * - Clean Material 3 UI
 */
class LibraryPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.tag("LibraryPickerActivity").d("Starting library picker")

        setContent {
            ComicRackTheme {
                LibraryPickerScreen(
                    context = this@LibraryPickerActivity,
                    onComicsSelected = { comics, path ->
                        // Persist selected folder and comics to database
                        persistLibraryToDatabase(comics, path)
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    /**
     * Persist selected library path and discovered comics to the application's database.
     *
     * Performs the following steps on a background (IO) dispatcher:
     * 1. Adds the selected folder to the `LibraryPath` table and returns the new ID.
     * 2. Scans and inserts discovered comics into the `ComicBook` table via `LibraryScannerService`.
     * 3. Updates scan metadata and returns a result Intent to the caller on the main thread.
     *
     * @param comics List of discovered [LibraryScanner.ComicFile] to be persisted.
     * @param path The picked folder's [Uri] as returned by SAF (Storage Access Framework).
     * @throws Exception Propagates I/O and database errors; callers should handle failures.
     */
    private fun persistLibraryToDatabase(
        comics: List<LibraryScanner.ComicFile>,
        path: Uri,
    ) {
        Timber.tag("LibraryPickerActivity").d("Persisting ${comics.size} comics to database for path: $path")

        // Launch background job to persist to database
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pathManager = LibraryPathManager(this@LibraryPickerActivity)
                val scannerService = LibraryScannerService.create(this@LibraryPickerActivity)

                // Step 1: Create LibraryPath record with display name
                val displayName = "Comic Library"
                val libraryPathId = pathManager.addLibraryPath(path, displayName)
                Timber.tag("LibraryPickerActivity").d("Created LibraryPath with ID: $libraryPathId")

                // Step 2: Scan and persist to database
                // Create LibraryPath entity with the returned ID
                val libraryPath =
                    LibraryPath(
                        path = path.toString(),
                        displayName = displayName,
                    ).apply {
                        id = libraryPathId
                    }

                // This will insert all comics into ComicBook table
                val (persistedComics, _) = scannerService.refreshLibraryPath(libraryPath)
                Timber.tag("LibraryPickerActivity").d("Persisted ${persistedComics.size} comics to database")

                // Step 3: Return to caller with success
                lifecycleScope.launch {
                    val intent =
                        Intent().apply {
                            putExtra("libraryPathId", libraryPathId)
                            putExtra("path", path.toString())
                            putExtra("comicCount", persistedComics.size)
                        }
                    setResult(RESULT_OK, intent)
                    finish()
                }
            } catch (e: Exception) {
                Timber.tag("LibraryPickerActivity").e(e, "Failed to persist library")
                lifecycleScope.launch {
                    val intent =
                        Intent().apply {
                            putExtra("error", "Failed to save library: ${e.message}")
                        }
                    setResult(RESULT_CANCELED, intent)
                    finish()
                }
            }
        }
    }
}

/**
 * Main UI composable for the library picker.
 *
 * Renders a Material 3 `Scaffold` that:
 * - Launches the SAF directory picker,
 * - Shows scanning progress and errors,
 * - Lists discovered comics and provides actions to cancel or confirm the selection.
 *
 * @param context Activity [Context] used to launch the directory picker and take persistable URI permissions.
 * @param onComicsSelected Callback invoked when the user confirms a folder containing comics.
 *        Receives the discovered [LibraryScanner.ComicFile] list and the selected [Uri].
 * @param onCancel Callback invoked when the user cancels the picker or navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPickerScreen(
    context: Context,
    onComicsSelected: (List<LibraryScanner.ComicFile>, Uri) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedPath by remember { mutableStateOf<Uri?>(null) }
    var foundComics by remember { mutableStateOf<List<LibraryScanner.ComicFile>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    // Directory picker launcher
    val directoryPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                if (uri != null) {
                    selectedPath = uri
                    Timber.tag("LibraryPickerScreen").d("Selected directory: $uri")

                    // Persist permission
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                    // Start scanning
                    isScanning = true
                    scope.launch {
                        try {
                            foundComics = LibraryScanner.scanPath(context, uri.toString(), recursive = true)
                            Timber.tag("LibraryPickerScreen").d("Found ${foundComics.size} comics")

                            if (foundComics.isEmpty()) {
                                errorMessage = "No comic archives found in selected directory"
                            } else {
                                errorMessage = ""
                            }
                        } catch (e: Exception) {
                            Timber.tag("LibraryPickerScreen").e(e, "Error scanning directory")
                            errorMessage = "Error scanning directory: ${e.message}"
                            foundComics = emptyList()
                        } finally {
                            isScanning = false
                        }
                    }
                }
            },
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Comic Library") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            // Directory selector button
            Button(
                onClick = { directoryPickerLauncher.launch(null) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                enabled = !isScanning,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(if (selectedPath == null) "Select Directory" else "Change Directory")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Loading indicator
            if (isScanning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Scanning for comics...")
                    }
                }
            } else {
                // Error message
                if (errorMessage.isNotEmpty()) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text(
                            errorMessage,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                // Found comics list
                if (foundComics.isNotEmpty()) {
                    Text(
                        "${foundComics.size} comics found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(foundComics) { comic ->
                            val (series, issue) = comic.extractMetadata()
                            ComicFileItem(
                                series = series,
                                issue = issue,
                                filename = comic.filename,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                selectedPath?.let { uri ->
                                    onComicsSelected(foundComics, uri)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Use This Folder")
                        }
                    }
                } else if (selectedPath != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No comics found. Try another folder.")
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Select a folder to begin scanning")
                    }
                }
            }
        }
    }
}

/**
 * Composable that renders a single comic file entry in the result list.
 *
 * Shows the series title, optional issue number, and the original file name using
 * Material 3 typography styles.
 *
 * @param series Series title extracted from the file metadata.
 * @param issue Issue identifier or empty string when not present.
 * @param filename Original archive filename shown to the user.
 */
@Composable
fun ComicFileItem(
    series: String,
    issue: String,
    filename: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Text(
                series,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (issue.isNotEmpty()) {
                Text(
                    "Issue #$issue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                filename,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
