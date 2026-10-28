package com.comicrack.core.metadata

import android.content.Context
import android.net.Uri
import timber.log.Timber

/**
 * Dispatches metadata extraction to the appropriate extractor based on file format.
 */
class MetadataDispatcher(
    private val context: Context,
    private val archiveStreamFactory: ArchiveStreamFactory
) {
    private val comicInfoExtractor = ComicInfoExtractor(archiveStreamFactory)
    private val cbpMetadataExtractor = CbpMetadataExtractor(context)

    // Metadata is now handled by the ViewModel via loadComicMetadata
    // which is triggered in ComicViewerScreen

    /**
     * Extracts metadata for a comic based on its format and URI.
     *
     * @param uri The URI of the comic file.
     * @param format The file format (e.g., "cbz", "cbp", "cbr").
     * @return Pair of (extracted fields, source format) or null if extraction failed.
     */
    suspend fun extractMetadata(uri: Uri, format: String): Pair<Map<String, String?>, String>? {
        val extractor = when (format.lowercase()) {
            "cbp" -> cbpMetadataExtractor
            else -> comicInfoExtractor
        }

        val fields = extractor.extract(uri)
        return if (fields != null) {
            fields to extractor.getSourceFormat()
        } else {
            // Fallback: If CBP sidecar fails, try internal ComicInfo
            if (format.lowercase() == "cbp") {
                val fallbackFields = comicInfoExtractor.extract(uri)
                if (fallbackFields != null) {
                    return fallbackFields to comicInfoExtractor.getSourceFormat()
                }
            }
            null
        }
    }
}
