package com.comicrack.settings.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicrack.core.storage.UriFileUtils
import com.comicrack.core.storage.saf.AllFilesAccessHelper
import timber.log.Timber


/**
 * Inline Picker Wrapper to support Split Button UI
 */
@Composable
fun LibraryFolderPickerWithSplitButton(
    currentPath: String?,
    isScanning: Boolean,
    useDirectFileAccess: Boolean = true,
    onFolderSelected: (Uri) -> Unit,
    onScanRequested: () -> Unit,
) {
    val context = LocalContext.current
    var showFolderDialog by remember { mutableStateOf(false) }
    var manualPath by remember { mutableStateOf("/storage/emulated/0/Comics") }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (e: Exception) {
                    Timber.tag("LibraryFolderPicker").e(e, "Failed to take permission")
                }
                onFolderSelected(it)
            }
        }

    val friendlyName = remember(currentPath) { UriFileUtils.getFriendlyPath(context, currentPath) }

    // Dialog for folder selection options
    if (showFolderDialog) {
        FolderSelectionDialog(
            manualPath = manualPath,
            onManualPathChange = { manualPath = it },
            onUseSafPicker = {
                showFolderDialog = false
                launcher.launch(null)
            },
            onUseManualPath = {
                showFolderDialog = false
                if (AllFilesAccessHelper.hasAllFilesAccess()) {
                    // Use file:// URI for direct path access
                    onFolderSelected(Uri.parse("file://$manualPath"))
                } else {
                    // Request All Files Access permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        context.startActivity(AllFilesAccessHelper.createAllFilesAccessIntent(context))
                    }
                }
            },
            onDismiss = { showFolderDialog = false },
            hasAllFilesAccess = AllFilesAccessHelper.hasAllFilesAccess(),
        )
    }

    // Wrap everything in a Column so we can add the Divider at the bottom
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Text Section (Aligned to the start)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 96.dp), // Ensures text doesn't overlap the button
            ) {
                Text(
                    text = "Main Library Folder",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Text(
                    text = friendlyName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (currentPath ==
                            null
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }

            // Split Button (Aligned to the Center-End)
            Row(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .height(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onScanRequested,
                    enabled = !isScanning,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                    modifier = Modifier.height(32.dp).width(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Scan",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                    thickness = 1.dp,
                )

                Surface(
                    onClick = {
                        if (useDirectFileAccess) {
                            showFolderDialog = true
                        } else {
                            launcher.launch(null)
                        }
                    },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 0.dp, bottomStart = 0.dp),
                    modifier = Modifier.height(32.dp).width(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Dialog for folder selection with multiple options:
 * 1. SAF Picker (standard Android way)
 * 2. Manual path entry (requires All Files Access permission)
 */
@Composable
fun FolderSelectionDialog(
    manualPath: String,
    onManualPathChange: (String) -> Unit,
    onUseSafPicker: () -> Unit,
    onUseManualPath: () -> Unit,
    onDismiss: () -> Unit,
    hasAllFilesAccess: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Library Folder") },
        text = {
            Column {
                Text(
                    "Choose how to select your comics folder:",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: SAF Picker
                Text(
                    "Option 1: Official System Picker",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "This is the standard Android way. It's safe but can be " +
                        "restricted by the system. Use this if your comics are in " +
                        "basic folders like 'Documents' or 'Downloads'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Option 2: Manual Path
                Text(
                    "Option 2: Direct Storage Access (Legacy)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Use this to see all folders no matter what. Recommended for " +
                        "SD Cards or external storage. Requires granting the " +
                        "'All Files Access' permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualPath,
                    onValueChange = onManualPathChange,
                    label = { Text("Folder Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onUseSafPicker) {
                    Text("System Picker")
                }
                TextButton(onClick = onUseManualPath) {
                    Text(if (hasAllFilesAccess) "Use Path" else "Grant Access")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
