package com.comicrack.wifi.server

import com.comicrack.wifi.file.FileManager
import com.comicrack.wifi.progress.SyncProgressTracker
import com.comicrack.wifi.protocol.Command
import com.comicrack.wifi.protocol.CommandHandler
import com.comicrack.wifi.protocol.ProtocolCodec
import com.comicrack.wifi.protocol.ProtocolException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * TCP server for handling sync connections from ComicRack CE desktop.
 *
 * Accepts connections, processes protocol commands, and manages file operations.
 */
class SyncServer(
    private val fileManager: FileManager,
    private val progressTracker: SyncProgressTracker,
    private val repository: com.comicrack.data.preferences.AppPreferencesRepository,
    private val deviceKey: String,
    private val deviceName: String,
    private val config: SyncSessionConfig = SyncSessionConfig(),
) {
    companion object {
        private const val TAG = "SyncServer"
        
        // Beacon transmission configuration
        private const val BEACON_CONNECT_TIMEOUT_MS = 5000
        private const val BEACON_SOCKET_TIMEOUT_MS = 5000

        /**
         * Log protocol-level messages for debugging.
         */
        fun logProtocol(message: String) {
            Timber.tag(TAG).d("PROTOCOL: $message")
        }
    }

    private val _state = MutableStateFlow<SyncServerState>(SyncServerState.Idle)
    val state: StateFlow<SyncServerState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var serverJob: Job? = null

    /**
     * Start the sync server.
     *
     * Listens for connections and handles sync sessions.
     */
    suspend fun start() =
        coroutineScope {
            if (_state.value.isRunning) {
                Timber.tag(TAG).w("Server already running")
                return@coroutineScope
            }

            serverJob =
                launch(Dispatchers.IO) {
                    try {
                        startServer()
                    } catch (e: CancellationException) {
                        Timber.tag(TAG).d("Server cancelled")
                        throw e
                    } catch (e: SocketException) {
                        Timber.tag(TAG).e(e, "Socket error during server startup")
                        _state.value = SyncServerState.Error("Socket error: ${e.message}")
                    } catch (e: IOException) {
                        Timber.tag(TAG).e(e, "I/O error during server startup")
                        _state.value = SyncServerState.Error("I/O error: ${e.message}")
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Server error")
                        _state.value = SyncServerState.Error(e.message ?: "Unknown error")
                    } finally {
                        cleanup()
                    }
                }
        }

    /**
     * Stop the sync server.
     */
    fun stop() {
        Timber.tag(TAG).i("Stopping server")
        serverJob?.cancel()
        cleanup()
    }

    /**
     * Request to abort the current sync session.
     */
    fun requestAbort() {
        Timber.tag(TAG).i("Abort requested")
        // The CommandHandler will check abort status on next CheckAbort command
    }

    private suspend fun startServer() =
        withContext(Dispatchers.IO) {
            Timber.tag("WiFiSync").i("▶▶▶ SERVER STARTUP: Creating ServerSocket on port ${config.port}")

            serverSocket =
                ServerSocket(config.port).apply {
                    reuseAddress = true
                    soTimeout = 0 // Block indefinitely on accept
                }

            // Log bound address details for diagnostics
            val ss = serverSocket ?: return@withContext
            Timber
                .tag(
                    "WiFiSync",
                ).i("▶▶▶ SERVER BOUND: localPort=${ss.localPort}, isBound=${ss.isBound}, isClosed=${ss.isClosed}")
            Timber.tag("WiFiSync").i("▶▶▶ SERVER ADDR: ${ss.localSocketAddress}")

            _state.value = SyncServerState.Listening(config.port)
            Timber.tag(TAG).i("====================================================")
            Timber.tag(TAG).i("SERVER ALERT: Sync Server listening on port ${config.port}")
            Timber.tag(TAG).i("====================================================")

            Timber.tag("WiFiSync").i("▶▶▶ SERVER READY: Entering accept() loop...")

            while (isActive) {
                try {
                    Timber.tag("WiFiSync").d("▶▶▶ SERVER ACCEPT: Waiting for connection...")
                    val socket = serverSocket?.accept() ?: break

                    val clientAddr = socket.inetAddress?.hostAddress ?: "unknown"
                    Timber.tag("WiFiSync").i("▶▶▶ SERVER CONNECT: Accepted connection from $clientAddr")

                    // Launch each connection in its own coroutine so the accept loop stays responsive
                    launch(Dispatchers.IO) {
                        try {
                            handleConnection(socket)
                        } catch (e: SocketException) {
                            Timber.tag(TAG).w(e, "Socket error handling connection from ${socket.inetAddress}")
                        } catch (e: IOException) {
                            Timber.tag(TAG).e(e, "I/O error handling connection from ${socket.inetAddress}")
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Error handling connection from ${socket.inetAddress}")
                        }
                    }
                } catch (e: SocketException) {
                    if (isActive) {
                        Timber.tag("WiFiSync").w(e, "▶▶▶ SERVER ERROR: Socket exception while accepting")
                        Timber.tag(TAG).w(e, "Socket error while accepting")
                    }
                    break
                }
            }

            Timber.tag("WiFiSync").i("▶▶▶ SERVER EXIT: Accept loop terminated")
        }

    private suspend fun handleConnection(socket: Socket) =
        withContext(Dispatchers.IO) {
            clientSocket = socket
            val clientAddress = socket.inetAddress.hostAddress ?: "unknown"

            Timber.tag(TAG).i("══════════════════════════════════════════════════════════════")
            Timber.tag(TAG).i("Connection from $clientAddress")
            _state.value = SyncServerState.Syncing(clientAddress, progress = 0)

            socket.soTimeout = config.socketTimeout

            var filesReceived = 0
            var bytesSynced = 0L
            var syncCompleted = false
            var commandCount = 0

            try {
                val codec =
                    ProtocolCodec(
                        socket.getInputStream().buffered(config.bufferSize),
                        socket.getOutputStream().buffered(config.bufferSize),
                    )

                val handler =
                    CommandHandler(
                        fileManager = fileManager,
                        progressTracker = progressTracker,
                        repository = repository,
                        deviceKey = deviceKey,
                        deviceName = deviceName,
                        onCompleted = { syncCompleted = true },
                    )

                // Command loop
                while (isActive && !syncCompleted) {
                    try {
                        val command = Command.parseCommand(codec)
                        commandCount++
                        Timber.tag(TAG).d("[$commandCount] ← ${command::class.simpleName}")

                        val result = handler.handle(command, codec)

                        // Handle post-processing actions
                        when (result) {
                            is CommandHandler.HandleResult.SendBeaconTo -> {
                                // Send beacon to desktop's control port on a new connection
                                sendBeaconToDesktop(socket.inetAddress, result.port)
                            }
                            CommandHandler.HandleResult.None -> { /* Nothing to do */ }
                        }

                        // Update state with progress
                        val progress = progressTracker.progress.value
                        _state.value = SyncServerState.Syncing(clientAddress, progress.percent)

                        // Track stats for WriteFile
                        if (command is Command.WriteFile) {
                            filesReceived++
                            bytesSynced += command.size
                        }
                    } catch (e: SocketTimeoutException) {
                        Timber.tag(TAG).w("Socket timeout, continuing")
                        continue
                    } catch (e: EOFException) {
                        Timber.tag(TAG).d("Client disconnected (EOF)")
                        break
                    } catch (e: ProtocolException) {
                        Timber.tag(TAG).e(e, "Protocol error")
                        break
                    } catch (e: IOException) {
                        Timber.tag(TAG).e(e, "I/O error during protocol parsing")
                        break
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Unexpected error during command processing")
                        break
                    }
                }

                // Log session summary
                Timber.tag(TAG).i("──────────────────────────────────────────────────────────────")
                Timber.tag(TAG).i("Session summary: $commandCount commands, $filesReceived files")
                Timber.tag(TAG).i("Protocol I/O: ${codec.getStats()}")
                if (syncCompleted) {
                    Timber.tag(TAG).i("✓ Sync completed successfully")
                } else {
                    Timber.tag(TAG).w("✗ Sync ended without Completed command")
                }
                Timber.tag(TAG).i("══════════════════════════════════════════════════════════════")
            } catch (e: SocketException) {
                Timber.tag(TAG).w(e, "Socket error during sync")
            } catch (e: EOFException) {
                Timber.tag(TAG).d("Connection terminated by client")
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "I/O error during sync")
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber.tag(TAG).e(e, "Error during sync")
                }
                throw e
            } finally {
                try {
                    socket.close()
                } catch (e: IOException) {
                    Timber.tag(TAG).w(e, "I/O error closing socket")
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error closing socket")
                }
                clientSocket = null
                _state.value = SyncServerState.Listening(config.port)
                Timber.tag(TAG).d("Connection closed")
            }
        }

    /**
     * Send beacon to desktop's control port to trigger sync initiation.
     *
     * The desktop's OnAcceptControl handler expects to receive the beacon
     * as raw UTF-8 bytes: "ComicRackAndroid:<KEY>:Sync"
     * Key must be lowercase to match the Id in comicrack.ini.
     */
    private fun sendBeaconToDesktop(
        address: java.net.InetAddress,
        port: Int,
    ) {
        try {
            val beacon = "ComicRackAndroid:${deviceKey.lowercase()}:Sync"
            val bytes = beacon.toByteArray(Charsets.UTF_8)

            Timber.tag(TAG).i("PROTOCOL [0D] → Sending beacon to ${address.hostAddress}:$port")

            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(address, port), BEACON_CONNECT_TIMEOUT_MS)
                socket.soTimeout = BEACON_SOCKET_TIMEOUT_MS
                socket.getOutputStream().write(bytes)
                socket.getOutputStream().flush()
            }

            Timber.tag(TAG).i("PROTOCOL [0D] ✓ Beacon sent successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "PROTOCOL [0D] ✗ Failed to send beacon to ${address.hostAddress}:$port")
        }
    }

    private fun cleanup() {
        try {
            clientSocket?.close()
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "I/O error closing client socket")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error closing client socket")
        }
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "I/O error closing server socket")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error closing server socket")
        }
        clientSocket = null
        serverSocket = null
        _state.value = SyncServerState.Idle
        Timber.tag(TAG).d("Server cleanup complete")
    }
}
