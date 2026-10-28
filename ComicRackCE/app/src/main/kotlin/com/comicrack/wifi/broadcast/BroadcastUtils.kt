package com.comicrack.wifi.broadcast

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import com.comicrack.wifi.protocol.Command
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility functions for broadcast packet handling.
 *
 * These are pure functions that can be unit tested without
 * Android framework dependencies.
 */
object BroadcastUtils {
    /**
     * Check if an IP address string is a valid multicast address.
     *
     * Multicast addresses are in the range 224.0.0.0 to 239.255.255.255.
     *
     * @param address IP address string
     * @return true if the address is a valid multicast address
     */
    fun isValidMulticastAddress(address: String): Boolean {
        if (address.isEmpty()) return false
        return try {
            val inetAddress = InetAddress.getByName(address)
            inetAddress.isMulticastAddress
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parse a ServerAvailable packet to extract the sync port.
     *
     * Packet format:
     * - Byte 0: Opcode (0x0D = 13 for ServerAvailable)
     * - Bytes 1-4: Port number (big-endian int)
     *
     * @param packet Raw packet bytes
     * @return The sync port, or null if packet is invalid
     */
    fun parseServerAvailable(packet: ByteArray): Int? {
        if (packet.size < 5) return null
        if (packet[0].toInt() != Command.OPCODE_SERVER_AVAILABLE) return null

        return ByteBuffer
            .wrap(packet, 1, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .getInt()
    }

    /**
     * Create a presence beacon to announce device to desktop.
     *
     * Format: "ComicRackAndroid:<DEVICE_KEY>" (UTF-8 bytes)
     * This is a passive announcement - Desktop will respond with ClientPong if paired.
     * Key must be lowercase to match the Id field in comicrack.ini.
     *
     * @param deviceKey Unique device key (SHA1 of model+manufacturer+serial)
     */
    fun createPresenceBeacon(deviceKey: String): ByteArray {
        val payload = "ComicRackAndroid:${deviceKey.lowercase()}"
        return payload.toByteArray(Charsets.UTF_8)
    }

    /**
     * Create a sync request packet to trigger immediate sync.
     *
     * Format: "ComicRackAndroid:<DEVICE_KEY>:Sync" (UTF-8 bytes)
     * This actively requests Desktop to initiate a sync session.
     * Key must be lowercase to match the Id field in comicrack.ini.
     *
     * @param deviceKey Unique device key (SHA1 of model+manufacturer+serial)
     */
    fun createSyncRequestPacket(deviceKey: String): ByteArray {
        val payload = "ComicRackAndroid:${deviceKey.lowercase()}:Sync"
        return payload.toByteArray(Charsets.UTF_8)
    }

    /**
     * Create a ClientPong packet (Opcode 12).
     */
    fun createClientPongPacket(): ByteArray = byteArrayOf(Command.OPCODE_CLIENT_PONG.toByte())
}
