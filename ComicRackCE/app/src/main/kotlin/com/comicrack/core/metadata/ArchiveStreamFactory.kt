package com.comicrack.core.metadata

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Factory for creating InputStreams for files within comic archives.
 * Supports zero-copy streaming where possible.
 */
class ArchiveStreamFactory(private val context: Context) {

    /**
     * Opens an InputStream for a specific file within an archive.
     * 
     * @param archiveUri The URI of the archive file.
     * @param fileName The name of the file to find within the archive.
     * @param format The archive format (cbz, cbr, etc.).
     * @return InputStream for the file, or null if not found or unsupported.
     */
    fun openFileStream(archiveUri: Uri, fileName: String, format: String): InputStream? {
        return try {
            val archiveStream = context.contentResolver.openInputStream(archiveUri) ?: return null
            
            when (format.lowercase()) {
                "cbz", "zip" -> findInZip(archiveStream, fileName)
                else -> {
                    Timber.tag("ArchiveStreamFactory").w("Streaming metadata not yet implemented for format: $format")
                    archiveStream.close()
                    null
                }
            }
        } catch (e: Exception) {
            Timber.tag("ArchiveStreamFactory").e(e, "Error opening file stream in archive: $archiveUri")
            null
        }
    }

    private fun findInZip(inputStream: InputStream, targetFileName: String): InputStream? {
        val zipInput = ZipInputStream(inputStream)
        var entry = zipInput.nextEntry
        while (entry != null) {
            if (entry.name.equals(targetFileName, ignoreCase = true)) {
                return zipInput // Note: The caller must NOT close this stream until done, 
                               // and closing it will close the underlying zipInput.
            }
            entry = zipInput.nextEntry
        }
        zipInput.close()
        return null
    }
}
