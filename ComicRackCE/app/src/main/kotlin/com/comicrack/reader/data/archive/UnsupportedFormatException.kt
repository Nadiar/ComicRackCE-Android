package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Exception thrown when attempting to open a file format that is recognized
 * but not natively supported by the app.
 *
 * This allows the UI layer to offer alternative actions like opening in
 * an external application.
 *
 * @param extension The file extension that isn't supported
 * @param filePath The full path to the file
 * @param mimeType MIME type for opening with external apps
 * @param reason Human-readable reason why format isn't supported
 */
class UnsupportedFormatException(
    val extension: String,
    val filePath: String,
    val mimeType: String,
    val reason: String,
) : Exception("Format '$extension' is not supported: $reason") {
    companion object {
        /**
         * Known unsupported formats with their MIME types and reasons.
         */
        fun forDjvu(filePath: String) =
            UnsupportedFormatException(
                extension = "djvu",
                filePath = filePath,
                mimeType = "image/vnd.djvu",
                reason = "DJVU decoding requires external libraries with incompatible licenses",
            )
    }
}
