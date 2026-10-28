package com.comicrack.core.storage.provider

import android.content.Context
import android.net.Uri
import com.comicrack.core.storage.saf.SAFFileSystem
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.InputStream

/**
 * [StorageProvider] implementation backed by Android's Storage Access Framework (SAF).
 *
 * Wraps [SAFFileSystem] (Okio) to conform to the [StorageProvider] interface,
 * allowing the scanner pipeline to handle SAF URIs through the same abstraction
 * used for local files and remote providers.
 *
 * @param context Android context for ContentResolver access
 * @param treeUri The SAF tree URI from ACTION_OPEN_DOCUMENT_TREE
 */
class SAFStorageProvider(
    private val context: Context,
    private val treeUri: Uri,
) : StorageProvider {

    override val type: StorageType = StorageType.SAF

    private val fileSystem: SAFFileSystem by lazy { SAFFileSystem(context, treeUri) }

    override suspend fun listFiles(directoryIdentifier: String): Result<List<StorageFileEntry>> =
        runCatching {
            val dirPath = directoryIdentifier.toPath()
            val paths = fileSystem.listOrNull(dirPath) ?: return Result.failure(
                java.io.FileNotFoundException("Directory not found: $directoryIdentifier"),
            )

            paths.mapNotNull { path ->
                val metadata = fileSystem.metadataOrNull(path) ?: return@mapNotNull null
                StorageFileEntry(
                    identifier = path.toString(),
                    displayName = path.name,
                    isDirectory = metadata.isDirectory,
                    size = metadata.size ?: 0L,
                    lastModified = metadata.lastModifiedAtMillis ?: 0L,
                    parentIdentifier = directoryIdentifier,
                )
            }
        }.onFailure { e ->
            Timber.e(e, "SAFStorageProvider.listFiles failed for: %s", directoryIdentifier)
        }

    override suspend fun getMetadata(fileIdentifier: String): Result<StorageFileEntry> =
        runCatching {
            val path = fileIdentifier.toPath()
            val metadata = fileSystem.metadataOrNull(path)
                ?: return Result.failure(
                    java.io.FileNotFoundException("File not found: $fileIdentifier"),
                )

            StorageFileEntry(
                identifier = fileIdentifier,
                displayName = path.name,
                isDirectory = metadata.isDirectory,
                size = metadata.size ?: 0L,
                lastModified = metadata.lastModifiedAtMillis ?: 0L,
            )
        }

    override suspend fun openInputStream(fileIdentifier: String): Result<InputStream> =
        runCatching {
            val path = fileIdentifier.toPath()
            val documentUri = fileSystem.getDocumentUri(path)
            context.contentResolver.openInputStream(documentUri)
                ?: throw java.io.IOException("ContentResolver returned null for: $documentUri")
        }

    override suspend fun isAccessible(): Boolean =
        try {
            // Check that we still hold the persistable permission
            val persistedUris = context.contentResolver.persistedUriPermissions
            persistedUris.any { it.uri == treeUri && it.isReadPermission }
        } catch (e: Exception) {
            Timber.w(e, "SAFStorageProvider.isAccessible check failed")
            false
        }

    override suspend fun resolveDisplayPath(identifier: String): String =
        try {
            val docUri = if (identifier.isEmpty()) {
                treeUri
            } else {
                fileSystem.getDocumentUri(identifier.toPath())
            }
            // Extract a friendly name from the URI
            docUri.lastPathSegment?.replace("primary:", "Internal Storage/")
                ?: treeUri.toString()
        } catch (e: Exception) {
            treeUri.toString()
        }
}
