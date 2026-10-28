package com.comicrack.wifi.server

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
// Server configuration constants (extracted from magic numbers)
private const val DEFAULT_SERVER_PORT = 7614
private const val DEFAULT_SOCKET_TIMEOUT_MS = 30_000
private const val DEFAULT_BUFFER_SIZE_BYTES = 64 * 1024
/**
 * Represents the current state of the sync server.
 *
 * State flow:
 * ```
 * Idle â†’ Broadcasting â†’ Listening â†’ [Syncing] â†’ Listening (or Idle on stop)
 *           â†“
 *       (discovery)
 * ```
 *
 * Note: Android is a PASSIVE server. Sync is initiated by the desktop.
 * The "Broadcasting" state means we're announcing our presence and waiting.
 */
sealed class SyncServerState {
    /**
     * Server is not running.
     */
    data object Idle : SyncServerState() {
        override val isRunning: Boolean = false
        override val hasActiveSession: Boolean = false
        override val statusText: String = "Stopped"
    }

    /**
     * Server is broadcasting presence and waiting for desktop to connect.
     * This is the pre-pairing discovery phase.
     *
     * @property port The TCP port the server is listening on
     * @property beaconCount Number of beacons sent (for UI feedback)
     */
    data class Broadcasting(
        val port: Int,
        val beaconCount: Int = 0,
    ) : SyncServerState() {
        override val isRunning: Boolean = true
        override val hasActiveSession: Boolean = false
        override val statusText: String = "Searching for desktop..."
    }

    /**
     * Server is listening and ready for connections.
     * This state is used when we're already paired and just waiting.
     *
     * @property port The TCP port the server is listening on
     */
    data class Listening(
        val port: Int,
    ) : SyncServerState() {
        override val isRunning: Boolean = true
        override val hasActiveSession: Boolean = false
        override val statusText: String = "Ready (port $port)"
    }

    /**
     * Server has an active sync session.
     *
     * @property clientAddress The IP address of the connected client
     * @property progress Current sync progress (0-100)
     * @property currentFile Name of file currently being transferred
     */
    data class Syncing(
        val clientAddress: String,
        val progress: Int = 0,
        val currentFile: String = "",
    ) : SyncServerState() {
        override val isRunning: Boolean = true
        override val hasActiveSession: Boolean = true
        override val statusText: String =
            when {
                currentFile.isNotEmpty() -> "Syncing: $currentFile ($progress%)"
                progress > 0 -> "Syncing: $progress%"
                else -> "Syncing with $clientAddress..."
            }
    }

    /**
     * Server encountered an error.
     *
     * @property message Error description
     * @property isRecoverable Whether the error can be recovered from
     */
    data class Error(
        val message: String,
        val isRecoverable: Boolean = true,
    ) : SyncServerState() {
        override val isRunning: Boolean = false
        override val hasActiveSession: Boolean = false
        override val statusText: String = "Error: $message"
    }

    abstract val isRunning: Boolean
    abstract val hasActiveSession: Boolean
    abstract val statusText: String
}

/**
 * Configuration for sync sessions.
 */
data class SyncSessionConfig(
    /**
     * TCP port to listen on.
     */
    val port: Int = DEFAULT_SERVER_PORT,
    /**
     * Socket read/write timeout in milliseconds.
     */
    val socketTimeout: Int = DEFAULT_SOCKET_TIMEOUT_MS,
    /**
     * Buffer size for data transfers.
     */
    val bufferSize: Int = DEFAULT_BUFFER_SIZE_BYTES,
)

/**
 * Result of a sync session.
 */
sealed class SyncResult {
    /**
     * Sync completed successfully.
     */
    data class Success(
        val filesReceived: Int,
        val bytesSynced: Long,
    ) : SyncResult() {
        override val isSuccess: Boolean = true
    }

    /**
     * Sync failed with an error.
     */
    data class Failure(
        val error: String,
    ) : SyncResult() {
        override val isSuccess: Boolean = false
    }

    /**
     * Sync was aborted by user.
     */
    data object Aborted : SyncResult() {
        override val isSuccess: Boolean = false
    }

    abstract val isSuccess: Boolean
}
