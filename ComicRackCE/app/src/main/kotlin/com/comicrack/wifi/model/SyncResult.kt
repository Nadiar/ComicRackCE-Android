package com.comicrack.wifi.model

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Result of a completed WiFi sync operation.
 *
 * Contains statistics about what was synchronized between
 * the Android device and ComicRack CE desktop.
 *
 * @property filesReceived Number of files received from desktop
 * @property filesSent Number of files sent to desktop
 * @property filesDeleted Number of files deleted during sync
 * @property bytesTransferred Total bytes transferred in both directions
 * @property durationMs Time taken for the sync operation in milliseconds
 * @property errors List of non-fatal errors encountered during sync
 *
 * @see com.comicrack.wifi.protocol.Command.Completed
 */
data class SyncResult(
    val filesReceived: Int = 0,
    val filesSent: Int = 0,
    val filesDeleted: Int = 0,
    val bytesTransferred: Long = 0,
    val durationMs: Long = 0,
    val errors: List<SyncError> = emptyList(),
) {
    /** Total number of files processed */
    val totalFiles: Int get() = filesReceived + filesSent + filesDeleted

    /** Whether the sync completed without errors */
    val isSuccess: Boolean get() = errors.isEmpty()

    /** Human-readable summary of the sync result */
    val summary: String get() =
        buildString {
            append("Sync completed: ")
            append("$filesReceived received, ")
            append("$filesSent sent, ")
            append("$filesDeleted deleted")
            if (errors.isNotEmpty()) {
                append(" (${errors.size} errors)")
            }
        }
}

/**
 * Represents a non-fatal error that occurred during sync.
 *
 * These errors don't stop the sync but are reported for logging.
 *
 * @property filename The file that caused the error
 * @property operation The operation that failed
 * @property message Error description
 */
data class SyncError(
    val filename: String,
    val operation: SyncOperation,
    val message: String,
)

/**
 * Types of sync operations that can fail.
 */
enum class SyncOperation {
    READ,
    WRITE,
    DELETE,
    VALIDATE,
}
