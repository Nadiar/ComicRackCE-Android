package com.comicrack.settings.ui.components

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Storage source selector for library folder selection.
 *
 * **Issue #2 Fix:** SAF Picker limitations for Google Drive
 *
 * Problem:
 * - OpenDocumentTree() works for local folders (Internal, SD Card)
 * - OpenDocumentTree() does NOT support folder selection on Google Drive
 * - Google Drive SAF only allows single-file access, not directory browsing
 *
 * Solution:
 * - For Local Storage: Use OpenDocumentTree() (folder picker)
 * - For Google Drive: Use OpenDocument() (file picker) or custom implementation
 * - Let user explicitly choose storage source upfront
 *
 * This component provides a dialog showing available storage sources.
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Composable
fun StorageSourcePicker(
    onSourceSelected: (StorageSource) -> Unit,
    onDismiss: () -> Unit,
    allowCloudStorage: Boolean = true,
    title: String = "Select Storage Source",
    allowOnlyLocalStorage: Boolean = false, // For Main Library (restrict to Internal/SD only)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Internal Storage - Uses OpenDocumentTree (folder picker)
                ListItem(
                    headlineContent = { Text("Internal Storage") },
                    supportingContent = { Text("Device internal memory - Select any folder") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            onSourceSelected(StorageSource.INTERNAL_STORAGE)
                            onDismiss()
                        },
                )

                // SD Card - Uses OpenDocumentTree (folder picker)
                if (isExternalSdCardAvailable()) {
                    ListItem(
                        headlineContent = { Text("SD Card") },
                        supportingContent = { Text("External SD card storage - Select any folder") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onSourceSelected(StorageSource.SD_CARD)
                                onDismiss()
                            },
                    )
                }

                // Google Drive - Requires different approach
                if (allowCloudStorage && !allowOnlyLocalStorage) {
                    ListItem(
                        headlineContent = { Text("Google Drive") },
                        supportingContent = {
                            Text(
                                "Cloud storage - Sign in with Google to browse and read comics from Drive folders.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onSourceSelected(StorageSource.GOOGLE_DRIVE)
                                onDismiss()
                            },
                    )
                }

                // Info message for Main Library restriction
                if (allowOnlyLocalStorage) {
                    ListItem(
                        headlineContent = { Text("Note") },
                        supportingContent = {
                            Text(
                                "Main Library only supports local storage (Internal/SD Card). " +
                                    "Use Additional Folders for cloud storage.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Enum representing available storage sources.
 * Each source has different picker behavior due to SAF limitations.
 */
enum class StorageSource {
    /**
     * Internal device storage.
     * Uses: OpenDocumentTree() for folder selection
     * Picker shows: Folder browser with full directory access
     */
    INTERNAL_STORAGE,

    /**
     * External SD card (if available).
     * Uses: OpenDocumentTree() for folder selection
     * Picker shows: Folder browser with full directory access
     */
    SD_CARD,

    /**
     * Google Drive cloud storage.
     * LIMITATION: OpenDocumentTree() does NOT support folder selection on Google Drive
     * Current option: Use OpenDocument() for individual file selection
     * Better option: Implement custom Google Drive API integration (future enhancement)
     */
    GOOGLE_DRIVE,
}

/**
 * Check if external SD card is available.
 */
private fun isExternalSdCardAvailable(): Boolean =
    try {
        val extStorage = Environment.getExternalStorageState()
        extStorage == Environment.MEDIA_MOUNTED
    } catch (e: Exception) {
        false
    }
