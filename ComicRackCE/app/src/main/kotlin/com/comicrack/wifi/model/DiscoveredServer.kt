package com.comicrack.wifi.model

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import java.net.InetAddress

/**
 * Represents a discovered ComicRack CE desktop server on the local network.
 *
 * Created when the [BroadcastManager][com.comicrack.wifi.broadcast.BroadcastManager]
 * receives a ServerAvailable UDP multicast packet from the desktop application.
 *
 * @property address IP address of the discovered server
 * @property port TCP port for sync connections (typically 7614)
 * @property timestamp Unix timestamp (ms) when server was last seen
 *
 * @see com.comicrack.wifi.broadcast.BroadcastManager
 * @see com.comicrack.wifi.protocol.Command.ServerAvailable
 */
data class DiscoveredServer(
    val address: InetAddress,
    val port: Int,
    val timestamp: Long,
) {
    /**
     * Check if this server discovery has expired.
     *
     * Desktop servers broadcast ServerAvailable every 10 seconds.
     * A server is considered expired if not seen within the timeout period.
     *
     * @param timeoutMillis Maximum age before considered expired (default: 30000ms)
     * @return true if the discovery is older than the timeout
     */
    fun isExpired(timeoutMillis: Long = DEFAULT_EXPIRY_MS): Boolean =
        System.currentTimeMillis() - timestamp > timeoutMillis

    /**
     * Get the server endpoint as "address:port" string.
     *
     * Useful for logging and display purposes.
     */
    val endpoint: String
        get() = "${address.hostAddress}:$port"

    companion object {
        /** Default expiry time for server discovery (30 seconds) */
        const val DEFAULT_EXPIRY_MS = 30_000L
    }
}
