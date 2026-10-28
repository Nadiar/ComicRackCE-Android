package com.comicrack.reader.utils

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import timber.log.Timber

/**
 * Utility class for Storage Access Framework (SAF) operations.
 */
object SAFUtils {
    /**
     * Checks if a target URI is covered by existing tree permissions.
     *
     * @param context App context
     * @param targetUri The URI to check
     * @return true if an existing persisted permission covers this URI
     */
    fun hasPermissionFor(
        context: Context,
        targetUri: Uri,
    ): Boolean {
        val persistedPerms = context.contentResolver.persistedUriPermissions

        // 1. Direct match
        if (persistedPerms.any { it.uri == targetUri && it.isReadPermission }) {
            return true
        }

        // 2. Parent match ( inheritance)
        // Tree URIs usually look like content://.../tree/root_id%3Apath
        // We check if targetUri starts with any of the permitted tree roots
        val targetString = targetUri.toString()
        for (perm in persistedPerms) {
            if (!perm.isReadPermission) continue

            val permString = perm.uri.toString()

            // Tree URI prefix check
            // For external storage:
            // content://com.android.externalstorage.documents/tree/primary%3AComics
            // Children:
            // content://.../tree/primary%3AComics/document/primary%3AComics%2FSubfolder

            if (targetString.startsWith(permString)) {
                // Potential hit, but be careful with URI structure
                // If perm is /tree/A and target is /tree/A/document/B, it's covered
                // If perm is /tree/A and target is /tree/AB, it's NOT covered (string prefix pitfall)

                // Check if it's a true descendant by verifying its document ID
                try {
                    val targetDocId =
                        if (DocumentsContract.isDocumentUri(context, targetUri)) {
                            DocumentsContract.getDocumentId(targetUri)
                        } else {
                            DocumentsContract.getTreeDocumentId(targetUri)
                        }

                    val permTreeId = DocumentsContract.getTreeDocumentId(perm.uri)

                    if (targetDocId.startsWith(permTreeId)) {
                        Timber.tag("SAFUtils").d("Inheriting permission for $targetDocId from $permTreeId")
                        return true
                    }
                } catch (e: Exception) {
                    // Fallback to basic string check if document ID extraction fails
                    if (targetString.startsWith("$permString/document/")) return true
                }
            }
        }

        return false
    }

    /**
     * Resolves a folder from a URI string, handling both direct and inherited permissions.
     */
    fun getDocumentFile(
        context: Context,
        uriString: String?,
    ): DocumentFile? {
        if (uriString.isNullOrEmpty()) return null
        val uri = Uri.parse(uriString)

        return if (DocumentsContract.isTreeUri(uri)) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }
    }

    /**
     * Extract a human-readable display name from a path or URI.
     *
     * For SAF URIs: Returns the folder name (without "primary:" prefix)
     * For local paths: Returns the folder name
     * Fallback: Returns the full path
     */
    fun extractDisplayNameFromPath(path: String): String =
        when {
            path.startsWith("content://") -> {
                // SAF URI - decode and extract folder name
                try {
                    val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
                    // Extract the tree path (e.g., "primary:Comics2" from the URI)
                    val treePattern = """/tree/([^/]+)""".toRegex()
                    val treeMatch = treePattern.find(decodedPath)
                    if (treeMatch != null) {
                        val treePart = treeMatch.groupValues[1]
                        // Remove "primary:" or other storage prefixes
                        treePart.substringAfter(":").ifEmpty { treePart }
                    } else {
                        // Fallback: get the last path segment
                        val segment = Uri.parse(path).lastPathSegment ?: path
                        segment.substringAfter(":").ifEmpty { segment }
                    }
                } catch (e: Exception) {
                    // Fallback to simple segment extraction if decoder fails
                    val segment = Uri.parse(path).lastPathSegment ?: path
                    segment.substringAfter(":").ifEmpty { segment }
                }
            }
            path.contains("/") -> {
                // Local path - extract the folder name
                path.trim('/').split('/').lastOrNull() ?: path
            }
            else -> path
        }
}
