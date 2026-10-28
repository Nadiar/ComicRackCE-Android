package com.comicrack.settings.ui.components

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Composable that displays a folder picker UI for selecting library storage locations.
 *
 * This component provides a Material 3 Card with a button to launch the Android Storage
 * Access Framework (SAF) folder picker. When a folder is selected, it persists the URI
 * permissions and notifies the caller via the [onFolderSelected] callback.
 *
 * ## Features
 * - Uses SAF `OpenDocumentTree` for secure folder access
 * - Automatically persists read/write permissions
 * - Shows current selected path if available
 * - Material 3 design with proper spacing and typography
 *
 * ## Usage
 * ```kotlin
 * LibraryFolderPicker(
 *     title = "Main Library Folder",
 *     description = "Select the folder containing your comics",
 *     currentPath = "/storage/emulated/0/Comics",
 *     onFolderSelected = { uri -> viewModel.setLibraryPath(uri) }
 * )
 * ```
 *
 * @param title The title text displayed at the top of the picker card
 * @param description Explanatory text shown below the title
 * @param currentPath The currently selected folder path, or null if not set
 * @param onFolderSelected Callback invoked with the selected folder URI when user picks a folder
 * @param allowCloudStorage If true, allows selection of cloud storage locations (default: false)
 * @param modifier Optional Modifier for this composable
 */
@Composable
fun LibraryFolderPicker(
    title: String,
    description: String,
    currentPath: String?,
    onFolderSelected: (Uri) -> Unit,
    _allowCloudStorage: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val folderPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                if (uri != null) {
                    try {
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                        onFolderSelected(uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
        )

    Card(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (description.isNotBlank()) {
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
            if (currentPath != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text(text = currentPath, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
            Button(onClick = { folderPickerLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Select Folder")
            }
        }
    }
}
