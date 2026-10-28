package com.comicrack.core.metadata

import android.net.Uri

/**
 * Interface for metadata extraction from various sources (archives, sidecar files, etc.).
 */
interface MetadataExtractor {
    /**
     * Extracts metadata from the given URI.
     *
     * @param uri The URI of the comic file or sidecar file.
     * @return A map of extracted fields, or null if extraction failed.
     */
    suspend fun extract(uri: Uri): Map<String, String?>?
    
    /**
     * Returns the name of the source format (e.g., "ComicInfo", "CBP").
     */
    fun getSourceFormat(): String
}
