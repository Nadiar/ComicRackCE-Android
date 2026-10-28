package com.comicrack.settings.screens

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicrack.core.storage.UriFileUtils
import com.comicrack.core.storage.saf.AllFilesAccessHelper

/**
 * AdditionalFoldersSection â€” UI for listing and managing additional library folders.
 *
 * Supports SAF picker and All Files Access fallback. Shows current folders, allows
 * scanning and removal, and provides an Add Folder flow (SAF or manual path).
 *
 * Documented in docfix/pilot-comments; no code behavior was changed.
 */

@Composable
fun AdditionalFoldersSection(
    folders: Set<String>,
    isScanning: Boolean = false,
    useDirectFileAccess: Boolean = true,
    onAddFolder: (Uri) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onScanFolder: (String) -> Unit,
) {
    val context = LocalContext.current

    // Support Dialog for Add Folder
    var showFolderDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var manualPath by remember { androidx.compose.runtime.mutableStateOf("/storage/emulated/0/") }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (e: Exception) {
                }
                onAddFolder(it)
            }
        }

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
                    onAddFolder(Uri.parse("file://$manualPath"))
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        context.startActivity(AllFilesAccessHelper.createAllFilesAccessIntent(context))
                    }
                }
            },
            onDismiss = { showFolderDialog = false },
            hasAllFilesAccess = AllFilesAccessHelper.hasAllFilesAccess(),
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (folders.isNotEmpty()) {
            folders.forEach { uriString ->
                val name = remember(uriString) { UriFileUtils.getFriendlyPath(context, uriString) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )

                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        // 2-button miniature pill: Scan and Delete
                        Row(
                            modifier = Modifier.height(32.dp).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick = { onScanFolder(uriString) },
                                enabled = !isScanning,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape =
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        bottomStart = 16.dp,
                                        topEnd = 0.dp,
                                        bottomEnd = 0.dp,
                                    ),
                                modifier = Modifier.height(32.dp).width(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Scan",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            VerticalDivider(
                                modifier = Modifier.height(32.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                                thickness = 1.dp,
                            )
                            Surface(
                                onClick = { onRemoveFolder(uriString) },
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.error,
                                shape =
                                    RoundedCornerShape(
                                        topEnd = 16.dp,
                                        bottomEnd = 16.dp,
                                        topStart = 0.dp,
                                        bottomStart = 0.dp,
                                    ),
                                modifier = Modifier.height(32.dp).width(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Styled "Add Folder" button at the bottom
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
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(48.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Additional Folder", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
