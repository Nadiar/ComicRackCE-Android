package com.comicrack.wifi.broadcast

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Configuration constants for WiFi sync broadcast discovery.
 *
 * These values must match the legacy ComicRack CE desktop exactly.
 */
object BroadcastConfig {
    /**
     * Multicast group address for server discovery.
     * Desktop sends ServerAvailable packets to this address.
     */
    const val MULTICAST_GROUP = "224.34.123.90"

    /**
     * UDP port for multicast discovery.
     */
    const val MULTICAST_PORT = 7615

    /**
     * Default TCP port for sync connections.
     * The actual port is sent in ServerAvailable packets.
     */
    const val DEFAULT_SYNC_PORT = 7614

    /**
     * Timeout for server discovery (in milliseconds).
     * If no ServerAvailable packet received within this time, consider server offline.
     */
    const val DISCOVERY_TIMEOUT_MS = 30_000L

    /**
     * Interval between sending ClientPong responses (in milliseconds).
     */
    const val PONG_INTERVAL_MS = 5_000L
}
