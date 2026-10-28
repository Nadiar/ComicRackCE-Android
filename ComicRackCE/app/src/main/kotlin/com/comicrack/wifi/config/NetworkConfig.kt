package com.comicrack.wifi.config

/**
 * Centralized network configuration for WiFi sync operations.
 *
 * All network constants are defined here to provide a single source of truth
 * for network configuration across the WiFi sync module.
 */
object NetworkConfig {
    // =====================================================================
    // Multicast Configuration
    // =====================================================================
    /** Multicast group IP address for server discovery */
    const val MULTICAST_GROUP = "224.34.123.90"

    /** Multicast port for server discovery broadcasts */
    const val MULTICAST_PORT = 7615

    /** Time-to-live for multicast packets */
    const val MULTICAST_TTL = 4

    /** Receive buffer size for multicast datagrams (bytes) */
    const val RECEIVE_BUFFER_SIZE = 64

    // =====================================================================
    // Sync Server Configuration
    // =====================================================================
    /** TCP port for sync server listening */
    const val SYNC_TCP_PORT = 7614

    /** UDP port for sync beacons and discovery */
    const val SYNC_UDP_PORT = 7615

    // =====================================================================
    // Socket Configuration
    // =====================================================================
    /** Socket timeout for network operations (milliseconds) */
    const val SOCKET_TIMEOUT_MS = 5000

    /** Timeout for discovery operations (milliseconds) */
    const val DISCOVERY_TIMEOUT_MS = 3000

    /** Interval for beacon pong responses (milliseconds) */
    const val PONG_INTERVAL_MS = 1000

    // =====================================================================
    // Beacon Configuration
    // =====================================================================
    /** Connect timeout for beacon transmission (milliseconds) */
    const val BEACON_CONNECT_TIMEOUT_MS = 1000

    /** Socket timeout for beacon operations (milliseconds) */
    const val BEACON_SOCKET_TIMEOUT_MS = 500
}
