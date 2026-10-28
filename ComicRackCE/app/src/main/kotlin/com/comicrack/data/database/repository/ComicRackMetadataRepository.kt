package com.comicrack.data.database.repository

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import com.comicrack.data.database.dao.ComicMetadataDao
import com.comicrack.data.database.entities.ComicMetadata
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for ComicMetadata persistence and caching.
 *
 * Provides a clean API for loading, storing, and updating comic metadata.
 * Wraps [ComicMetadataDao] with error handling and logging.
 *
 * Phase 3: Task 3.2 â€” Metadata Repository
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 */
@Singleton
class ComicRackMetadataRepository
    @Inject
    constructor(
        private val metadataDao: ComicMetadataDao,
    ) {
        companion object {
            private const val TAG = "ComicMetadataRepository"
        }

        /**
         * Get cached metadata for a comic.
         *
         * @param comicBookId Comic ID
         * @return The ComicMetadata, or null if not found
         */
        suspend fun getMetadata(comicBookId: Long): ComicMetadata? =
            try {
                metadataDao.getMetadataByComicId(comicBookId)
            } catch (exception: Exception) {
                Timber.tag(TAG).w(exception, "Failed to get metadata for comic $comicBookId")
                null
            }

        /**
         * Get metadata for multiple comics.
         *
         * @param comicBookIds List of comic IDs
         * @return Map of comic ID -> metadata (null if not found)
         */
        suspend fun getMetadataForComics(comicBookIds: List<Long>): Map<Long, ComicMetadata?> {
            val metadataMap = mutableMapOf<Long, ComicMetadata?>()

            for (id in comicBookIds) {
                metadataMap[id] = getMetadata(id)
            }

            return metadataMap
        }

        /**
         * Save metadata for a comic.
         *
         * @param metadata The ComicMetadata to save
         */
        suspend fun upsertMetadata(metadata: ComicMetadata) {
            try {
                metadataDao.upsertMetadata(metadata)
                Timber.tag(TAG).d("Saved metadata for comic ${metadata.comicBookId}")
            } catch (exception: Exception) {
                Timber.tag(TAG).e(exception, "Failed to save metadata for comic ${metadata.comicBookId}")
            }
        }

        /**
         * Save multiple metadata records.
         *
         * @param metadataList List of ComicMetadata to save
         */
        suspend fun upsertMetadataBatch(metadataList: List<ComicMetadata>) {
            try {
                if (metadataList.isEmpty()) return

                metadataDao.insertMetadataBatch(*metadataList.toTypedArray())
                Timber.tag(TAG).d("Saved ${metadataList.size} metadata records")
            } catch (exception: Exception) {
                Timber.tag(TAG).e(exception, "Failed to save metadata batch")
            }
        }

        /**
         * Delete metadata for a specific comic.
         *
         * @param comicBookId Comic ID
         */
        suspend fun deleteMetadata(comicBookId: Long) {
            try {
                metadataDao.deleteMetadataByComicId(comicBookId)
                Timber.tag(TAG).d("Deleted metadata for comic $comicBookId")
            } catch (deleteException: Exception) {
                Timber.tag(TAG).e(deleteException, "Failed to delete metadata for comic $comicBookId")
            }
        }

        /**
         * Clear all cached metadata.
         *
         * Used for testing or forced refresh.
         */
        suspend fun clearAllMetadata() {
            try {
                metadataDao.deleteAllMetadata()
                Timber.tag(TAG).i("Cleared all metadata")
            } catch (clearException: Exception) {
                Timber.tag(TAG).e(clearException, "Failed to clear all metadata")
            }
        }

        /**
         * Get count of cached metadata records.
         *
         * Useful for monitoring cache size.
         *
         * @return Number of metadata records in database
         */
        suspend fun getMetadataCount(): Int =
            try {
                metadataDao.getMetadataCount()
            } catch (exception: Exception) {
                Timber.tag(TAG).w(exception, "Failed to get metadata count")
                0
            }
    }
