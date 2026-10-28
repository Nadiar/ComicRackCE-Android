package com.comicrack.core.metadata

import android.content.Context
import android.net.Uri
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.ComicMetadata
import com.comicrack.data.database.repository.ComicRackMetadataRepository
import timber.log.Timber
import java.io.File

/**
 * Service for loading and caching comic metadata using modular extractors.
 */
class ComicMetadataService(
    private val metadataRepository: ComicRackMetadataRepository,
    private val metadataDispatcher: MetadataDispatcher,
    private val metadataMapper: MetadataMapper,
) {
    companion object {
        private const val TAG = "ComicMetadataService"
    }

    /**
     * Load metadata for a single comic from its archive.
     *
     * @param comicBook The comic to load metadata for
     * @param force Forced refresh (ignore cache)
     * @return Loaded/cached ComicMetadata, or null if parsing failed
     */
    suspend fun loadMetadataForComic(comicBook: ComicBook, force: Boolean = false): ComicMetadata? {
        if (comicBook.id <= 0) {
            Timber.tag(TAG).w("Cannot load metadata for comic without ID: ${comicBook.title}")
            return null
        }

        // Check if metadata already cached and up-to-date
        val cached = metadataRepository.getMetadata(comicBook.id)
        if (!force && cached != null) {
            // Check if file has changed since last parse
            // We use the comicBook's dateModified/fileSize as a heuristic
            // (Note: ComicBook.dateModified/fileSize are updated during Refresh)
            if (cached.parsedAt > comicBook.dateModified) {
                Timber.tag(TAG)
                    .d("Using up-to-date cached metadata for: ${comicBook.title}")
                return cached
            } else {
                Timber.tag(TAG).d(
                    "Cached metadata stale for: ${comicBook.title} " +
                        "(Parsed: ${cached.parsedAt}, File: ${comicBook.dateModified})"
                )
            }
        }

        // Extract metadata using dispatcher
        return try {
            val fileUri = if (comicBook.filePath.startsWith("content://")) {
                Uri.parse(comicBook.filePath)
            } else {
                Uri.fromFile(File(comicBook.filePath))
            }

            val (parsedFields, sourceFormat) =
                metadataDispatcher.extractMetadata(fileUri, comicBook.format) ?: return null

            // Map and save
            val metadata = metadataMapper.mapToEntity(
                comicBookId = comicBook.id,
                defaultTitle = comicBook.title ?: "Unknown",
                fields = parsedFields,
                source = sourceFormat
            )
            
            // Cache result
            metadataRepository.upsertMetadata(metadata)
            Timber.tag(TAG).d("Loaded and cached $sourceFormat metadata for: ${comicBook.title}")
            metadata
        } catch (exception: Exception) {
            Timber.tag(TAG).w(exception, "Failed to parse metadata for: ${comicBook.filePath}")
            null
        }
    }

    /**
     * Batch load metadata for multiple comics.
     */
    suspend fun batchLoadMetadata(comicBooks: List<ComicBook>, batchSize: Int = 10): Int {
        var loaded = 0
        comicBooks.chunked(batchSize).forEach { batch ->
            batch.forEach { comic ->
                if (loadMetadataForComic(comic) != null) loaded++
            }
        }
        return loaded
    }
}
