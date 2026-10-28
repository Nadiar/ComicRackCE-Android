package com.comicrack.wifi.broadcast

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import com.comicrack.wifi.model.DiscoveredServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * Manages UDP multicast discovery of ComicRack CE desktop servers.
 *
 * Listens for ServerAvailable broadcasts on the multicast group (224.34.123.90:7615)
 * and emits [DiscoveredServer] instances when a desktop server is detected.
 *
 * @see DiscoveredServer
 * @see BroadcastConfig
 */
class BroadcastManager {
    companion object {
        private const val TAG = "BroadcastManager"
        private const val RECEIVE_BUFFER_SIZE = 64
    }

    /**
     * Flow of discovered servers.
     *
     * Joins the multicast group, listens for ServerAvailable packets,
     * and emits DiscoveredServer instances.
     *
     * The flow responds to each ServerAvailable with a ClientPong.
     */

    /**
     * Flow of discovered servers.
     *
     * Joins the multicast group, listens for ServerAvailable packets,
     * and emits DiscoveredServer instances.
     *
     * The flow responds to each ServerAvailable with a Discovery Packet (beacon).
     */
    fun discoverServers(deviceKey: String): Flow<DiscoveredServer> =
        callbackFlow {
            Timber.tag(TAG).d("discoverServers() called with key length: ${deviceKey.length}")

            // 1. Setup Sender (Ephemeral Port) - Critical for announcing presence
            val multicastGroup = InetAddress.getByName(BroadcastConfig.MULTICAST_GROUP)
            Timber.tag(TAG).d("Multicast group: ${BroadcastConfig.MULTICAST_GROUP}")

            val senderSocket =
                try {
                    MulticastSocket().apply {
                        timeToLive = 4
                        // Explicitly valid WiFi interface to ensure routing
                        val wifiInterface = findWiFiInterface()
                        if (wifiInterface != null) {
                            networkInterface = wifiInterface
                            Timber.tag(TAG).d("Sender socket bound to ${wifiInterface.name}")
                        } else {
                            Timber.tag(TAG).w("No WiFi interface found, using default")
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to create sender socket")
                    close() // Fatal
                    return@callbackFlow
                }

            // Launch Beacon Job
            Timber.tag(TAG).d("Starting beacon job...")
            val beaconJob =
                launch {
                    var beaconCount = 0
                    while (isActive) {
                        try {
                            sendDiscoveryBeacon(senderSocket, multicastGroup, deviceKey)
                            beaconCount++
                            if (beaconCount <= 5 || beaconCount % 10 == 0) {
                                Timber.tag(TAG).d("Beacon #$beaconCount sent")
                            }
                        } catch (e: Exception) {
                            if (e !is CancellationException) {
                                Timber.tag(TAG).w(e, "Failed to send beacon")
                            }
                        }
                        delay(BroadcastConfig.PONG_INTERVAL_MS)
                    }
                }

            // 2. Setup Receiver (Bound Port 7615) - Best effort
            var receiverSocket: MulticastSocket? = null
            try {
                receiverSocket =
                    MulticastSocket(BroadcastConfig.MULTICAST_PORT).apply {
                        reuseAddress = true
                        soTimeout = 5000
                    }

                val networkInterface = findWiFiInterface()
                if (networkInterface != null) {
                    receiverSocket.joinGroup(
                        java.net.InetSocketAddress(multicastGroup, BroadcastConfig.MULTICAST_PORT),
                        networkInterface,
                    )
                    Timber.tag(TAG).i("Joined multicast group on ${networkInterface.name}")
                } else {
                    @Suppress("DEPRECATION")
                    receiverSocket.joinGroup(multicastGroup)
                    Timber.tag(TAG).i("Joined multicast group (default interface)")
                }

                // Receive Loop
                val receiveBuffer = ByteArray(RECEIVE_BUFFER_SIZE)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        receiverSocket.receive(packet)

                        val data = packet.data.copyOf(packet.length)
                        val port = BroadcastUtils.parseServerAvailable(data)

                        if (port != null) {
                            val server =
                                DiscoveredServer(
                                    address = packet.address,
                                    port = port,
                                    timestamp = System.currentTimeMillis(),
                                )
                            Timber.tag(TAG).d("Discovered server: ${server.endpoint}")

                            // Send immediate beacon response (unicast to server)
                            sendDiscoveryBeaconTo(senderSocket, packet.address, port, deviceKey)

                            trySend(server)
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // Continue
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber
                        .tag(
                            TAG,
                        ).w(e, "UDP Receiver failed (binding conflict?). Discovery will continue via Beacon-only.")
                }
                // Do NOT throw. We want the beaconJob to keep running.
                // Just suspend until cancelled.
                if (isActive) {
                    kotlinx.coroutines.awaitCancellation()
                }
            } finally {
                try {
                    senderSocket.close()
                } catch (e: Exception) {
                    // ignore
                }

                receiverSocket?.let { s ->
                    try {
                        val networkInterface = findWiFiInterface()
                        if (networkInterface != null) {
                            s.leaveGroup(
                                java.net.InetSocketAddress(multicastGroup, BroadcastConfig.MULTICAST_PORT),
                                networkInterface,
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            s.leaveGroup(multicastGroup)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                    s.close()
                }
            }

            awaitClose {
                Timber.tag(TAG).d("Broadcast discovery flow closed")
                beaconJob.cancel()
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Send a presence beacon to the multicast group (passive announcement).
     * Desktop will add us to known devices and respond with ClientPong if paired.
     */
    private fun sendDiscoveryBeacon(
        socket: MulticastSocket,
        group: InetAddress,
        deviceKey: String,
    ) {
        val payload = BroadcastUtils.createPresenceBeacon(deviceKey)
        val packet =
            DatagramPacket(
                payload,
                payload.size,
                group,
                BroadcastConfig.MULTICAST_PORT,
            )
        socket.send(packet)
        Timber.tag(TAG).v("Sent presence beacon to multicast group")
    }

    /**
     * Send a presence beacon directly to a server (Unicast).
     * Used to respond to ServerAvailable.
     */
    private fun sendDiscoveryBeaconTo(
        socket: MulticastSocket,
        address: InetAddress,
        port: Int,
        deviceKey: String,
    ) {
        try {
            // Desktop expects "ComicRackAndroid:<Key>" format for presence.
            // Verified from legacy APK packet capture.
            val payload = BroadcastUtils.createPresenceBeacon(deviceKey)
            val packet = DatagramPacket(payload, payload.size, address, port)
            socket.send(packet)
            Timber.tag(TAG).v("Sent presence beacon to ${address.hostAddress}:$port")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to send beacon response to server")
        }
    }

    /**
     * Send a sync request to the multicast group.
     * This actively requests Desktop to initiate a sync session.
     * Call this when user taps "Sync Now" button.
     */
    fun sendSyncRequest(deviceKey: String) {
        try {
            val multicastGroup = InetAddress.getByName(BroadcastConfig.MULTICAST_GROUP)
            MulticastSocket().use { socket ->
                socket.timeToLive = 4
                val wifiInterface = findWiFiInterface()
                if (wifiInterface != null) {
                    socket.networkInterface = wifiInterface
                }

                val payload = BroadcastUtils.createSyncRequestPacket(deviceKey)
                val packet =
                    DatagramPacket(
                        payload,
                        payload.size,
                        multicastGroup,
                        BroadcastConfig.MULTICAST_PORT,
                    )
                socket.send(packet)
                Timber.tag(TAG).i("Sent sync request to multicast group")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send sync request")
        }
    }

    /**
     * Find the WiFi network interface.
     *
     * Returns null if not found, allowing fallback to default interface.
     */
    private fun findWiFiInterface(): NetworkInterface? =
        try {
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .find { ni ->
                    ni.isUp &&
                        !ni.isLoopback &&
                        (ni.name.startsWith("wlan") || ni.name.startsWith("wifi"))
                }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error finding WiFi interface")
            null
        }
}
