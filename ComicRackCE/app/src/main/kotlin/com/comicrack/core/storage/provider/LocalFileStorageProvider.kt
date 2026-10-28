package com.comicrack.core.storage.provider

import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * [StorageProvider] implementation for local file system paths (java.io.File).
 *
 * Used for app-specific directories (cache, internal data) where SAF is not required.
 * All operations are synchronous but wrapped in suspend for interface uniformity.
 */
class LocalFileStorageProvider : StorageProvider {

    override val type: StorageType = StorageType.LOCAL

    override suspend fun listFiles(directoryIdentifier: String): Result<List<StorageFileEntry>> =
        runCatching {
            val dir = File(directoryIdentifier)
            if (!dir.exists() || !dir.isDirectory) {
                return Result.failure(FileNotFoundException("Directory not found: $directoryIdentifier"))
            }

            val children = dir.listFiles() ?: return Result.success(emptyList())
            children.map { file ->
                StorageFileEntry(
                    identifier = file.absolutePath,
                    displayName = file.name,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                    lastModified = file.lastModified(),
                    parentIdentifier = directoryIdentifier,
                )
            }
        }.onFailure { e ->
            Timber.e(e, "LocalFileStorageProvider.listFiles failed for: %s", directoryIdentifier)
        }

    override suspend fun getMetadata(fileIdentifier: String): Result<StorageFileEntry> =
        runCatching {
            val file = File(fileIdentifier)
            if (!file.exists()) {
                return Result.failure(FileNotFoundException("File not found: $fileIdentifier"))
            }

            StorageFileEntry(
                identifier = file.absolutePath,
                displayName = file.name,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
            )
        }

    override suspend fun openInputStream(fileIdentifier: String): Result<InputStream> =
        runCatching {
            val file = File(fileIdentifier)
            if (!file.exists()) {
                throw FileNotFoundException("File not found: $fileIdentifier")
            }
            FileInputStream(file) as InputStream
        }

    override suspend fun isAccessible(): Boolean = true

    override suspend fun resolveDisplayPath(identifier: String): String =
        try {
            File(identifier).absolutePath
        } catch (e: Exception) {
            identifier
        }
}
