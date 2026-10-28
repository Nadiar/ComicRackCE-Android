package com.comicrack.wifi.model

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Represents an active WiFi sync session with a desktop server.
 *
 * A sync session is established after successful discovery and pairing.
 * The session tracks the connected server and overall sync state.
 *
 * @property server The discovered server this session is connected to
 * @property startTime Unix timestamp when the session started
 * @property state Current state of the sync session
 *
 * @see com.comicrack.wifi.server.SyncServer
 */
data class SyncSession(
    val server: DiscoveredServer,
    val startTime: Long = System.currentTimeMillis(),
    val state: SyncSessionState = SyncSessionState.Connecting,
)

/**
 * Possible states of a sync session.
 *
 * Maps to the protocol session flow:
 * Discovery â†’ Pairing â†’ Syncing â†’ Complete/Error
 */
sealed interface SyncSessionState {
    /** Initial connection being established */
    data object Connecting : SyncSessionState

    /** Handshake/pairing in progress (Session 2) */
    data object Pairing : SyncSessionState

    /** Active sync operations in progress */
    data class Syncing(
        val progress: Int = 0,
    ) : SyncSessionState

    /** Sync completed successfully */
    data object Complete : SyncSessionState

    /** Session terminated with error */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : SyncSessionState

    /** Session was cancelled by user */
    data object Cancelled : SyncSessionState
}
