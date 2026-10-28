package com.comicrack.wifi.broadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * Tests for BroadcastManager - verifies UDP multicast discovery logic.
 *
 * Note: Actual multicast socket operations require Android instrumentation tests.
 * These unit tests focus on packet parsing, address validation, and state management.
 */
class BroadcastManagerTest {
    // ==========================================================================
    // Multicast address validation
    // ==========================================================================

    @Test
    fun `multicast group address is correct`() {
        assertEquals("224.34.123.90", BroadcastConfig.MULTICAST_GROUP)
    }

    @Test
    fun `multicast port is correct`() {
        assertEquals(7615, BroadcastConfig.MULTICAST_PORT)
    }

    @Test
    fun `default sync port is correct`() {
        assertEquals(7614, BroadcastConfig.DEFAULT_SYNC_PORT)
    }

    @Test
    fun `isValidMulticastAddress returns true for multicast address`() {
        assertTrue(BroadcastUtils.isValidMulticastAddress("224.34.123.90"))
    }

    @Test
    fun `isValidMulticastAddress returns false for unicast address`() {
        assertFalse(BroadcastUtils.isValidMulticastAddress("192.168.1.1"))
    }

    @Test
    fun `isValidMulticastAddress returns false for invalid address`() {
        assertFalse(BroadcastUtils.isValidMulticastAddress("invalid"))
    }

    @Test
    fun `isValidMulticastAddress returns false for empty string`() {
        assertFalse(BroadcastUtils.isValidMulticastAddress(""))
    }

    // ==========================================================================
    // Broadcast packet parsing
    // ==========================================================================

    @Test
    fun `parseServerAvailable extracts port from valid packet`() {
        // Packet format: opcode (1 byte) + port (4 bytes big-endian)
        val packet =
            byteArrayOf(
                0x0D, // OPCODE_SERVER_AVAILABLE = 13
                0x00,
                0x00,
                0x1D.toByte(),
                0xBE.toByte(), // 7614 in big-endian
            )
        val result = BroadcastUtils.parseServerAvailable(packet)
        assertNotNull(result)
        assertEquals(7614, result)
    }

    @Test
    fun `parseServerAvailable returns null for wrong opcode`() {
        val packet =
            byteArrayOf(
                0x00, // Wrong opcode (ListFiles)
                0x00,
                0x00,
                0x1D.toByte(),
                0xBE.toByte(),
            )
        val result = BroadcastUtils.parseServerAvailable(packet)
        assertEquals(null, result)
    }

    @Test
    fun `parseServerAvailable returns null for too short packet`() {
        val packet = byteArrayOf(0x0D, 0x00, 0x00) // Only 3 bytes, need 5
        val result = BroadcastUtils.parseServerAvailable(packet)
        assertEquals(null, result)
    }

    @Test
    fun `parseServerAvailable returns null for empty packet`() {
        val packet = byteArrayOf()
        val result = BroadcastUtils.parseServerAvailable(packet)
        assertEquals(null, result)
    }

    // ==========================================================================
    // Presence beacon creation
    // ==========================================================================

    @Test
    fun `createPresenceBeacon formats beacon with ComicRackAndroid prefix`() {
        val deviceKey = "8430f142f16749e626eed46674c67e402a6c2fe"
        val packet = BroadcastUtils.createPresenceBeacon(deviceKey)
        val expected = "ComicRackAndroid:$deviceKey"
        assertEquals(expected, String(packet, Charsets.UTF_8))
    }

    @Test
    fun `createPresenceBeacon encodes as UTF-8`() {
        val deviceKey = "test-key-123"
        val packet = BroadcastUtils.createPresenceBeacon(deviceKey)
        // Verify it's valid UTF-8 bytes that decode back
        val decoded = String(packet, Charsets.UTF_8)
        assertEquals("ComicRackAndroid:test-key-123", decoded)
    }

    // ==========================================================================
    // Sync request packet creation
    // ==========================================================================

    @Test
    fun `createSyncRequestPacket includes Sync suffix`() {
        val deviceKey = "test-key-123"
        val packet = BroadcastUtils.createSyncRequestPacket(deviceKey)
        val decoded = String(packet, Charsets.UTF_8)
        assertEquals("ComicRackAndroid:test-key-123:Sync", decoded)
    }

    // ==========================================================================
    // Client ping packet creation
    // ==========================================================================

    @Test
    fun `createClientPongPacket creates correct packet`() {
        val packet = BroadcastUtils.createClientPongPacket()
        assertEquals(1, packet.size)
        assertEquals(0x0C.toByte(), packet[0]) // OPCODE_CLIENT_PONG = 12
    }

    // ==========================================================================
    // Server discovery state
    // ==========================================================================

    @Test
    fun `DiscoveredServer has correct properties`() {
        val address = InetAddress.getByName("192.168.1.100")
        val server =
            DiscoveredServer(
                address = address,
                port = 7614,
                timestamp = System.currentTimeMillis(),
            )
        assertEquals(address, server.address)
        assertEquals(7614, server.port)
    }

    @Test
    fun `DiscoveredServer isExpired returns false for recent discovery`() {
        val server =
            DiscoveredServer(
                address = InetAddress.getByName("192.168.1.100"),
                port = 7614,
                timestamp = System.currentTimeMillis(),
            )
        assertFalse(server.isExpired(timeoutMillis = 30000))
    }

    @Test
    fun `DiscoveredServer isExpired returns true for old discovery`() {
        val server =
            DiscoveredServer(
                address = InetAddress.getByName("192.168.1.100"),
                port = 7614,
                timestamp = System.currentTimeMillis() - 60000, // 60 seconds ago
            )
        assertTrue(server.isExpired(timeoutMillis = 30000)) // 30 second timeout
    }

    @Test
    fun `DiscoveredServer endpoint returns address and port`() {
        val server =
            DiscoveredServer(
                address = InetAddress.getByName("192.168.1.100"),
                port = 7614,
                timestamp = System.currentTimeMillis(),
            )
        assertEquals("192.168.1.100:7614", server.endpoint)
    }
}
